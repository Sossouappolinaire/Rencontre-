-- Connexion par e-mail + mot de passe (mots de passe stockés hachés, jamais en clair)
create extension if not exists pgcrypto with schema extensions;

-- Colonne transitoire : reçoit le mot de passe à l'inscription, vidée aussitôt par le trigger
alter table membres add column if not exists mdp text;

-- Hachés dans une table sans aucune policy : inaccessible depuis l'API publique
create table if not exists membres_secret (
  membre_id uuid primary key references membres(id) on delete cascade deferrable initially deferred,
  mdp_hash text not null
);
alter table membres_secret enable row level security;

create table if not exists connexion_essais (email text not null, cree_le timestamptz default now());
alter table connexion_essais enable row level security;

create or replace function hacher_mdp_inscription() returns trigger
language plpgsql security definer set search_path = public, extensions as $$
begin
  if new.mdp is not null and length(new.mdp) >= 8 then
    insert into membres_secret(membre_id, mdp_hash) values (new.id, crypt(new.mdp, gen_salt('bf')))
    on conflict (membre_id) do update set mdp_hash = excluded.mdp_hash;
  end if;
  new.mdp := null;
  return new;
end $$;
drop trigger if exists trg_hacher_mdp on membres;
create trigger trg_hacher_mdp before insert on membres
  for each row execute function hacher_mdp_inscription();

-- Connexion : renvoie l'id du membre, ou null. 5 échecs max par e-mail sur 15 minutes.
create or replace function connexion_mdp(p_email text, p_mdp text) returns uuid
language plpgsql security definer set search_path = public, extensions as $$
declare v_id uuid; v_hash text; v_mail text := lower(trim(p_email));
begin
  delete from connexion_essais where cree_le < now() - interval '15 minutes';
  if (select count(*) from connexion_essais where email = v_mail) >= 5 then
    raise exception 'Trop de tentatives, réessaie dans 15 minutes.';
  end if;
  select m.id, s.mdp_hash into v_id, v_hash
    from membres m join membres_secret s on s.membre_id = m.id
    where lower(m.email) = v_mail;
  if v_id is not null and crypt(p_mdp, v_hash) = v_hash then
    delete from connexion_essais where email = v_mail;
    return v_id;
  end if;
  insert into connexion_essais(email) values (v_mail);
  return null;
end $$;

-- Mot de passe oublié / anciens membres sans mot de passe : on vérifie le code reçu par e-mail
-- avec ta fonction existante verifier_connexion, puis on enregistre le nouveau mot de passe.
create or replace function reinitialiser_mdp(p_email text, p_code text, p_mdp text) returns boolean
language plpgsql security definer set search_path = public, extensions as $$
declare v_id uuid;
begin
  if length(coalesce(p_mdp, '')) < 8 then return false; end if;
  v_id := verifier_connexion(p_email, p_code)::uuid;
  if v_id is null then return false; end if;
  insert into membres_secret(membre_id, mdp_hash) values (v_id, crypt(p_mdp, gen_salt('bf')))
  on conflict (membre_id) do update set mdp_hash = excluded.mdp_hash;
  return true;
end $$;
