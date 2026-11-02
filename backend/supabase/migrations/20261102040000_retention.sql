-- Retention: daily streak bonus, weekly leaderboard, generated nicknames, friend referrals.

alter table public.profiles
  add column nickname      text,
  add column streak        int not null default 0,
  add column referral_code text unique,
  add column referred_by   uuid references public.profiles(id),
  add column referral_paid boolean not null default false;

-- Fun, safe nicknames (no free text, so no offensive or personal names).
create or replace function public.random_nickname() returns text
language sql volatile set search_path = public as $$
  select (array['Golden','Swift','Lucky','Turbo','Mega','Super','Happy','Brave',
                'Cosmic','Neon','Royal','Speedy','Shiny','Mighty','Jolly','Fuzzy'])[1 + floor(random() * 16)::int]
      || (array['Bee','Hornet','Honey','Buzz','Hive','Pollen','Comb','Queen','Drone','Nectar'])[1 + floor(random() * 10)::int]
      || (100 + floor(random() * 900)::int)::text
$$;

create or replace function public.new_referral_code() returns text
language sql volatile set search_path = public as $$
  select upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6))
$$;

update public.profiles
   set nickname = coalesce(nickname, public.random_nickname()),
       referral_code = coalesce(referral_code, public.new_referral_code());

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, nickname, referral_code)
  values (new.id, public.random_nickname(), public.new_referral_code())
  on conflict do nothing;
  return new;
end $$;

-- Streak bonus: 2 days +5%, 3 days +10%, 5 days +15%, 7+ days +20% (paid from the margin).
create or replace function public.streak_bonus_pct(p_streak int) returns int
language sql immutable set search_path = public as $$
  select case when p_streak >= 7 then 20 when p_streak >= 5 then 15
              when p_streak >= 3 then 10 when p_streak >= 2 then 5 else 0 end
$$;

-- The streak value the user's next ad counts toward.
create or replace function public.next_streak(p public.profiles, p_today date) returns int
language sql stable set search_path = public as $$
  select case when p.ads_day = p_today then greatest(p.streak, 1)
              when p.ads_day = p_today - 1 then p.streak + 1
              else 1 end
$$;

-- Referral reward: 50 MB to both once the invited user has watched 30 verified ads.
create or replace function public.pay_referral(p_user uuid) returns void
language plpgsql security definer set search_path = public as $$
declare v_ref uuid;
begin
  update public.profiles set referral_paid = true
   where id = p_user and not referral_paid and referred_by is not null
  returning referred_by into v_ref;
  if not found then return; end if;

  insert into public.ledger (user_id, delta_kb, kind, ref, note) values (p_user, 51200, 'bonus', 'referral-in:' || p_user, 'Referral');
  update public.profiles set balance_kb = balance_kb + 51200 where id = p_user;

  if exists (select 1 from public.profiles where id = v_ref and not flagged) then
    insert into public.ledger (user_id, delta_kb, kind, ref, note) values (v_ref, 51200, 'bonus', 'referral-out:' || p_user, 'Referral');
    update public.profiles set balance_kb = balance_kb + 51200 where id = v_ref;
  end if;
end $$;

create or replace function public.credit_ad(p_user uuid, p_tx text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_today  date := (now() at time zone 'utc')::date;
  v_p      public.profiles;
  v_c      public.country_config;
  v_ads    int;
  v_streak int;
  v_pct    int;
  v_kb     bigint;
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
  v_kb := public.kb_per_ad(v_c) * (100 + v_pct) / 100;

  insert into public.ledger (user_id, delta_kb, kind, ref, note)
  values (p_user, v_kb, 'ad', 'ad:' || p_tx, case when v_pct > 0 then '🔥 +' || v_pct || '%' end);
  update public.profiles
     set balance_kb = balance_kb + v_kb, ads_today = v_ads + 1, ads_day = v_today,
         lifetime_ads = lifetime_ads + 1, streak = v_streak
   where id = p_user;

  if v_p.referred_by is not null and not v_p.referral_paid and v_p.lifetime_ads + 1 >= 30 then
    perform public.pay_referral(p_user);
  end if;
  return jsonb_build_object('ok', true, 'kb', v_kb, 'streak', v_streak);
end $$;

create or replace function public.app_state(p_country text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := auth.uid();
  v_today date := (now() at time zone 'utc')::date;
  v_p     public.profiles;
  v_c     public.country_config;
  v_e     public.esims;
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
  v_pct := public.streak_bonus_pct(public.next_streak(v_p, v_today));

  return jsonb_build_object(
    'balance_kb', v_p.balance_kb,
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
    'referral_kb', 51200,
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

create or replace function public.redeem_referral(p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_me  public.profiles;
  v_ref public.profiles;
begin
  if v_uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;
  select * into v_me from public.profiles where id = v_uid for update;
  if v_me.referred_by is not null then return jsonb_build_object('ok', false, 'reason', 'already'); end if;
  if v_me.created_at < now() - interval '7 days' then return jsonb_build_object('ok', false, 'reason', 'too_late'); end if;

  select * into v_ref from public.profiles where referral_code = upper(trim(p_code));
  if not found or v_ref.id = v_uid or v_ref.referred_by = v_uid then
    return jsonb_build_object('ok', false, 'reason', 'invalid');
  end if;

  update public.profiles set referred_by = v_ref.id where id = v_uid;
  if v_me.lifetime_ads >= 30 then perform public.pay_referral(v_uid); end if;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.reroll_nickname() returns text
language sql volatile security definer set search_path = public as $$
  update public.profiles set nickname = public.random_nickname() where id = auth.uid() returning nickname
$$;

-- Weekly ranking (Monday 00:00 UTC) by MB earned. Only nicknames are exposed.
create or replace function public.leaderboard() returns jsonb
language sql stable security definer set search_path = public as $$
  with week as (select (date_trunc('week', now() at time zone 'utc')) at time zone 'utc' as start),
  totals as (
    select l.user_id, sum(l.delta_kb) as kb
      from public.ledger l, week
     where l.created_at >= week.start and l.delta_kb > 0 and l.kind in ('ad', 'bonus', 'survey')
     group by l.user_id
  ),
  ranked as (
    select t.user_id, t.kb, coalesce(p.nickname, 'Bee') as nickname, rank() over (order by t.kb desc) as rnk
      from totals t join public.profiles p on p.id = t.user_id
     where not p.flagged
  )
  select jsonb_build_object(
    'top', coalesce((
      select jsonb_agg(jsonb_build_object('rank', x.rnk, 'nickname', x.nickname, 'kb', x.kb, 'me', x.user_id = auth.uid())
                       order by x.rnk, x.kb desc)
        from (select * from ranked order by rnk limit 20) x
    ), '[]'::jsonb),
    'me', (select jsonb_build_object('rank', rnk, 'kb', kb) from ranked where user_id = auth.uid())
  )
$$;

revoke execute on function public.random_nickname(), public.new_referral_code(), public.pay_referral(uuid),
                         public.streak_bonus_pct(int), public.next_streak(public.profiles, date),
                         public.handle_new_user()
  from public, anon, authenticated;
revoke execute on function public.redeem_referral(text), public.reroll_nickname(), public.leaderboard() from public, anon;
grant  execute on function public.redeem_referral(text), public.reroll_nickname(), public.leaderboard() to authenticated;
