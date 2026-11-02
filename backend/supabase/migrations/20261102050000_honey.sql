-- Honey economy: ads earn 🍯 honey drops. Honey buys "co" (data) cards directly, or spins the lucky wheel.
-- 1 drop = 0.1 MB of data value (102.4 KB), so kb_per_ad() still decides what an ad is worth.
-- For ledger rows with currency = 'honey', delta_kb holds a number of drops.

alter table public.profiles add column honey bigint not null default 0 check (honey >= 0);
alter table public.ledger add column currency text not null default 'kb' check (currency in ('kb', 'honey'));
alter table public.ledger add column card_kb bigint;
alter table public.ledger drop constraint ledger_kind_check;
alter table public.ledger add constraint ledger_kind_check
  check (kind in ('ad', 'redeem', 'refund', 'bonus', 'survey', 'spin', 'buy'));

-- Co cards: the user's data, usable later for eSIM bundles. profiles.balance_kb = sum of unused cards.
create table public.cards (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  kb         bigint not null check (kb > 0),
  source     text not null,              -- spin | buy | legacy | change | refund
  used_at    timestamptz,
  created_at timestamptz not null default now()
);
create index cards_user_unused on public.cards (user_id) where used_at is null;
alter table public.cards enable row level security;
create policy "read own cards" on public.cards for select to authenticated using (user_id = (select auth.uid()));

-- Existing MB become a card so nobody loses anything.
insert into public.cards (user_id, kb, source) select id, balance_kb, 'legacy' from public.profiles where balance_kb > 0;

create or replace function public.card_rarity(p_kb bigint) returns text
language sql immutable set search_path = public as $$
  select case when p_kb >= 512000 then 'legendary' when p_kb >= 102400 then 'epic'
              when p_kb >= 25600 then 'rare' when p_kb >= 10240 then 'uncommon' else 'common' end
$$;

-- Game settings (edit these rows to tune the game; the app shows whatever is here).
create table public.game_config (
  id             int primary key default 1 check (id = 1),
  spin_cost      int not null,   -- drops per wheel spin
  referral_honey int not null    -- drops for each side of a successful invite
);
insert into public.game_config values (1, 100, 500);

-- Wheel prizes: chance = weight / sum(weight). Average win ≈ 7.5 MB for a 100-drop (10 MB) spin.
create table public.wheel_prizes (kb bigint primary key, weight int not null check (weight > 0));
insert into public.wheel_prizes values
  (2048, 3990),     -- 2 MB    39.9 %
  (5120, 3900),     -- 5 MB    39 %
  (10240, 1500),    -- 10 MB   15 %
  (25600, 500),     -- 25 MB   5 %
  (102400, 100),    -- 100 MB  1 %
  (1048576, 10);    -- 1 GB    0.1 %

-- Direct swap: always 1 drop = 0.1 MB.
create table public.shop_items (kb bigint primary key, cost int not null check (cost > 0));
insert into public.shop_items values (10240, 100), (51200, 500), (102400, 1000);

alter table public.game_config  enable row level security;
alter table public.wheel_prizes enable row level security;
alter table public.shop_items   enable row level security;

create or replace function public.honey_per_ad(c public.country_config, p_pct int) returns int
language sql immutable set search_path = public as $$
  select floor(public.kb_per_ad(c) * (100 + p_pct) / 100.0 / 102.4)::int
$$;

