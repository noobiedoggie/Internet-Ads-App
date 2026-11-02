-- BitLabs survey rewards.
alter table public.ledger drop constraint ledger_kind_check;
alter table public.ledger add constraint ledger_kind_check
  check (kind in ('ad', 'redeem', 'refund', 'bonus', 'survey'));

-- Credits (or, for reversals, debits) MB from an external reward partner. Idempotent on p_ref.
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

  v_delta := greatest(p_kb, -v_p.balance_kb); -- a reversal never pushes the balance below zero
  if v_delta = 0 then return jsonb_build_object('ok', true, 'kb', 0); end if;

  insert into public.ledger (user_id, delta_kb, kind, ref, note) values (p_user, v_delta, p_kind, p_ref, p_note);
  update public.profiles set balance_kb = balance_kb + v_delta where id = p_user;
  return jsonb_build_object('ok', true, 'kb', v_delta);
end $$;

revoke execute on function public.credit_external(uuid, text, bigint, text, text) from public, anon, authenticated;
grant  execute on function public.credit_external(uuid, text, bigint, text, text) to service_role;
