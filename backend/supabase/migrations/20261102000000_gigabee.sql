-- GigaBee backend: balances, ad credits (AdMob SSV only), and data-bundle redemptions.
-- Data amounts are in KB (1 GB = 1048576 KB).

-- ---------------------------------------------------------------------------
-- Economics per country. 'ZZ' is the fallback for every country without a row.
-- Update ecpm_usd from your AdMob report and data_cost_usd_per_gb from your top-up
-- provider; kb_per_ad() recomputes rewards instantly so you never pay out more than you earn.
-- ---------------------------------------------------------------------------
create table public.country_config (
  country_code         text primary key check (country_code ~ '^[A-Z]{2}$'),
  ecpm_usd             numeric(8,3) not null,              -- net rewarded eCPM you receive
  data_cost_usd_per_gb numeric(8,3) not null,              -- what 1 GB costs you
  target_margin        numeric(4,3) not null default 0.35 check (target_margin >= 0 and target_margin < 1),
  daily_ad_cap         int not null default 25,
  updated_at           timestamptz not null default now()
);

insert into public.country_config (country_code, ecpm_usd, data_cost_usd_per_gb, target_margin, daily_ad_cap)
values ('ZZ', 4.000, 0.800, 0.35, 25);

create or replace function public.kb_per_ad(c public.country_config) returns bigint
language sql immutable as $$
  select greatest(256, least(51200,
    floor((c.ecpm_usd / 1000.0) * (1 - c.target_margin) / (c.data_cost_usd_per_gb / 1048576.0))
  ))::bigint
$$;

-- ---------------------------------------------------------------------------
-- Users, ledger, bundles, redemptions
-- ---------------------------------------------------------------------------
create table public.profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  country_code text,
  balance_kb   bigint not null default 0 check (balance_kb >= 0),
  ads_today    int not null default 0,
  ads_day      date,
  lifetime_ads int not null default 0,
  flagged      boolean not null default false,          -- set true to block a fraudster
  created_at   timestamptz not null default now()
);

create table public.ledger (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  delta_kb   bigint not null,
  kind       text not null check (kind in ('ad', 'redeem', 'refund', 'bonus')),
  ref        text unique,                                -- idempotency key (AdMob transaction id, redemption id)
  note       text,
  created_at timestamptz not null default now()
);
create index ledger_user_time on public.ledger (user_id, created_at desc);

-- Only bundles YOU add are offered, so every redemption is one you priced.
create table public.bundles (
  id               uuid primary key default gen_random_uuid(),
  country_code     text not null,
  carrier          text not null,                        -- shown to users, e.g. 'Telcel'
  label            text not null,                        -- e.g. '1 GB'
  data_kb          bigint not null,                      -- MB users spend (= data they receive)
  operator_id      bigint not null,                      -- Reloadly operatorId for this bundle
  amount           numeric(10,2) not null,               -- amount sent to Reloadly
  use_local_amount boolean not null default false,
  cost_usd         numeric(10,4) not null,               -- your cost, for margin reports
  active           boolean not null default true,
  sort             int not null default 0
);

create table public.redemptions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.profiles(id),
  bundle_id   uuid not null references public.bundles(id),
  phone       text not null,
  data_kb     bigint not null,
  cost_usd    numeric(10,4) not null,
  status      text not null default 'pending' check (status in ('pending', 'success', 'failed')),
  provider_tx text,
  error       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
create index redemptions_phone_time on public.redemptions (phone, created_at desc);
create index redemptions_user_time on public.redemptions (user_id, created_at desc);

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id) values (new.id) on conflict do nothing;
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Row level security: users can read their own rows; all writes go through
-- the functions below (security definer) or the service role.
-- ---------------------------------------------------------------------------
alter table public.country_config enable row level security;
alter table public.profiles       enable row level security;
alter table public.ledger         enable row level security;
alter table public.bundles        enable row level security;
alter table public.redemptions    enable row level security;