create or replace function public.credit_ad(p_user uuid, p_tx text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_today  date := (now() at time zone 'utc')::date;
  v_p      public.profiles;
  v_c      public.country_config;
  v_ads    int;
  v_streak int;
  v_pct    int;
  v_drops  int;
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

  v_streak := public.next_streak(v_p, v_today);
  v_pct := public.streak_bonus_pct(v_streak);
  v_drops := public.honey_per_ad(v_c, v_pct);

  insert into public.ledger (user_id, delta_kb, kind, ref, note, currency)
  values (p_user, v_drops, 'ad', 'ad:' || p_tx, case when v_pct > 0 then '🔥 +' || v_pct || '%' end, 'honey');
  update public.profiles
     set honey = honey + v_drops, ads_today = v_ads + 1, ads_day = v_today,
         lifetime_ads = lifetime_ads + 1, streak = v_streak
   where id = p_user;

  if v_p.referred_by is not null and not v_p.referral_paid and v_p.lifetime_ads + 1 >= 30 then
    perform public.pay_referral(p_user);
  end if;
  return jsonb_build_object('ok', true, 'honey', v_drops, 'streak', v_streak);
end $$;

create or replace function public.pay_referral(p_user uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  v_ref   uuid;
  v_drops int := (select referral_honey from public.game_config where id = 1);
begin
  update public.profiles set referral_paid = true
   where id = p_user and not referral_paid and referred_by is not null
  returning referred_by into v_ref;
  if not found then return; end if;

  insert into public.ledger (user_id, delta_kb, kind, ref, note, currency)
  values (p_user, v_drops, 'bonus', 'referral-in:' || p_user, 'Referral', 'honey');
  update public.profiles set honey = honey + v_drops where id = p_user;

  if exists (select 1 from public.profiles where id = v_ref and not flagged) then
    insert into public.ledger (user_id, delta_kb, kind, ref, note, currency)
    values (v_ref, v_drops, 'bonus', 'referral-out:' || p_user, 'Referral', 'honey');
    update public.profiles set honey = honey + v_drops where id = v_ref;
  end if;
end $$;

-- Survey partners still report MB; we pay the same value in honey (1 MB = 10 drops).
create or replace function public.credit_external(p_user uuid, p_ref text, p_kb bigint, p_kind text, p_note text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_p     public.profiles;
  v_delta bigint;
begin
  select * into v_p from public.profiles where id = p_user for update;
  if not found then return jsonb_build_object('ok', false, 'reason', 'no_user'); end if;
  if exists (select 1 from public.ledger where ref = p_ref) then
    return jsonb_build_object('ok', true, 'duplicate', true);
  end if;
  v_delta := greatest(round(p_kb / 102.4)::bigint, -v_p.honey);
  if v_delta = 0 then return jsonb_build_object('ok', true, 'honey', 0); end if;
  insert into public.ledger (user_id, delta_kb, kind, ref, note, currency) values (p_user, v_delta, p_kind, p_ref, p_note, 'honey');
  update public.profiles set honey = honey + v_delta where id = p_user;
  return jsonb_build_object('ok', true, 'honey', v_delta);
end $$;

create or replace function public.buy_card(p_kb bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := auth.uid();
  v_cost int;
  v_p    public.profiles;
begin
  if v_uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
  select cost into v_cost from public.shop_items where kb = p_kb;
  if not found then return jsonb_build_object('ok', false, 'reason', 'item'); end if;
  select * into v_p from public.profiles where id = v_uid for update;
  if v_p.honey < v_cost then return jsonb_build_object('ok', false, 'reason', 'honey'); end if;

  update public.profiles set honey = honey - v_cost, balance_kb = balance_kb + p_kb where id = v_uid;
  insert into public.cards (user_id, kb, source) values (v_uid, p_kb, 'buy');
  insert into public.ledger (user_id, delta_kb, kind, currency, card_kb) values (v_uid, -v_cost, 'buy', 'honey', p_kb);
  return jsonb_build_object('ok', true, 'kb', p_kb, 'rarity', public.card_rarity(p_kb), 'honey', v_p.honey - v_cost);
end $$;

-- The draw happens here, on the server, so the app can't pick its own prize.
create or replace function public.spin_wheel() returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := auth.uid();
  v_cost  int := (select spin_cost from public.game_config where id = 1);
  v_p     public.profiles;
  v_total int;
  v_roll  int;
  v_acc   int := 0;
  v_kb    bigint;
  r       record;
begin
  if v_uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
  select * into v_p from public.profiles where id = v_uid for update;
  if v_p.honey < v_cost then return jsonb_build_object('ok', false, 'reason', 'honey'); end if;

  select sum(weight) into v_total from public.wheel_prizes;
  v_roll := floor(random() * v_total)::int;
  for r in select kb, weight from public.wheel_prizes order by kb loop
    v_acc := v_acc + r.weight;
    if v_roll < v_acc then v_kb := r.kb; exit; end if;
  end loop;

  update public.profiles set honey = honey - v_cost, balance_kb = balance_kb + v_kb where id = v_uid;
  insert into public.cards (user_id, kb, source) values (v_uid, v_kb, 'spin');
  insert into public.ledger (user_id, delta_kb, kind, currency, card_kb) values (v_uid, -v_cost, 'spin', 'honey', v_kb);
  return jsonb_build_object('ok', true, 'kb', v_kb, 'rarity', public.card_rarity(v_kb), 'honey', v_p.honey - v_cost);
end $$;

-- Uses the biggest cards first and gives change back as a new card.
create or replace function public.consume_cards(p_user uuid, p_kb bigint) returns void
language plpgsql security definer set search_path = public as $$
declare
  v_left bigint := p_kb;
  r      record;
begin
  for r in select id, kb from public.cards where user_id = p_user and used_at is null order by kb desc, id loop
    exit when v_left <= 0;
    update public.cards set used_at = now() where id = r.id;
    v_left := v_left - r.kb;
  end loop;
  if v_left < 0 then insert into public.cards (user_id, kb, source) values (p_user, -v_left, 'change'); end if;
end $$;

create or replace function public.start_esim_redemption(p_user uuid, p_bundle uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_p public.profiles;
  v_b public.bundles;
  v_e public.esims;
  v_r public.redemptions;
  v_recent int;
begin
  select * into v_p from public.profiles where id = p_user for update;
  if not found or v_p.flagged then return jsonb_build_object('ok', false, 'reason', 'blocked'); end if;
  select * into v_b from public.bundles
   where id = p_bundle and active and provider_item is not null
     and (country_code = 'ESIM' or country_code = coalesce(v_p.country_code, 'ZZ'));
  if not found then return jsonb_build_object('ok', false, 'reason', 'bundle'); end if;
  if v_p.balance_kb < v_b.data_kb then return jsonb_build_object('ok', false, 'reason', 'balance'); end if;
  select count(*) into v_recent from public.redemptions
   where user_id = p_user and status <> 'failed' and created_at > now() - interval '30 days';
  if v_recent >= 6 then return jsonb_build_object('ok', false, 'reason', 'limit'); end if;
  select * into v_e from public.esims where user_id = p_user;

  insert into public.redemptions (user_id, bundle_id, iccid, data_kb, cost_usd)
  values (p_user, v_b.id, v_e.iccid, v_b.data_kb, v_b.cost_usd) returning * into v_r;
  insert into public.ledger (user_id, delta_kb, kind, ref, note)
  values (p_user, -v_b.data_kb, 'redeem', 'redeem:' || v_r.id, v_b.carrier || ' · ' || v_b.label || ' eSIM');
  update public.profiles set balance_kb = balance_kb - v_b.data_kb where id = p_user;
  perform public.consume_cards(p_user, v_b.data_kb);

  return jsonb_build_object('ok', true, 'redemption_id', v_r.id, 'item', v_b.provider_item,
                            'iccid', v_e.iccid, 'label', v_b.label);
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
    insert into public.cards (user_id, kb, source) values (v_r.user_id, v_r.data_kb, 'refund');
  end if;
end $$;

create or replace function public.app_state(p_country text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := auth.uid();
  v_today date := (now() at time zone 'utc')::date;
  v_p     public.profiles;
  v_c     public.country_config;
  v_e     public.esims;
  v_g     public.game_config;
  v_pct   int;
begin
  if v_uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
  insert into public.profiles (id, nickname, referral_code)
  values (v_uid, public.random_nickname(), public.new_referral_code()) on conflict do nothing;
  update public.profiles set country_code = upper(p_country)
   where id = v_uid and country_code is null and p_country ~* '^[a-z]{2}$';
  update public.profiles
     set nickname = coalesce(nickname, public.random_nickname()),
         referral_code = coalesce(referral_code, public.new_referral_code())
   where id = v_uid and (nickname is null or referral_code is null);

  select * into v_p from public.profiles where id = v_uid;
  select * into v_c from public.country_config where country_code = v_p.country_code;
  if not found then select * into v_c from public.country_config where country_code = 'ZZ'; end if;
  select * into v_e from public.esims where user_id = v_uid;
  select * into v_g from public.game_config where id = 1;
  v_pct := public.streak_bonus_pct(public.next_streak(v_p, v_today));

  return jsonb_build_object(
    'balance_kb', v_p.balance_kb,
    'honey', v_p.honey,
    'honey_per_ad', public.honey_per_ad(v_c, v_pct),
    'spin_cost', v_g.spin_cost,
    'referral_honey', v_g.referral_honey,
    'ads_today',  case when v_p.ads_day = v_today then v_p.ads_today else 0 end,
    'daily_cap',  v_c.daily_ad_cap,
    'kb_per_ad',  public.kb_per_ad(v_c) * (100 + v_pct) / 100,
    'streak',     case when v_p.ads_day >= v_today - 1 then v_p.streak else 0 end,
    'streak_bonus_pct', v_pct,
    'nickname',   v_p.nickname,
    'referral_code', v_p.referral_code,
    'referred',   v_p.referred_by is not null,
    'referral_paid', v_p.referral_paid,
    'can_enter_referral', v_p.referred_by is null and v_p.created_at > now() - interval '7 days',
    'lifetime_ads', v_p.lifetime_ads,
    'referral_target', 30,
    'wheel', coalesce((select jsonb_agg(jsonb_build_object('kb', w.kb, 'weight', w.weight, 'rarity', public.card_rarity(w.kb))
                                        order by w.kb) from public.wheel_prizes w), '[]'::jsonb),
    'shop', coalesce((select jsonb_agg(jsonb_build_object('kb', s.kb, 'cost', s.cost) order by s.kb)
                        from public.shop_items s), '[]'::jsonb),
    'cards', coalesce((select jsonb_agg(jsonb_build_object('kb', x.kb, 'rarity', public.card_rarity(x.kb), 'count', x.n)
                                        order by x.kb desc)
                         from (select kb, count(*) as n from public.cards
                                where user_id = v_uid and used_at is null group by kb) x), '[]'::jsonb),
    'esim', case when v_e.iccid is null then null else
      jsonb_build_object('iccid', v_e.iccid, 'smdp', v_e.smdp_address, 'code', v_e.matching_id) end,
    'bundles', coalesce((
      select jsonb_agg(jsonb_build_object('id', b.id, 'carrier', b.carrier, 'label', b.label, 'data_kb', b.data_kb)
                       order by b.carrier, b.sort, b.data_kb)
        from public.bundles b
       where b.active and b.provider_item is not null
         and (b.country_code = 'ESIM' or b.country_code = coalesce(v_p.country_code, 'ZZ'))
    ), '[]'::jsonb)
  );
end $$;

create or replace function public.my_history(p_limit int default 100) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(to_jsonb(x) order by x.ts desc), '[]'::jsonb)
  from (
    select l.id, l.delta_kb, l.kind, l.note, l.currency, l.card_kb,
           (extract(epoch from l.created_at) * 1000)::bigint as ts,
           r.status
      from public.ledger l
      left join public.redemptions r on l.kind = 'redeem' and l.ref = 'redeem:' || r.id::text
     where l.user_id = auth.uid()
     order by l.created_at desc
     limit least(greatest(p_limit, 1), 200)
  ) x
$$;

-- Weekly ranking (Monday 00:00 UTC) by honey earned.
create or replace function public.leaderboard() returns jsonb
language sql stable security definer set search_path = public as $$
  with week as (select (date_trunc('week', now() at time zone 'utc')) at time zone 'utc' as start),
  totals as (
    select l.user_id, sum(l.delta_kb) as honey
      from public.ledger l, week
     where l.created_at >= week.start and l.currency = 'honey' and l.delta_kb > 0
       and l.kind in ('ad', 'bonus', 'survey')
     group by l.user_id
  ),
  ranked as (
    select t.user_id, t.honey, coalesce(p.nickname, 'Bee') as nickname, rank() over (order by t.honey desc) as rnk
      from totals t join public.profiles p on p.id = t.user_id
     where not p.flagged
  )
  select jsonb_build_object(
    'top', coalesce((
      select jsonb_agg(jsonb_build_object('rank', x.rnk, 'nickname', x.nickname, 'honey', x.honey, 'me', x.user_id = auth.uid())
                       order by x.rnk, x.honey desc)
        from (select * from ranked order by rnk limit 20) x
    ), '[]'::jsonb),
    'me', (select jsonb_build_object('rank', rnk, 'honey', honey) from ranked where user_id = auth.uid())
  )
$$;

revoke execute on function public.card_rarity(bigint), public.honey_per_ad(public.country_config, int),
                         public.consume_cards(uuid, bigint)
  from public, anon, authenticated;
revoke execute on function public.buy_card(bigint), public.spin_wheel() from public, anon;
grant  execute on function public.buy_card(bigint), public.spin_wheel() to authenticated;
