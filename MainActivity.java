package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root;
    U.PBtn rafr;
    OtpView otp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        root = U.col(this);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }
    @Override protected void onResume() { super.onResume(); charger(); }

    void charger() {
        String id = U.id(this);
        if (id == null) {
            startActivity(new Intent(this, InscriptionActivity.class));
            finish();
            return;
        }
        if (LockActivity.pinDefini(this) && !LockActivity.deverrouille) {
            startActivity(new Intent(this, LockActivity.class));
            finish();
            return;
        }
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(Api.get("/rest/v1/membres?id=eq." + id + "&select=statut,video_ok"));
                if (a.length() == 0) { U.setId(this, null); runOnUiThread(this::charger); return; }
                String s = a.getJSONObject(0).getString("statut");
                boolean vo = a.getJSONObject(0).optBoolean("video_ok");
                runOnUiThread(() -> ecran(s, vo));
            } catch (Exception e) {
                U.erreur(this, "Erreur réseau : " + U.msg(e, "vérifie ta connexion puis réessaie."));
                runOnUiThread(() -> { if (rafr != null) rafr.stop(); });
            }
        });
    }

    void ecran(String s, boolean videoOk) {
        root.removeAllViews();
        // Compte opérationnel (la vidéo se fait ensuite, depuis la liste ou les paramètres)
        if (s.equals("actif") || s.equals("attente_video") || s.equals("attente_validation")) {
            startActivity(new Intent(this, ListeActivity.class));
            finish();
            return;
        }
        root.addView(U.header(this));
        root.addView(U.tv(this, "Bienvenue 💞", 22, true));
        rafr = U.connexion(this, "Actualiser");
        rafr.setOnClickListener(v -> { rafr.start("Actualisation"); charger(); });
        if (s.equals("demande_code") || s.equals("code_envoye")) {
            // Compte créé et ouvert : l'administrateur valide puis envoie le code par WhatsApp
            LinearLayout k = U.carte(this, root, "ACTIVATION");
            TextView d = U.tv(this, "Ton compte est créé ✅\nL'administrateur va le valider puis t'envoyer un code à 6 chiffres sur ton WhatsApp.\nSaisis ce code ici pour que ton compte soit opérationnel.", 14, false);
            d.setGravity(android.view.Gravity.CENTER);
            d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 14));
            k.addView(d);
            U.PBtn ok = U.valider(this, "Valider le code");
            otp = new OtpView(this, 6, () -> { if (!ok.busy) ok.performClick(); });
            k.addView(otp, new LinearLayout.LayoutParams(-1, -2));
            ok.setOnClickListener(v -> valider(otp.code().trim(), ok));
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(android.view.Gravity.CENTER);
            actions.setPadding(0, U.dp(this, 14), 0, 0);
            Button coller = U.btn(this, "📋 Coller le code");
            coller.setTextSize(12);
            coller.setOnClickListener(v -> { if (!otp.coller(this, true)) U.erreur(this, "Aucun code à 6 chiffres copié. Copie le code depuis le message WhatsApp de l'administrateur."); });
            actions.addView(coller, new LinearLayout.LayoutParams(0, -2, 1));
            k.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            U.add(k, ok);
            // La vidéo ne dépend pas du code : on peut la faire dès maintenant
            root.addView(U.tv(this, "\nTu peux enregistrer ta vidéo dès maintenant, sans attendre le code." + (videoOk ? "\n✔ Ta vidéo est déjà envoyée." : ""), 14, false));
            U.PBtn vb = U.inscription(this, videoOk ? "🎥 Refaire ma vidéo" : "🎥 Enregistrer ma vidéo");
            vb.setOnClickListener(v -> startActivity(new Intent(this, VideoActivity.class)));
            U.add(root, vb);
            U.add(root, U.retour(this, "← Retour (changer de compte)", () -> {
                U.setId(this, null);
                startActivity(new Intent(this, InscriptionActivity.class));
                finish();
            }));
            otp.post(() -> otp.coller(this, false)); // code déjà copié depuis WhatsApp : saisie automatique
        } else {
            root.addView(U.tv(this, "\nTon compte a été refusé par l'administrateur.", 16, false));
        }
        U.add(root, rafr);
        LinearLayout bar = new LinearLayout(this);
        U.PBtn prm = U.btn(this, "⚙ Paramètres"), out = U.btn(this, "Déconnexion");
        prm.setOnClickListener(v -> startActivity(new Intent(this, ParametresActivity.class)));
        out.setOnClickListener(v -> U.deconnecter(this));
        bar.addView(prm, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(out, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    void valider(String code, U.PBtn ok) {
        if (code.length() != 6) { U.erreur(this, "Entre le code à 6 chiffres reçu sur WhatsApp."); return; }
        String id = U.id(this);
        ok.start("Vérification");
        Api.bg(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("p_id", id);
                o.put("p_code", code);
                String r = Api.call("POST", "/rest/v1/rpc/verifier_code_whatsapp", o.toString(), null);
                if (r.trim().equals("true")) runOnUiThread(() -> ok.succes(this::charger));
                else {
                    U.erreur(this, "Code incorrect, expiré ou bloqué après 5 essais. Demande un nouveau code à l'administrateur.");
                    runOnUiThread(() -> { ok.stop(); if (otp != null) otp.vider(); });
                }
            } catch (Exception e) { U.erreur(this, "Erreur : " + U.msg(e, "réseau.")); runOnUiThread(ok::stop); }
        });
    }
}
