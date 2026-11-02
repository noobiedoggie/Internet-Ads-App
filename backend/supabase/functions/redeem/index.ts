// Sends a data bundle to the user's phone through Reloadly's top-up API.
// Secrets (supabase secrets set ...): RELOADLY_CLIENT_ID, RELOADLY_CLIENT_SECRET,
// RELOADLY_SANDBOX ("true" while testing, "false" for real top-ups).
import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const admin = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const SANDBOX = (Deno.env.get("RELOADLY_SANDBOX") ?? "true") !== "false";
const TOPUPS = SANDBOX ? "https://topups-sandbox.reloadly.com" : "https://topups.reloadly.com";

const MESSAGES: Record<string, string> = {
  phone: "That phone number doesn't look right.",
  balance: "You don't have enough MB for that bundle yet.",
  bundle: "That bundle isn't available anymore.",
  limit: "Monthly limit reached for this number. Try again later.",
  blocked: "This account can't redeem right now.",
  provider: "The carrier didn't accept the top-up. Your MB were returned.",
};

const json = (body: unknown, status = 200) => Response.json(body, { status });

let token = "";
let tokenExpires = 0;

async function reloadlyToken(): Promise<string> {
  if (token && Date.now() < tokenExpires) return token;
  const res = await fetch("https://auth.reloadly.com/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      client_id: Deno.env.get("RELOADLY_CLIENT_ID"),
      client_secret: Deno.env.get("RELOADLY_CLIENT_SECRET"),
      grant_type: "client_credentials",
      audience: TOPUPS,
    }),
  });
  if (!res.ok) throw new Error(`Reloadly auth failed: ${res.status}`);
  const body = await res.json();
  token = body.access_token;
  tokenExpires = Date.now() + (body.expires_in - 300) * 1000;
  return token;
}

class ProviderRejected extends Error {}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, message: "POST only" }, 405);

  const { data: { user } } = await admin.auth.getUser(
    (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, ""),
  );
  if (!user) return json({ ok: false, message: "Not signed in" }, 401);

  const { bundle_id, phone } = await req.json().catch(() => ({}));
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (digits.length < 8 || digits.length > 15 || typeof bundle_id !== "string") {
    return json({ ok: false, code: "phone", message: MESSAGES.phone }, 400);
  }

  const { data: start, error } = await admin.rpc("start_redemption", {
    p_user: user.id,
    p_bundle: bundle_id,
    p_phone: digits,
  });
  if (error) return json({ ok: false, message: error.message }, 500);
  if (!start.ok) return json({ ok: false, code: start.reason, message: MESSAGES[start.reason] }, 400);

  try {
    const res = await fetch(`${TOPUPS}/topups`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${await reloadlyToken()}`,
        Accept: "application/com.reloadly.topups-v1+json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        operatorId: start.operator_id,
        amount: start.amount,
        useLocalAmount: start.use_local_amount,
        customIdentifier: start.redemption_id,
        recipientPhone: { countryCode: start.country_code, number: digits },
      }),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new ProviderRejected(body.message ?? `HTTP ${res.status}`);

    const status = body.status === "SUCCESSFUL" ? "success"
      : body.status === "FAILED" || body.status === "REFUNDED" ? "failed" : "pending";
    await admin.rpc("finish_redemption", {
      p_id: start.redemption_id, p_status: status, p_tx: String(body.transactionId ?? ""), p_error: null,
    });
    if (status === "failed") return json({ ok: false, code: "provider", message: MESSAGES.provider }, 502);
    return json({ ok: true, status });
  } catch (e) {
    if (e instanceof ProviderRejected) {
      // Reloadly said no: nothing was sent, so refund.
      await admin.rpc("finish_redemption", {
        p_id: start.redemption_id, p_status: "failed", p_tx: null, p_error: e.message,
      });
      return json({ ok: false, code: "provider", message: MESSAGES.provider }, 502);
    }
    // Network error / timeout: the top-up may have gone through. Leave it 'pending' for
    // manual review (search Reloadly by customIdentifier = redemption id) instead of refunding.
    await admin.from("redemptions").update({ error: String(e) }).eq("id", start.redemption_id);
    return json({ ok: true, status: "pending" });
  }
});
