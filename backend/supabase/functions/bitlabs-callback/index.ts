// BitLabs server-to-server callback: credits MB when a user completes a survey.
// BitLabs signs every callback: &hash= is the hex HMAC-SHA1 of the full URL (without &hash=),
// keyed with your App Secret. Unsigned or tampered calls are rejected.
//
// Secrets: BITLABS_APP_SECRET, BITLABS_CALLBACK_URL (the URL you typed into BitLabs, without "?...").
// In the BitLabs dashboard set the app currency to "MB" so [%VAL%] is megabytes.
// Deploy with: supabase functions deploy bitlabs-callback --no-verify-jwt
import { createClient } from "jsr:@supabase/supabase-js@2";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const SECRET = Deno.env.get("BITLABS_APP_SECRET") ?? "";
const PUBLIC_URL = Deno.env.get("BITLABS_CALLBACK_URL") ?? "";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

async function hmacSha1Hex(message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(SECRET), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(message)));
  return Array.from(sig, (b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (!SECRET) return new Response("BITLABS_APP_SECRET not set", { status: 500 });

  const url = new URL(req.url);
  const query = url.search.startsWith("?") ? url.search.slice(1) : url.search;
  const at = query.lastIndexOf("&hash=");
  if (at < 0) return new Response("missing hash", { status: 400 });
  const given = decodeURIComponent(query.slice(at + 6)).toLowerCase();
  const unsigned = query.slice(0, at);

  // The function can see a different host/path than the public URL, so check both forms.
  const candidates = [`${url.origin}${url.pathname}?${unsigned}`];
  if (PUBLIC_URL) candidates.unshift(`${PUBLIC_URL}?${unsigned}`);
  let valid = false;
  for (const c of candidates) if ((await hmacSha1Hex(c)) === given) valid = true;
  if (!valid) return new Response("bad hash", { status: 403 });

  const p = url.searchParams;
  const uid = p.get("uid") ?? "";
  const tx = p.get("tx") ?? "";
  const type = (p.get("type") ?? "").toUpperCase();
  const mb = Number(p.get("val"));
  if (!UUID_RE.test(uid) || !tx || !Number.isFinite(mb) || mb === 0) return new Response("OK (ignored)");
  // BitLabs: don't pay out before the COMPLETED callback.
  if (type.includes("PENDING")) return new Response("OK (pending)");

  const { error } = await admin.rpc("credit_external", {
    p_user: uid,
    p_ref: `bitlabs:${tx}:${type}`,
    p_kb: Math.round(mb * 1024),
    p_kind: "survey",
    p_note: "BitLabs",
  });
  if (error) return new Response(error.message, { status: 500 }); // non-200 makes BitLabs retry
  return new Response("OK");
});
