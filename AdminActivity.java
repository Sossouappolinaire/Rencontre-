package bj.rencontre;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;

public class AdminActivity extends Activity {
    LinearLayout root, zone;
    byte[] apk;
    TextView apkNom;
    String admId = "", admMdp = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        root = U.col(this);
        setContentView(root);
        String aid = getIntent().getStringExtra("aid"), amdp = getIntent().getStringExtra("amdp");
        if (aid != null && amdp != null) { admId = aid; admMdp = amdp; tableau(); }
        else login();
    }

    void login() {
        root.removeAllViews();
        root.addView(U.header(this));
        root.addView(U.tv(this, "Espace administrateur", 20, true));
        EditText idf = U.et(this, "Identifiant");
        EditText mdp = U.et(this, "Mot de passe");
        mdp.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        U.PBtn ok = U.connexion(this, "Connexion");
        ok.setOnClickListener(v -> {
            ok.start("Connexion");
            Api.bg(() -> {
                try {
                    JSONObject o = new JSONObject();
                    admId = idf.getText().toString().trim();
                    admMdp = mdp.getText().toString();
                    o.put("p_identifiant", idf.getText().toString().trim());
                    o.put("p_mdp", mdp.getText().toString());
                    String r = Api.call("POST", "/rest/v1/rpc/admin_login", o.toString(), null);
                    if (r.trim().equals("true")) runOnUiThread(() -> ok.succes(this::tableau));
                    else { U.erreur(this, "Identifiants incorrects."); runOnUiThread(ok::stop); }
                } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); runOnUiThread(ok::stop); }
            });
        });
        U.add(root, idf); U.add(root, mdp);
        U.add(root, ok);
        U.add(root, U.accueil(this));
        U.add(root, U.retour(this));
    }

    void tableau() {
        root.removeAllViews();
        root.addView(U.tv(this, "Panneau administrateur", 20, true));
        U.add(root, U.accueil(this));
        U.add(root, U.retour(this));
        LinearLayout bar = new LinearLayout(this);
        Button b2 = U.btn(this, "Validations"), b3 = U.btn(this, "Discussions"),
                b4 = U.btn(this, "En ligne"), b5 = U.btn(this, "Canal Telegram"), b6 = U.btn(this, "Membres"), b7 = U.btn(this, "Version");
        for (Button x : new Button[]{b2, b6, b4, b3, b5, b7}) { x.setTextSize(12); bar.addView(x, new LinearLayout.LayoutParams(-2, -2)); }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(bar);
        root.addView(hs);
        ScrollView sv = new ScrollView(this);
        zone = new LinearLayout(this);
        zone.setOrientation(LinearLayout.VERTICAL);
        sv.addView(zone);
        root.addView(sv);
        b2.setOnClickListener(v -> validations());
        b3.setOnClickListener(v -> discussions());
        b4.setOnClickListener(v -> enLigne());
        b5.setOnClickListener(v -> telegram());
        b6.setOnClickListener(v -> membres());
        b7.setOnClickListener(v -> version());
        validations();
    }

    View carte(Bitmap photo, String titre, String sous, String[] noms, Runnable[] actions) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(U.fond(0x22FFFFFF, U.dp(this, 14)));
        int p = U.dp(this, 10);
        c.setPadding(p, p, p, p);
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        if (photo != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(photo);
            h.addView(iv, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        }
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(p, 0, 0, 0);
        t.addView(U.tv(this, titre, 16, true));
        t.addView(U.tv(this, sous, 13, false));
        h.addView(t);
        c.addView(h);
        LinearLayout bt = new LinearLayout(this);
        for (int i = 0; i < noms.length; i++) {
            Button x = U.btn(this, noms[i]);
            x.setTextSize(12);
            Runnable a = actions[i];
            x.setOnClickListener(v -> a.run());
            bt.addView(x, new LinearLayout.LayoutParams(0, -2, 1));
        }
        c.addView(bt);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, U.dp(this, 8));
        c.setLayoutParams(lp);
        return c;
    }

    void afficher(JSONArray a, String vide, java.util.function.Function<JSONObject, View> f) {
        runOnUiThread(() -> {
            zone.removeAllViews();
            if (a.length() == 0) zone.addView(U.tv(this, "\n" + vide, 16, false));
            for (int i = 0; i < a.length(); i++) zone.addView(f.apply(a.optJSONObject(i)));
        });
    }

    void validations() {
        Api.bg(() -> {
            try {
                JSONArray codes = new JSONArray(Api.call("POST", "/rest/v1/rpc/admin_a_valider", creds().toString(), null));
                JSONArray videos = new JSONArray(Api.call("POST", "/rest/v1/rpc/admin_videos", creds().toString(), null));
                runOnUiThread(() -> {
                    zone.removeAllViews();
                    zone.addView(U.tv(this, "\nComptes à valider (code envoyé par WhatsApp) : " + codes.length(), 16, true));
                    if (codes.length() == 0) zone.addView(U.tv(this, "Aucun compte en attente.", 14, false));
                    for (int i = 0; i < codes.length(); i++) {
                        JSONObject o = codes.optJSONObject(i);
                        String id = o.optString("id"), nom = o.optString("prenom") + " " + o.optString("nom");
                        boolean envoye = o.optString("statut").equals("code_envoye");
                        zone.addView(carte(U.b64(o.optString("photo_url")), nom,
                                o.optString("pays") + " · " + o.optString("sexe")
                                        + (envoye ? "\n💬 code envoyé, en attente de saisie" : "\n⏳ à valider"),
                                new String[]{envoye ? "↻ Renvoyer le code" : "✔ Valider", "✖ Refuser"},
                                new Runnable[]{() -> envoyerCode(id), () -> refuser(id, nom)}));
                    }
                    zone.addView(U.tv(this, "\nVidéos reçues : " + videos.length(), 16, true));
                    zone.addView(U.tv(this, "Quand tu ouvres une vidéo, elle est enregistrée sur ton téléphone puis supprimée automatiquement du serveur.", 12, false));
                    if (videos.length() == 0) zone.addView(U.tv(this, "Aucune vidéo en attente.", 14, false));
                    for (int i = 0; i < videos.length(); i++) {
                        JSONObject o = videos.optJSONObject(i);
                        String id = o.optString("id"), nom = o.optString("prenom") + " " + o.optString("nom");
                        zone.addView(carte(U.b64(o.optString("photo_url")), nom,
                                o.optString("pays") + " · " + o.optString("sexe"),
                                new String[]{"▶ Voir la vidéo"},
                                new Runnable[]{() -> lireVideo(id, nom)}));
                    }
                });
            } catch (Exception e) { U.erreur(this, "Erreur : " + U.msg(e, "réseau.")); }
        });
    }

    /**
     * Télécharge la vidéo, l'enregistre sur le téléphone de l'administrateur, puis la supprime du serveur
     * (fichier + lien en base) et la lit. Si l'enregistrement sur le téléphone échoue, rien n'est supprimé.
     */
    void lireVideo(String id, String nom) {
        U.toast(this, "Téléchargement de la vidéo…");
        Api.bg(() -> {
            Uri local;
            try {
                String fichier = nom.replaceAll("[^A-Za-z0-9]+", "_") + "_" + System.currentTimeMillis() + ".mp4";
                local = sauvegarder(Config.URL + "/storage/v1/object/public/videos/" + id + ".mp4", fichier);
            } catch (Exception e) {
                U.erreur(this, "La vidéo n'a pas pu être enregistrée sur ton téléphone (elle reste sur le serveur) :\n" + U.msg(e, "erreur inconnue."));
                return;
            }
            String avert = null;
            try {
                Api.call("DELETE", "/storage/v1/object/videos/" + id + ".mp4", null, null);
                Api.call("POST", "/rest/v1/rpc/admin_video_recue", creds().put("p_id", id).toString(), null);
            } catch (Exception e) { avert = U.msg(e, "erreur inconnue."); }
            final String av = avert;
            validations();
            runOnUiThread(() -> startActivity(new Intent(this, LecteurActivity.class)
                    .putExtra("uri", local.toString()).putExtra("titre", nom)));
            if (av != null) U.erreur(this, "La vidéo est enregistrée sur ton téléphone, mais n'a pas pu être supprimée du serveur :\n" + av);
        });
    }

    /** Android 10+ : dossier Films/LylyDating (visible dans la galerie). Android 7 à 9 : dossier Films de l'application. */
    Uri sauvegarder(String url, String nom) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, nom);
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LylyDating");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri u = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new IOException("Stockage du téléphone indisponible.");
            try (OutputStream o = getContentResolver().openOutputStream(u)) {
                Api.download(url, o);
            } catch (Exception e) {
                getContentResolver().delete(u, null, null);
                throw e;
            }
            ContentValues fin = new ContentValues();
            fin.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(u, fin, null, null);
            return u;
        }
        File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (dir == null) throw new IOException("Stockage du téléphone indisponible.");
        dir.mkdirs();
        File f = new File(dir, nom);
        try (OutputStream o = new FileOutputStream(f)) {
            Api.download(url, o);
        } catch (Exception e) {
            f.delete();
            throw e;
        }
        return Uri.fromFile(f);
    }

    /** Génère le code côté serveur puis ouvre WhatsApp sur le numéro du membre avec le message prêt à envoyer. */
    void envoyerCode(String id) {
        Api.bg(() -> {
            try {
                String r = Api.call("POST", "/rest/v1/rpc/admin_valider_code", creds().put("p_id", id).toString(), null).trim();
                if (r.isEmpty() || r.equals("null")) {
                    U.erreur(this, "Action refusée : compte déjà traité ou accès administrateur refusé.");
                    validations();
                    return;
                }
                JSONObject o = new JSONObject(r);
                String code = o.optString("code"), prenom = o.optString("prenom");
                String num = o.optString("whatsapp").replaceAll("[^0-9]", "");
                validations();
                if (num.isEmpty()) {
                    U.erreur(this, "Numéro WhatsApp introuvable pour ce membre. Code à lui transmettre : " + code);
                    return;
                }
                String msg = "Bonjour " + prenom + " 💞, ton compte Lyly Dating est validé ✅.\n"
                        + "Ton code d'activation : " + code + "\n"
                        + "Ouvre l'application et saisis ce code pour que ton compte soit opérationnel.";
                Uri u = Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(msg));
                runOnUiThread(() -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                    catch (Exception e) { U.erreur(this, "WhatsApp indisponible sur ce téléphone. Code à transmettre à " + prenom + " : " + code); }
                });
            } catch (Exception e) { U.erreur(this, "Erreur : " + U.msg(e, "réseau.")); }
        });
    }

    void refuser(String id, String nom) {
        runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                .setTitle("Refuser ce compte ?")
                .setMessage(nom + " ne pourra plus utiliser l'application.")
                .setPositiveButton("Refuser", (d, w) -> Api.bg(() -> {
                    try {
                        if (actionMembre("admin_rejeter", id)) U.toast(this, "Compte refusé.");
                        else U.erreur(this, "Action impossible.");
                    } catch (Exception e) { U.erreur(this, "Erreur : " + U.msg(e, "réseau.")); }
                    validations();
                }))
                .setNegativeButton("Annuler", null).show());
    }

    void statut(String id, String s) {
        Api.bg(() -> {
            try {
                String r = Api.call("POST", "/rest/v1/rpc/admin_statut", creds().put("p_id", id).put("p_statut", s).toString(), null);
                if (!r.trim().equals("true")) { U.erreur(this, "Action refusée (compte déjà traité ?)."); validations(); return; }
                validations();
                if (s.equals("actif")) whatsappMembre(id, "Bonjour %s 💞, ton compte Lyly Dating est confirmé et activé ✅. Tu peux maintenant découvrir les profils.");
            } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); }
        });
    }

    /** Ouvre WhatsApp sur la conversation du membre avec un message prérempli (%s = prénom). */
    void whatsappMembre(String id, String modele) {
        try {
            JSONObject r = new JSONObject(Api.call("POST", "/rest/v1/rpc/admin_whatsapp", creds().put("p_id", id).toString(), null));
            String num = r.optString("whatsapp").replaceAll("[^0-9]", "");
            if (num.isEmpty()) { U.erreur(this, "Numéro WhatsApp introuvable."); return; }
            String msg = String.format(modele, r.optString("prenom"));
            Uri u = Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(msg));
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                catch (Exception e) { U.erreur(this, "WhatsApp indisponible."); }
            });
        } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); }
    }

    String libelleStatut(String s) {
        switch (s) {
            case "demande_code": return "⏳ à valider (code WhatsApp)";
            case "code_envoye": return "💬 code envoyé, en attente de saisie";
            case "attente_video": return "🎥 vidéo à envoyer";
            case "attente_validation": return "⏳ à valider";
            case "actif": return "✅ actif";
            case "refuse": return "🚫 rejeté";
            default: return s;
        }
    }

    /** Action admin sur un membre via une fonction réservée à l'administrateur ; true si acceptée. */
    boolean actionMembre(String fonction, String id) throws Exception {
        return Api.call("POST", "/rest/v1/rpc/" + fonction, creds().put("p_id", id).toString(), null).trim().equals("true");
    }

    void discussions() {
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(Api.get("/rest/v1/conversations?order=cree_le.desc&select=id,cree_le,statut,accord_a,accord_b,a:membres!membre_a(prenom),b:membres!membre_b(prenom)"));
                afficher(a, "Aucune discussion.", o -> {
                    String nA = o.optJSONObject("a").optString("prenom"), nB = o.optJSONObject("b").optString("prenom");
                    String st = o.optString("statut").equals("accord") ? "✅ D'accord : couple formé"
                            : o.optString("statut").equals("rejetee") ? "❌ Rejetée" : "💬 En cours";
                    String cid = o.optString("id");
                    return carte(null, nA + " ↔ " + nB, "Créée le " + U.heure(o.optString("cree_le")) + "\n" + st,
                            new String[]{"Ouvrir la discussion"},
                            new Runnable[]{() -> startActivity(new Intent(this, ChatActivity.class)
                                    .putExtra("cid", cid).putExtra("titre", nA + " ↔ " + nB).putExtra("ro", true))});
                });
            } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); }
        });
    }

    JSONObject creds() throws JSONException {
        return new JSONObject().put("p_identifiant", admId).put("p_mdp", admMdp);
    }

    // ---------- Membres en ligne (tous pays) ----------
    void enLigne() {
        Api.bg(() -> {
            try {
                JSONObject r = new JSONObject(Api.call("POST", "/rest/v1/rpc/admin_en_ligne", creds().toString(), null));
                JSONArray a = r.optJSONArray("en_ligne");
                if (a == null) a = new JSONArray();
                final JSONArray L = a;
                final int total = r.optInt("total");
                runOnUiThread(() -> {
                    zone.removeAllViews();
                    zone.addView(U.tv(this, "\n🟢 " + L.length() + " en ligne sur " + total + " membre(s) actif(s)", 16, true));
                    if (L.length() == 0) zone.addView(U.tv(this, "\nPersonne en ligne pour le moment.", 14, false));
                    for (int i = 0; i < L.length(); i++) {
                        JSONObject o = L.optJSONObject(i);
                        String drap = o.optString("pays_code").length() == 2 ? U.drapeau(o.optString("pays_code")) + " " : "";
                        zone.addView(U.tv(this, "\n● " + o.optString("prenom") + " " + o.optString("nom") + "  —  " + drap + o.optString("pays"), 15, false));
                    }
                    U.PBtn rf = U.pulse(this, "Actualiser");
                    rf.setOnClickListener(v -> enLigne());
                    U.add(zone, rf);
                });
            } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); }
        });
    }

    // ---------- Membres inscrits ----------
    void membres() {
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(Api.call("POST", "/rest/v1/rpc/admin_membres", creds().toString(), null));
                int on = 0;
                for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).optBoolean("en_ligne")) on++;
                final int n = on;
                runOnUiThread(() -> {
                    zone.removeAllViews();
                    zone.addView(U.tv(this, "\n👥 " + a.length() + " membre(s) inscrit(s)  ·  🟢 " + n + " en ligne\n", 16, true));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        String id = o.optString("id"), st = o.optString("statut"), nom = o.optString("prenom") + " " + o.optString("nom");
                        String drap = o.optString("pays_code").length() == 2 ? U.drapeau(o.optString("pays_code")) + " " : "";
                        java.util.ArrayList<String> noms = new java.util.ArrayList<>();
                        java.util.ArrayList<Runnable> acts = new java.util.ArrayList<>();
                        if (st.equals("demande_code") || st.equals("code_envoye")) {
                            noms.add(st.equals("code_envoye") ? "↻ Code" : "✔ Valider");
                            acts.add(() -> envoyerCode(id));
                        }
                        noms.add("💬");
                        acts.add(() -> Api.bg(() -> whatsappMembre(id, "Bonjour %s 👋, c'est l'équipe Lyly Dating.")));
                        if (!st.equals("refuse")) {
                            noms.add("🚫 Rejeter");
                            acts.add(() -> new android.app.AlertDialog.Builder(this)
                                    .setTitle("Rejeter ce compte ?")
                                    .setMessage(nom + " ne pourra plus utiliser l'application.")
                                    .setPositiveButton("Rejeter", (d, w) -> Api.bg(() -> traiter("admin_rejeter", id, "Compte rejeté.")))
                                    .setNegativeButton("Annuler", null).show());
                        }
                        noms.add("🗑");
                        acts.add(() -> new android.app.AlertDialog.Builder(this)
                                .setTitle("Supprimer définitivement ?")
                                .setMessage(nom + " sera supprimé(e) avec ses discussions. Cette action est irréversible.")
                                .setPositiveButton("Supprimer", (d, w) -> Api.bg(() -> traiter("admin_supprimer", id, "Compte supprimé.")))
                                .setNegativeButton("Annuler", null).show());
                        zone.addView(carte(null, (o.optBoolean("en_ligne") ? "🟢 " : "⚪ ") + nom,
                                drap + o.optString("pays") + " · " + o.optString("sexe") + "\n" + libelleStatut(st),
                                noms.toArray(new String[0]), acts.toArray(new Runnable[0])));
                    }
                });
            } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); }
        });
    }

    void traiter(String fonction, String id, String ok) {
        try {
            if (actionMembre(fonction, id)) U.toast(this, ok); else U.erreur(this, "Action impossible.");
        } catch (Exception e) { U.erreur(this, "Erreur réseau. Vérifie ta connexion puis réessaie."); }
        membres();
    }

    // ---------- Canal Telegram ----------
    JSONObject creds2() throws JSONException { return new JSONObject().put("identifiant", admId).put("mdp", admMdp); }

    String tg(String action, String chat) throws Exception {
        JSONObject o = creds2().put("action", action);
        if (chat != null) o.put("chat_id", chat);
        return Api.call("POST", "/functions/v1/telegram-canaux", o.toString(), null);
    }

    String tgToken(String token) throws Exception {
        return Api.call("POST", "/functions/v1/telegram-canaux", creds2().put("action", "token").put("token", token).toString(), null);
    }

    String err(Exception e, String def) {
        try { return new JSONObject(e.getMessage()).optString("erreur", def); } catch (Exception x) { return def; }
    }

    void telegram() {
        runOnUiThread(() -> {
            zone.removeAllViews();
            zone.addView(U.tv(this, "\nBot Telegram", 16, true));
            EditText tok = U.et(this, "API token du bot (vide = inchangé)");
            U.add(zone, tok);
            U.PBtn sv = U.valider(this, "Enregistrer le bot (active la lecture)");
            U.add(zone, sv);
            zone.addView(U.tv(this, "\nCanaux et groupes suivis", 16, true));
            zone.addView(U.tv(this, "Ajoute le bot comme administrateur du canal (ou membre du groupe), puis entre ici l'ID ou le @nom.", 12, false));
            EditText can = U.et(this, "ID ou @nom du canal / groupe");
            U.add(zone, can);
            U.PBtn aj = U.connexion(this, "Ajouter");
            U.add(zone, aj);
            LinearLayout liste = new LinearLayout(this);
            liste.setOrientation(LinearLayout.VERTICAL);
            zone.addView(liste);
            Api.bg(() -> {
                try {
                    JSONObject r = new JSONObject(tg("etat", null));
                    if (r.optBoolean("token_defini")) runOnUiThread(() -> tok.setHint("Bot " + r.optString("bot_nom") + " enregistré (vide = inchangé)"));
                } catch (Exception ignore) { }
            });
            chats(liste);
            sv.setOnClickListener(v -> {
                sv.start("Activation");
                Api.bg(() -> {
                    try {
                        String t = tok.getText().toString().trim();
                        if (t.isEmpty()) { U.erreur(this, "Colle le token du bot."); runOnUiThread(sv::stop); return; }
                        tgToken(t);
                        U.toast(this, "Bot enregistré, lecture des messages activée.");
                        runOnUiThread(() -> { tok.setText(""); sv.succes(null); });
                    } catch (Exception e) { U.erreur(this, err(e, "Erreur réseau.")); runOnUiThread(sv::stop); }
                });
            });
            aj.setOnClickListener(v -> {
                String c = can.getText().toString().trim();
                if (c.isEmpty()) return;
                aj.start("Ajout");
                Api.bg(() -> {
                    try {
                        tg("ajouter", c);
                        runOnUiThread(() -> { can.setText(""); aj.succes(null); });
                        chats(liste);
                    } catch (Exception e) { U.erreur(this, err(e, "Erreur réseau.")); runOnUiThread(aj::stop); }
                });
            });
        });
    }

    void chats(LinearLayout liste) {
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(tg("liste", null));
                runOnUiThread(() -> {
                    liste.removeAllViews();
                    if (a.length() == 0) liste.addView(U.tv(this, "\nAucun canal ni groupe ajouté.", 14, false));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        String id = o.optString("chat_id"), titre = o.optString("titre");
                        String type = o.optString("type").equals("channel") ? "Canal" : "Groupe";
                        View c = carte(null, titre, type + " · " + o.optInt("nb") + " message(s) reçus",
                                new String[]{"💬 Messages", "Retirer"},
                                new Runnable[]{() -> messagesChat(id, titre), () -> retirer(id, liste)});
                        liste.addView(c);
                    }
                });
            } catch (Exception e) { U.erreur(this, err(e, "Erreur réseau.")); }
        });
    }

    void retirer(String id, LinearLayout liste) {
        Api.bg(() -> {
            try { tg("supprimer", id); chats(liste); }
            catch (Exception e) { U.erreur(this, err(e, "Erreur réseau.")); }
        });
    }

    void messagesChat(String id, String titre) {
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(tg("messages", id));
                runOnUiThread(() -> {
                    zone.removeAllViews();
                    zone.addView(U.tv(this, "\n💬 " + titre, 16, true));
                    U.PBtn rf = U.pulse(this, "Actualiser");
                    rf.setOnClickListener(v -> messagesChat(id, titre));
                    U.add(zone, rf);
                    U.add(zone, U.retour(this, "← Canaux et groupes", this::telegram));
                    if (a.length() == 0) zone.addView(U.tv(this, "\nAucun message reçu pour le moment.", 14, false));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        zone.addView(U.tv(this, "\n" + o.optString("auteur") + "  ·  " + U.heure(o.optString("date")), 12, true));
                        zone.addView(U.tv(this, o.optString("texte"), 14, false));
                    }
                });
            } catch (Exception e) { U.erreur(this, err(e, "Erreur réseau.")); }
        });
    }

    // ---------- Publier une nouvelle version téléchargeable ----------
    void version() {
        apk = null;
        runOnUiThread(() -> {
            zone.removeAllViews();
            zone.addView(U.tv(this, "\nNouvelle version de l'application", 16, true));
            zone.addView(U.tv(this, "Le numéro doit être supérieur à celui de la version actuelle (versionCode dans app_build.gradle). Les membres la téléchargent depuis ⚙ Paramètres.", 12, false));
            EditText code = U.et(this, "Numéro de version (entier, ex : 2)");
            code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            EditText nom = U.et(this, "Nom de version (ex : 1.1)");
            EditText notes = U.et(this, "Nouveautés (facultatif)");
            notes.setMinLines(3);
            notes.setGravity(Gravity.TOP);
            U.add(zone, code); U.add(zone, nom); U.add(zone, notes);
            U.PBtn choix = U.btn(this, "📦 Choisir le fichier .apk");
            choix.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                startActivityForResult(i, 7);
            });
            U.add(zone, choix);
            apkNom = U.tv(this, "Aucun fichier choisi.", 13, false);
            zone.addView(apkNom);
            U.PBtn pub = U.inscription(this, "Publier la version");
            pub.setOnClickListener(v -> publier(pub, code, nom, notes));
            U.add(zone, pub);
        });
    }

    void publier(U.PBtn pub, EditText code, EditText nom, EditText notes) {
        int c;
        try { c = Integer.parseInt(code.getText().toString().trim()); } catch (Exception e) { c = 0; }
        final int vc = c;
        final String vn = nom.getText().toString().trim();
        if (vc <= 0 || vn.isEmpty() || apk == null) { U.erreur(this, "Numéro, nom et fichier .apk sont obligatoires."); return; }
        final byte[] data = apk;
        pub.start("Envoi");
        Api.bg(() -> {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                StringBuilder sb = new StringBuilder();
                for (byte x : md.digest(data)) sb.append(String.format("%02x", x));
                String fichier = "lyly-" + vc + "-" + System.currentTimeMillis() + ".apk";
                Api.upload("apk", fichier, data, "application/vnd.android.package-archive", false,
                        f -> runOnUiThread(() -> pub.setProgress(f)));
                JSONObject o = creds().put("p_code", vc).put("p_nom", vn)
                        .put("p_notes", notes.getText().toString().trim())
                        .put("p_url", Config.URL + "/storage/v1/object/public/apk/" + fichier)
                        .put("p_sha", sb.toString()).put("p_taille", data.length);
                String r = Api.call("POST", "/rest/v1/rpc/admin_publier_version", o.toString(), null);
                if (!r.trim().equals("true")) throw new java.io.IOException("{\"erreur\":\"Refusé (identifiants).\"}");
                U.toast(this, "Version " + vn + " publiée.");
                runOnUiThread(() -> pub.succes(this::version));
            } catch (Exception e) {
                U.erreur(this, err(e, "Échec : numéro déjà utilisé ou envoi impossible."));
                runOnUiThread(pub::stop);
            }
        });
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == 7 && rs == RESULT_OK && d != null && d.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(d.getData());
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                apk = out.toByteArray();
                if (apkNom != null) apkNom.setText("✔ " + (apk.length / 1048576.0) + " Mo prêts à publier");
            } catch (Exception e) { U.erreur(this, "Fichier illisible."); }
        }
    }
}
