// AdMob server-side verification (SSV) callback.
// AdMob calls this URL after every completed rewarded ad; we verify Google's signature and
// only then credit MB. The app itself can never add MB, which is what keeps payouts honest.
// Deploy with: supabase functions deploy admob-ssv --no-verify-jwt
import { createClient } from "jsr:@supabase/supabase-js@2";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Optional: the numeric part of your rewarded ad unit id (after the "/"), to reject other units.
const EXPECTED_AD_UNIT = Deno.env.get("ADMOB_REWARDED_AD_UNIT") ?? "";

let keys = new Map<string, CryptoKey>();
let keysFetchedAt = 0;

async function verifierKey(keyId: string): Promise<CryptoKey | undefined> {
  if (!keys.has(keyId) || Date.now() - keysFetchedAt > 12 * 3600_000) {
    const res = await fetch(KEYS_URL);
    const body = (await res.json()) as { keys: { keyId: number; base64: string }[] };
    const fresh = new Map<string, CryptoKey>();
    for (const k of body.keys) {
      const spki = Uint8Array.from(atob(k.base64), (c) => c.charCodeAt(0));
      fresh.set(
        String(k.keyId),
        await crypto.subtle.importKey("spki", spki, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]),
      );
    }
    keys = fresh;
    keysFetchedAt = Date.now();
  }
  return keys.get(keyId);
}

function base64UrlToBytes(s: string): Uint8Array {
  let b64 = s.replace(/-/g, "+").replace(/_/g, "/");
  while (b64.length % 4) b64 += "=";
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

// AdMob signs with DER-encoded ECDSA; WebCrypto expects raw r||s (64 bytes for P-256).
function derToRaw(der: Uint8Array): Uint8Array {
  let i = der[1] & 0x80 ? 2 + (der[1] & 0x7f) : 2;
  const readInt = () => {
    if (der[i++] !== 0x02) throw new Error("bad signature");
    const len = der[i++];
    let v = der.slice(i, i + len);
    i += len;
    while (v.length > 32 && v[0] === 0) v = v.slice(1);
    const out = new Uint8Array(32);
    out.set(v, 32 - v.length);
    return out;
  };
  const raw = new Uint8Array(64);
  raw.set(readInt(), 0);
  raw.set(readInt(), 32);
  return raw;
}

Deno.serve(async (req) => {
  const url = new URL(req.url);
  const query = url.search.startsWith("?") ? url.search.slice(1) : url.search;
  const params = url.searchParams;
  const signature = params.get("signature");
  const keyId = params.get("key_id");
  const sigAt = query.indexOf("&signature=");
  if (!signature || !keyId || sigAt < 0) return new Response("missing signature", { status: 400 });

  const key = await verifierKey(keyId);
  if (!key) return new Response("unknown key", { status: 400 });

  // The signed message is every query parameter before "&signature=", exactly as sent.
  const valid = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    derToRaw(base64UrlToBytes(signature)),
    new TextEncoder().encode(query.slice(0, sigAt)),
  );
  if (!valid) return new Response("bad signature", { status: 403 });

  const userId = params.get("user_id") ?? "";
  const tx = params.get("transaction_id") ?? "";
  // AdMob's "Verify URL" button sends a signed request with no user: acknowledge it.
  if (!UUID_RE.test(userId) || !tx) return new Response("ok", { status: 200 });
  if (EXPECTED_AD_UNIT && params.get("ad_unit") !== EXPECTED_AD_UNIT) {
    return new Response("ignored ad unit", { status: 200 });
  }

  const { data, error } = await admin.rpc("credit_ad", { p_user: userId, p_tx: tx });
  if (error) return new Response(error.message, { status: 500 }); // non-200 makes AdMob retry
  return Response.json(data);
});
