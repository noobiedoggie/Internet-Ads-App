-- Security advisor fixes: the signup trigger isn't an API, and pin kb_per_ad's search_path.
revoke execute on function public.handle_new_user() from public, anon, authenticated;
alter function public.kb_per_ad(public.country_config) set search_path = public;
