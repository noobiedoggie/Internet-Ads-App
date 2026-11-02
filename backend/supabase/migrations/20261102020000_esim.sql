-- eSIM-only redemptions: each user gets one GigaBee eSIM; every redemption adds a data bundle to it.

create table public.esims (
  user_id      uuid primary key references public.profiles(id) on delete cascade,
  iccid        text not null unique,
  smdp_address text not null,
  matching_id  text not null,
  created_at   timestamptz not null default now()
);
alter table public.esims enable row level security;
create policy "read own esim" on public.esims for select to authenticated using (user_id = (select auth.uid()));

-- Bundles now come from the eSIM supplier. 'carrier' is shown as the region name (e.g. 'USA', 'Europe').
-- Use country_code = 'ESIM' so a bundle is offered to everyone, whatever their SIM country.
alter table public.bundles add column provider_item text;              -- exact eSIM Go bundle name
alter table public.bundles alter column operator_id drop not null;
alter table public.bundles alter column amount drop not null;
alter table public.redemptions alter column phone drop not null;
alter table public.redemptions add column iccid text;

create or replace function public.app_state(p_country text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := auth.uid();
  v_today date := (now() at time zone 'utc')::date;
  v_p     public.profiles;
  v_c     public.country_config;
  v_e     public.esims;
begin
  if v_uid is null then raise exception 'not authenticated' using errcode = '28000'; end if;

  insert into public.profiles (id) values (v_uid) on conflict do nothing;
  update public.profiles set country_code = upper(p_country)
   where id = v_uid and country_code is null and p_country ~* '^[a-z]{2}$';

  select * into v_p from public.profiles where id = v_uid;
  select * into v_c from public.country_config where country_code = v_p.country_code;
  if not found then select * into v_c from public.country_config where country_code = 'ZZ'; end if;
  select * into v_e from public.esims where user_id = v_uid;

  return jsonb_build_object(
    'balance_kb', v_p.balance_kb,
    'ads_today',  case when v_p.ads_day = v_today then v_p.ads_today else 0 end,
    'daily_cap',  v_c.daily_ad_cap,
    'kb_per_ad',  public.kb_per_ad(v_c),
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

  -- Anti-abuse: at most 6 bundles per account every 30 days.
  select count(*) into v_recent from public.redemptions
   where user_id = p_user and status <> 'failed' and created_at > now() - interval '30 days';
  if v_recent >= 6 then return jsonb_build_object('ok', false, 'reason', 'limit'); end if;

  select * into v_e from public.esims where user_id = p_user;

  insert into public.redemptions (user_id, bundle_id, iccid, data_kb, cost_usd)
  values (p_user, v_b.id, v_e.iccid, v_b.data_kb, v_b.cost_usd) returning * into v_r;
  insert into public.ledger (user_id, delta_kb, kind, ref, note)
  values (p_user, -v_b.data_kb, 'redeem', 'redeem:' || v_r.id, v_b.carrier || ' · ' || v_b.label || ' eSIM');
  update public.profiles set balance_kb = balance_kb - v_b.data_kb where id = p_user;

  return jsonb_build_object('ok', true, 'redemption_id', v_r.id, 'item', v_b.provider_item,
                            'iccid', v_e.iccid, 'label', v_b.label);
end $$;

create or replace function public.save_esim(p_user uuid, p_iccid text, p_smdp text, p_code text) returns void
language sql security definer set search_path = public as $$
  insert into public.esims (user_id, iccid, smdp_address, matching_id)
  values (p_user, p_iccid, p_smdp, p_code)
  on conflict (user_id) do nothing
$$;

revoke execute on function public.start_esim_redemption(uuid, uuid)   from public, anon, authenticated;
revoke execute on function public.save_esim(uuid, text, text, text)   from public, anon, authenticated;
grant  execute on function public.start_esim_redemption(uuid, uuid), public.save_esim(uuid, text, text, text) to service_role;
