# GigaBee 🐝

Watch short ads, earn megabytes, send real mobile data to your phone.
Minimal, black and yellow, three screens: **Earn**, **Redeem**, **Activity**. English and Spanish.

## How it makes money

Every rewarded ad pays you its eCPM ÷ 1000. GigaBee gives users only part of that as data:

```
MB per ad = (eCPM / 1000) × (1 − margin) ÷ (your cost per MB)
```

The backend computes this **per country** from the numbers you put in `country_config`.
Update them when your AdMob eCPM or data prices change, and rewards adjust right away.
The app never decides payouts itself.

| Your eCPM | Your cost for 1 GB | Margin | MB per ad | Ads per 1 GB | You earn per GB redeemed |
|---|---|---|---|---|---|
| $4 | $0.80 | 35% | 3.3 | ~308 | $0.43 |
| $8 | $0.80 | 35% | 6.7 | ~154 | $0.43 |
| $4 | $0.30 | 35% | 8.9 | ~116 | $0.16 |
| $4 | $2.00 | 35% | 1.3 | ~769 | $1.08 |

These are example inputs, not real market prices. Use your own AdMob report and top-up price list.

Other profit protections already built in:
- **Server-side verification (SSV):** only Google's signed callback can add MB, so a hacked app can't add any.
- **Daily cap** per user (default 25 ads) and a **country lock** (set once, from the SIM).
- **Only bundles you add** are offered, so you price each one yourself.
- **Anti-abuse:** max 4 bundles per phone number and per account every 30 days, plus a `flagged` switch per user.
- Unredeemed balances stay as MB, and they only cost you if someone redeems them.
- **Profit view:** `select * from economics_30d;` shows ads watched, estimated revenue and data cost per country.

## Target markets: USA, France and Europe

The app is in English, French (with Mo/Go units), German, Italian and Spanish. Google's consent form
for Europe (GDPR) is already built in.

**Problem:** in the US and France most people pay a monthly plan, and many have unlimited or very
cheap data. A carrier top-up API like Reloadly can only add credit to **prepaid** lines, and it mostly
sends airtime (call/text credit), not data packs. So carrier top-ups would reach only a small share of users.

**Recommended instead: a GigaBee eSIM.** Use an eSIM reseller API (for example Airalo Partners,
eSIM Go or eSIM Access) to offer your own data plans inside the app. This works on any recent phone,
on any carrier, in the US and all of Europe. Users install the GigaBee eSIM once and then:
- **earn** data on it by watching ads (the current points system, unchanged), and
- **buy** extra GB in the app (a second income on top of ads).

This also helps with ad network rules: the reward becomes credit for **your own in-app service**,
the same model carriers use for "watch ads, get data". AdMob allows rewards that are only usable
inside the publisher's own app or service. Still get written confirmation from your ad network.

Example economics, using the same formula and a 35% margin. These are examples, not real quotes:
put your real AdMob eCPM and your eSIM supplier's price per GB into `country_config`.

| Market | Example eCPM | Example cost per GB | MB per ad | Ads per 1 GB | You earn per GB |
|---|---|---|---|---|---|
| USA | $15 | $2.00 | 5.0 | ~205 | $1.08 |
| France | $8 | $2.00 | 2.7 | ~385 | $1.08 |

Before publishing in the EU you also need a privacy policy URL, and a "delete my account" option
because the Play Store requires one for apps that create accounts.

## BitLabs surveys (bigger rewards)

The Earn screen has a **Surveys** button that opens the BitLabs survey wall inside the app. In the demo
(no token) it shows a fake survey worth 100 MB so you can see the flow.

1. Sign up at bitlabs.ai, create an app, and copy the **App Token** and **App Secret**.
2. In the app settings, set the currency name to **MB** and choose how many MB users get per $1 you earn.
   To keep the same margin as ads: `MB per $1 = (1 − margin) ÷ cost per MB`.
   Example: $2/GB and a 35% margin gives 0.65 ÷ (2/1024) ≈ **333 MB per $1**.
3. Set the callback URL, and check that the placeholder names match the ones listed in the dashboard:
   `https://<project>.supabase.co/functions/v1/bitlabs-callback?uid=[%UID%]&val=[%VAL%]&tx=[%TX%]&type=[%TYPE%]`