create policy "read own profile"     on public.profiles    for select to authenticated using (id = (select auth.uid()));
create policy "read own ledger"      on public.ledger      for select to authenticated using (user_id = (select auth.uid()));
create policy "read own redemptions" on public.redemptions for select to authenticated using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Called by the app
-- ---------------------------------------------------------------------------
create or replace function public.app_state(p_country text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := auth.uid();
  v_today date := (now() at time zone 'utc')::date;
  v_p     public.profiles;
  v_c     public.country_config;
begin
  if v_uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;

  insert into public.profiles (id) values (v_uid) on conflict do nothing;
  -- Country is locked the first time (from the SIM) so users can't hop to a richer payout table.
  update public.profiles set country_code = upper(p_country)
   where id = v_uid and country_code is null and p_country ~* '^[a-z]{2}$';

  select * into v_p from public.profiles where id = v_uid;
  select * into v_c from public.country_config where country_code = v_p.country_code;
  if not found then select * into v_c from public.country_config where country_code = 'ZZ'; end if;

  return jsonb_build_object(
    'balance_kb', v_p.balance_kb,
    'ads_today',  case when v_p.ads_day = v_today then v_p.ads_today else 0 end,
    'daily_cap',  v_c.daily_ad_cap,
    'kb_per_ad',  public.kb_per_ad(v_c),
    'bundles', coalesce((
      select jsonb_agg(jsonb_build_object('id', b.id, 'carrier', b.carrier, 'label', b.label, 'data_kb', b.data_kb)
                       order by b.carrier, b.sort, b.data_kb)
        from public.bundles b
       where b.active and b.country_code = coalesce(v_p.country_code, 'ZZ')
    ), '[]'::jsonb)
  );
end $$;

create or replace function public.my_history(p_limit int default 100) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(to_jsonb(x) order by x.ts desc), '[]'::jsonb)
  from (
    select l.id, l.delta_kb, l.kind, l.note,
           (extract(epoch from l.created_at) * 1000)::bigint as ts,
           r.status
      from public.ledger l
      left join public.redemptions r on l.kind = 'redeem' and l.ref = 'redeem:' || r.id::text
     where l.user_id = auth.uid()
     order by l.created_at desc
     limit least(greatest(p_limit, 1), 200)
  ) x
$$;

