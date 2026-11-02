// Creates an account for a new install, without email or anonymous sign-ins.
// Returns random credentials that only this phone stores; the app then signs in with them
// (and can always sign back in, so a lost session never loses the user's MB).
// Deploy with: supabase functions deploy device-signup --no-verify-jwt
import { createClient } from "jsr:@supabase/supabase-js@2";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

Deno.serve(async (req) => {
  if (req.method !== "POST") return Response.json({ message: "POST only" }, { status: 405 });

  const email = `${crypto.randomUUID()}@device.gigabee.app`;
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  const password = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

  const { data, error } = await admin.auth.admin.createUser({ email, password, email_confirm: true });
  if (error || !data.user) return Response.json({ message: error?.message ?? "signup failed" }, { status: 500 });

  return Response.json({ email, password, user_id: data.user.id });
});