4. Deploy and add the secrets:
   ```bash
   supabase db push
   ```
   ```bash
   supabase functions deploy bitlabs-callback --no-verify-jwt
   ```
   ```bash
   supabase secrets set BITLABS_APP_SECRET=... BITLABS_CALLBACK_URL=https://<project>.supabase.co/functions/v1/bitlabs-callback
   ```
5. Add `BITLABS_TOKEN` as a GitHub secret (or in `gradle.properties`) and push. The next APK opens real surveys.

Only BitLabs' signed callback can add survey MB. Pending callbacks are ignored, and reversals are
deducted (never below 0).

## ⚠️ Read before launch: ad network rules

AdMob (and Unity Ads) **don't allow rewards with real-world value** that can be used outside your app.
Mobile data from a carrier probably counts as that. Running this on AdMob could get the account banned.
Pick one of these before going live:
1. **Partner with a carrier/MVNO.** The data is then their in-app reward, which is the model carriers use for "watch ads, get data".
2. **Use an offerwall or network made for reward apps** (for example adjoe, Tapjoy, ayeT-Studios, AdGem). Get written confirmation from them that mobile data rewards are allowed.
3. **Ask Unity/AppLovin account managers** for written approval of real-world rewards.

The ad code is isolated in `app/src/main/java/com/gigabee/app/ads/`, so swapping networks only touches that folder.

## Build the APK

**Option A, GitHub (no setup):** push this folder to a GitHub repo. The included workflow builds
`gigabee-apk` on every push. Download it from the run's *Artifacts* section.

**Option B, Android Studio:** open the folder and press ▶ Run. Or from a terminal with the Android SDK installed:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With no keys set you get the **demo**: Google test ads and a local balance, with redemptions simulated.
In debug builds, **long-press the ring** to add 100 MB so you can test redeeming.

## Go live

1. **AdMob:** create the app and a *Rewarded* ad unit (plus a banner, optional). In the rewarded unit,
   turn on *Server-side verification* with URL `https://<project>.supabase.co/functions/v1/admob-ssv`.
2. **Supabase:** create a project, enable *Anonymous sign-ins*, then:
   ```bash
   supabase link --project-ref <project-ref>
   ```
   ```bash
   supabase db push
   ```
   ```bash
   supabase functions deploy admob-ssv --no-verify-jwt
   ```
   ```bash
   supabase functions deploy redeem
   ```
   ```bash
   supabase secrets set RELOADLY_CLIENT_ID=... RELOADLY_CLIENT_SECRET=... RELOADLY_SANDBOX=true ADMOB_REWARDED_AD_UNIT=<numeric id after the slash>
   ```
3. **Reloadly** (or another top-up provider): find the operator IDs and data bundle amounts for your
   country, then add them. 1 GB = 1048576 KB:
   ```sql
   insert into country_config values ('MX', 4.0, 0.80, 0.35, 25, now());
   insert into bundles (country_code, carrier, label, data_kb, operator_id, amount, cost_usd)
   values ('MX', 'Telcel', '1 GB', 1048576, <operatorId>, <amount>, <your cost>);
   ```
4. **Build with your keys:** set `ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_BANNER_ID`, `SUPABASE_URL` and
   `SUPABASE_ANON_KEY` in `gradle.properties` or as GitHub secrets. For a Play Store release, also set
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
5. Test with `RELOADLY_SANDBOX=true`, then switch it to `false`.

## Project layout

```
app/                       Android app (Kotlin, Jetpack Compose)
  data/Economics.kt        payout formula (same as the SQL)
  data/*Repository.kt      demo (local) and Supabase backends
  ads/                     AdMob rewarded + banner + consent (swap networks here)
  ui/                      Earn, Redeem, Activity screens, theme
backend/supabase/
  migrations/              tables, RLS, credit/redeem functions, profit view
  functions/admob-ssv      verifies Google's signature, credits MB
  functions/redeem         sends the bundle via Reloadly, refunds on failure
.github/workflows/         builds the APK in the cloud
```

## Next steps worth doing

- Play Integrity API check before redeeming, to block emulators and farms.
- Account linking (Google sign-in) so balances survive a reinstall.
- A scheduled job that pulls your real eCPM per country from the AdMob API into `country_config`.
