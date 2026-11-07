-- 1) Nouveaux champs d'inscription
alter table membres add column if not exists age int;
alter table membres add column if not exists taille_cm int;
alter table membres add column if not exists profession text;
alter table membres add column if not exists description text;

-- 2) Versions téléchargeables de l'application
create table if not exists app_versions (
  id uuid primary key default gen_random_uuid(),
  version_code int not null unique,
  version_name text not null,
  notes text,
  apk_url text not null,
  sha256 text not null,
  taille_octets bigint,
  cree_le timestamptz default now()
);
alter table app_versions enable row level security;
drop policy if exists "lecture versions" on app_versions;
create policy "lecture versions" on app_versions for select using (true);
-- Aucune policy d'écriture : seul l'admin publie, via la fonction ci-dessous.

create or replace function admin_publier_version(
  p_identifiant text, p_mdp text, p_code int, p_nom text,
  p_notes text, p_url text, p_sha text, p_taille bigint
) returns boolean language plpgsql security definer as $$
begin
  if not admin_login(p_identifiant, p_mdp) then return false; end if;
  insert into app_versions(version_code, version_name, notes, apk_url, sha256, taille_octets)
  values (p_code, p_nom, p_notes, p_url, lower(p_sha), p_taille);
  return true;
end $$;

-- 3) Bucket public « apk » (Storage > New bucket > nom : apk > Public)
insert into storage.buckets (id, name, public) values ('apk', 'apk', true) on conflict do nothing;
drop policy if exists "apk upload" on storage.objects;
create policy "apk upload" on storage.objects for insert to anon with check (bucket_id = 'apk');
