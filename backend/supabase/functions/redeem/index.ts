// Turns a user's MB into real data on their GigaBee eSIM, through the eSIM Go API.
// First redemption: buys a new eSIM with the bundle and saves its install code.
// Later redemptions: adds the bundle to the same eSIM (no reinstall).
//
// Secrets: ESIMGO_API_KEY, ESIMGO_MODE ("validate" = test without paying, "transaction" = real).
import { createClient } from "jsr:@supabase/supabase-js@2";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const API = "https://api.esim-go.com/v2.4";
const API_KEY = Deno.env.get("ESIMGO_API_KEY") ?? "";
const MODE = Deno.env.get("ESIMGO_MODE") === "transaction" ? "transaction" : "validate";

const MESSAGES: Record<string, string> = {
  balance: "You don't have enough MB for that bundle yet.",
  bundle: "That bundle isn't available anymore.",
  limit: "Monthly limit reached. Try again later.",
  blocked: "This account can't redeem right now.",
  provider: "The eSIM provider didn't accept the order. Your MB were returned.",
};

const json = (body: unknown, status = 200) => Response.json(body, { status });

type Esim = { iccid: string; smdp: string; code: string };

// Accepts both the JSON order response and the assignments format.
function findEsim(value: unknown): Esim | null {
  if (!value || typeof value !== "object") return null;
  if (Array.isArray(value)) {
    for (const v of value) {
      const e = findEsim(v);
      if (e) return e;
    }
    return null;
  }
  const o = value as Record<string, unknown>;
  const iccid = o.iccid ?? o.ICCID;
  const code = o.matchingId ?? o["Matching ID"];
  const smdp = o.smdpAddress ?? o["RSP URL"];
  if (iccid && code && smdp) return { iccid: String(iccid), code: String(code), smdp: String(smdp) };
  for (const v of Object.values(o)) {
    const e = findEsim(v);
    if (e) return e;
  }
  return null;
}

class ProviderRejected extends Error {}

async function esimGo(path: string, init: RequestInit = {}) {
  const res = await fetch(`${API}${path}`, {
    ...init,
    headers: { "X-API-Key": API_KEY, "Content-Type": "application/json", Accept: "application/json" },
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new ProviderRejected(body.message ?? `HTTP ${res.status}`);
  return body;
}

async function orderBundle(item: string, iccid: string | null) {
  const line: Record<string, unknown> = { type: "bundle", quantity: 1, item };
  if (iccid) Object.assign(line, { iccids: [iccid], allowReassign: false });
  const body = await esimGo("/orders", {
    method: "POST",
    body: JSON.stringify({ type: MODE, assign: true, order: [line] }),
  });
  let esim = findEsim(body.order ?? body);
  if (!esim && !iccid && body.orderReference && MODE === "transaction") {
    esim = findEsim(await esimGo(`/esims/assignments?reference=${encodeURIComponent(body.orderReference)}`));
  }
  return { reference: String(body.orderReference ?? ""), esim };
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, message: "POST only" }, 405);
  if (!API_KEY) return json({ ok: false, message: "ESIMGO_API_KEY not set" }, 500);

  const { data: { user } } = await admin.auth.getUser(
    (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, ""),
  );
  if (!user) return json({ ok: false, message: "Not signed in" }, 401);

  const { bundle_id } = await req.json().catch(() => ({}));
  if (typeof bundle_id !== "string") return json({ ok: false, code: "bundle", message: MESSAGES.bundle }, 400);

  const { data: start, error } = await admin.rpc("start_esim_redemption", { p_user: user.id, p_bundle: bundle_id });
  if (error) return json({ ok: false, message: error.message }, 500);
  if (!start.ok) return json({ ok: false, code: start.reason, message: MESSAGES[start.reason] }, 400);

  const refund = (why: string) =>
    admin.rpc("finish_redemption", { p_id: start.redemption_id, p_status: "failed", p_tx: null, p_error: why });

  try {
    const { reference, esim } = await orderBundle(start.item, start.iccid);

    if (MODE === "validate") {
      // Test mode: eSIM Go checked the order but charged nothing and sent no data.
      await refund("validate mode (test, nothing sent)");
      return json({ ok: false, code: "test", message: "Test mode: order is valid, nothing was sent. MB returned." });
    }

    if (!start.iccid) {
      if (!esim) throw new Error(`order ${reference} returned no eSIM details`);
      await admin.rpc("save_esim", { p_user: user.id, p_iccid: esim.iccid, p_smdp: esim.smdp, p_code: esim.code });
    }
    await admin.rpc("finish_redemption", { p_id: start.redemption_id, p_status: "success", p_tx: reference, p_error: null });
    await admin.from("redemptions").update({ iccid: start.iccid ?? esim?.iccid }).eq("id", start.redemption_id);
    return json({ ok: true, status: "success", new_esim: !start.iccid });
  } catch (e) {
    if (e instanceof ProviderRejected) {
      await refund(e.message);
      return json({ ok: false, code: "provider", message: MESSAGES.provider }, 502);
    }
    // Network error or missing details: the order may have gone through. Keep it 'pending' for a manual
    // check in the eSIM Go portal instead of refunding automatically.
    await admin.from("redemptions").update({ error: String(e) }).eq("id", start.redemption_id);
    return json({ ok: true, status: "pending" });
  }
});
