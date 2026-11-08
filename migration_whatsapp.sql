-- Validation du compte par l'administrateur : le code est généré quand l'admin clique « Valider »
-- puis envoyé au membre par WhatsApp. Plus d'e-mail pour l'inscription.
-- À exécuter dans Supabase > SQL Editor.
create extension if not exists pgcrypto with schema extensions;

-- Code d'activation (table sans aucune policy : inaccessible depuis l'API publique)
create table if not exists membres_code (
  membre_id uuid primary key references membres(id) on delete cascade,
  code text not null,
  expire timestamptz not null,
  essais int not null default 0
);
alter table membres_code enable row level security;

-- Comptes en attente du code (statuts existants : demande_code = pas encore validé, code_envoye = code donné)
create or replace function admin_a_valider(p_identifiant text, p_mdp text) returns json
language plpgsql security definer set search_path = public, extensions as $$
begin
  if not admin_login(p_identifiant, p_mdp) then return '[]'::json; end if;
  return coalesce((
    select json_agg(json_build_object(
      'id', m.id, 'prenom', m.prenom, 'nom', m.nom, 'pays', m.pays,
      'sexe', m.sexe, 'photo_url', m.photo_url, 'statut', m.statut) order by m.prenom)
    from membres m where m.statut in ('demande_code', 'code_envoye')), '[]'::json);
end $$;

-- Clic sur « Valider » : génère un code à 6 chiffres (valable 7 jours) et renvoie {code, whatsapp, prenom}
create or replace function admin_valider_code(p_identifiant text, p_mdp text, p_id uuid) returns json
language plpgsql security definer set search_path = public, extensions as $$
declare v_wa text; v_prenom text; v_b bytea; v_code text;
begin
  if not admin_login(p_identifiant, p_mdp) then return null; end if;
  select whatsapp, prenom into v_wa, v_prenom from membres
    where id = p_id and statut in ('demande_code', 'code_envoye');
  if not found then return null; end if;
  v_b := gen_random_bytes(4);
  v_code := lpad(((get_byte(v_b,0)::bigint * 16777216 + get_byte(v_b,1) * 65536
                   + get_byte(v_b,2) * 256 + get_byte(v_b,3)) % 1000000)::text, 6, '0');
  insert into membres_code(membre_id, code, expire) values (p_id, v_code, now() + interval '7 days')
    on conflict (membre_id) do update set code = excluded.code, expire = excluded.expire, essais = 0;
  update membres set statut = 'code_envoye' where id = p_id;
  return json_build_object('code', v_code, 'whatsapp', v_wa, 'prenom', v_prenom);
end $$;

-- Saisie du code par le membre : bon code -> compte actif (5 essais maximum par code)
create or replace function verifier_code_whatsapp(p_id uuid, p_code text) returns boolean
language plpgsql security definer set search_path = public, extensions as $$
declare c membres_code%rowtype;
begin
  select * into c from membres_code where membre_id = p_id;
  if not found or c.expire < now() or c.essais >= 5 then return false; end if;
  if c.code <> trim(p_code) then
    update membres_code set essais = essais + 1 where membre_id = p_id;
    return false;
  end if;
  delete from membres_code where membre_id = p_id;
  update membres set statut = 'actif' where id = p_id and statut in ('demande_code', 'code_envoye');
  return true;
end $$;

-- Vidéo : indépendante du code. video_ok = « vidéo déjà envoyée » (reste vrai après lecture par l'admin),
-- video_url = fichier encore sur le serveur (vidé quand l'admin l'a lue et enregistrée sur son téléphone).
alter table membres add column if not exists video_url text;
alter table membres add column if not exists video_ok boolean not null default false;
update membres set video_ok = true where video_url is not null and video_ok = false;

create or replace function soumettre_video_membre(p_id uuid, p_url text) returns boolean
language plpgsql security definer set search_path = public, extensions as $$
begin
  update membres set video_url = p_url, video_ok = true where id = p_id and coalesce(statut, '') <> 'refuse';
  return found;
end $$;

create or replace function admin_videos(p_identifiant text, p_mdp text) returns json
language plpgsql security definer set search_path = public, extensions as $$
begin
  if not admin_login(p_identifiant, p_mdp) then return '[]'::json; end if;
  return coalesce((
    select json_agg(json_build_object(
      'id', m.id, 'prenom', m.prenom, 'nom', m.nom, 'pays', m.pays, 'sexe', m.sexe, 'photo_url', m.photo_url)
      order by m.prenom)
    from membres m where m.video_url is not null), '[]'::json);
end $$;

-- Appelée après que l'admin a enregistré la vidéo sur son téléphone : le lien disparaît de la base
create or replace function admin_video_recue(p_identifiant text, p_mdp text, p_id uuid) returns boolean
language plpgsql security definer set search_path = public, extensions as $$
begin
  if not admin_login(p_identifiant, p_mdp) then return false; end if;
  update membres set video_url = null where id = p_id;
  return found;
end $$;

-- Anciens comptes bloqués à l'étape vidéo ou en attente de validation de vidéo : ils deviennent actifs
update membres set statut = 'actif' where statut in ('attente_video', 'attente_validation');

-- L'ancienne vérification par code e-mail ne doit plus permettre de s'auto-valider
do $$
declare f record;
begin
  for f in select p.oid::regprocedure as sig from pg_proc p
           join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'verifier_code' loop
    execute 'revoke execute on function ' || f.sig || ' from public, anon, authenticated';
  end loop;
end $$;

-- Envoi de la vidéo : bucket « videos » public, avec droit d'écrire et de remplacer un fichier
insert into storage.buckets (id, name, public) values ('videos', 'videos', true) on conflict do nothing;
drop policy if exists "videos upload" on storage.objects;
create policy "videos upload" on storage.objects for insert to anon with check (bucket_id = 'videos');
drop policy if exists "videos update" on storage.objects;
create policy "videos update" on storage.objects for update to anon
  using (bucket_id = 'videos') with check (bucket_id = 'videos');
drop policy if exists "videos delete" on storage.objects;
create policy "videos delete" on storage.objects for delete to anon using (bucket_id = 'videos');