-- ---------------------------------------------------------------------------
-- Called only by edge functions (service role)
-- ---------------------------------------------------------------------------
create or replace function public.credit_ad(p_user uuid, p_tx text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_today date := (now() at time zone 'utc')::date;
  v_p     public.profiles;
  v_c     public.country_config;
  v_ads   int;
  v_kb    bigint;
begin
  select * into v_p from public.profiles where id = p_user for update;
  if not found then return jsonb_build_object('ok', false, 'reason', 'no_user'); end if;
  if v_p.flagged then return jsonb_build_object('ok', false, 'reason', 'flagged'); end if;
  if exists (select 1 from public.ledger where ref = 'ad:' || p_tx) then
    return jsonb_build_object('ok', true, 'duplicate', true);
  end if;

  select * into v_c from public.country_config where country_code = v_p.country_code;
  if not found then select * into v_c from public.country_config where country_code = 'ZZ'; end if;

  v_ads := case when v_p.ads_day = v_today then v_p.ads_today else 0 end;
  if v_ads >= v_c.daily_ad_cap then return jsonb_build_object('ok', false, 'reason', 'cap'); end if;

  v_kb := public.kb_per_ad(v_c);
  insert into public.ledger (user_id, delta_kb, kind, ref) values (p_user, v_kb, 'ad', 'ad:' || p_tx);
  update public.profiles
     set balance_kb = balance_kb + v_kb, ads_today = v_ads + 1, ads_day = v_today, lifetime_ads = lifetime_ads + 1
   where id = p_user;
  return jsonb_build_object('ok', true, 'kb', v_kb);
end $$;

create or replace function public.start_redemption(p_user uuid, p_bundle uuid, p_phone text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_p public.profiles;
  v_b public.bundles;
  v_r public.redemptions;
  v_phone_recent int;
  v_user_recent  int;
begin
  select * into v_p from public.profiles where id = p_user for update;
  if not found or v_p.flagged then return jsonb_build_object('ok', false, 'reason', 'blocked'); end if;

  select * into v_b from public.bundles
   where id = p_bundle and active and country_code = coalesce(v_p.country_code, 'ZZ');
  if not found then return jsonb_build_object('ok', false, 'reason', 'bundle'); end if;
  if v_p.balance_kb < v_b.data_kb then return jsonb_build_object('ok', false, 'reason', 'balance'); end if;

  -- Anti-abuse: max 4 bundles per phone number and per account every 30 days.
  select count(*) into v_phone_recent from public.redemptions
   where phone = p_phone and status <> 'failed' and created_at > now() - interval '30 days';
  select count(*) into v_user_recent from public.redemptions
   where user_id = p_user and status <> 'failed' and created_at > now() - interval '30 days';
  if v_phone_recent >= 4 or v_user_recent >= 4 then return jsonb_build_object('ok', false, 'reason', 'limit'); end if;

  insert into public.redemptions (user_id, bundle_id, phone, data_kb, cost_usd)
  values (p_user, v_b.id, p_phone, v_b.data_kb, v_b.cost_usd) returning * into v_r;
  insert into public.ledger (user_id, delta_kb, kind, ref, note)
  values (p_user, -v_b.data_kb, 'redeem', 'redeem:' || v_r.id, v_b.label || ' → •••• ' || right(p_phone, 4));
  update public.profiles set balance_kb = balance_kb - v_b.data_kb where id = p_user;

  return jsonb_build_object(
    'ok', true, 'redemption_id', v_r.id, 'operator_id', v_b.operator_id, 'amount', v_b.amount,
    'use_local_amount', v_b.use_local_amount, 'country_code', v_b.country_code, 'label', v_b.label
  );
end $$;

create or replace function public.finish_redemption(p_id uuid, p_status text, p_tx text, p_error text) returns void
language plpgsql security definer set search_path = public as $$
declare v_r public.redemptions;
begin
  update public.redemptions
     set status = p_status, provider_tx = coalesce(p_tx, provider_tx), error = p_error, updated_at = now()
   where id = p_id and status = 'pending'
  returning * into v_r;

  if found and p_status = 'failed' then
    insert into public.ledger (user_id, delta_kb, kind, ref, note)
    values (v_r.user_id, v_r.data_kb, 'refund', 'refund:' || v_r.id, 'Refund')
    on conflict (ref) do nothing;
    update public.profiles set balance_kb = balance_kb + v_r.data_kb where id = v_r.user_id;
  end if;
end $$;

revoke execute on function public.app_state(text)                       from public, anon;
revoke execute on function public.my_history(int)                       from public, anon;
revoke execute on function public.credit_ad(uuid, text)                 from public, anon, authenticated;
revoke execute on function public.start_redemption(uuid, uuid, text)    from public, anon, authenticated;
revoke execute on function public.finish_redemption(uuid, text, text, text) from public, anon, authenticated;
revoke execute on function public.kb_per_ad(public.country_config)      from public, anon, authenticated;
grant  execute on function public.app_state(text)  to authenticated;
grant  execute on function public.my_history(int)  to authenticated;
grant  execute on function public.credit_ad(uuid, text), public.start_redemption(uuid, uuid, text),
                           public.finish_redemption(uuid, text, text, text) to service_role;

-- ---------------------------------------------------------------------------
-- Profit dashboard (run in the SQL editor): last 30 days per country.
-- ---------------------------------------------------------------------------
create or replace view public.economics_30d with (security_invoker = true) as
with cfg as (
  select p.id as user_id, coalesce(c.country_code, 'ZZ') as country, coalesce(c.ecpm_usd, z.ecpm_usd) as ecpm
    from public.profiles p
    left join public.country_config c on c.country_code = p.country_code
    cross join (select ecpm_usd from public.country_config where country_code = 'ZZ') z
)
select cfg.country,
       count(l.id) filter (where l.kind = 'ad')                                   as ads_watched,
       round(sum(case when l.kind = 'ad' then cfg.ecpm / 1000 else 0 end), 2)     as est_ad_revenue_usd,
       coalesce((select sum(r.cost_usd) from public.redemptions r join cfg c2 on c2.user_id = r.user_id
                  where c2.country = cfg.country and r.status = 'success'
                    and r.created_at > now() - interval '30 days'), 0)            as data_cost_usd
  from cfg
  join public.ledger l on l.user_id = cfg.user_id and l.created_at > now() - interval '30 days'
 group by cfg.country;

revoke all on public.economics_30d from anon, authenticated;
