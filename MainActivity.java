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
        setContentView(root);
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
                JSONArray a = new JSONArray(Api.get("/rest/v1/membres?id=eq." + id + "&select=statut"));
                if (a.length() == 0) { U.setId(this, null); runOnUiThread(this::charger); return; }
                String s = a.getJSONObject(0).getString("statut");
                runOnUiThread(() -> ecran(s));
            } catch (Exception e) {
                U.toast(this, "Erreur réseau, réessaie.");
                runOnUiThread(() -> { if (rafr != null) rafr.stop(); });
            }
        });
    }

    void ecran(String s) {
        root.removeAllViews();
        if (s.equals("actif")) {
            startActivity(new Intent(this, ListeActivity.class));
            finish();
            return;
        }
        root.addView(U.header(this));
        root.addView(U.tv(this, "Bienvenue 💞", 22, true));
        rafr = U.connexion(this, "Actualiser");
        rafr.setOnClickListener(v -> { rafr.start("Actualisation"); charger(); });
        if (s.equals("demande_code") || s.equals("code_envoye")) {
            LinearLayout k = U.carte(this, root, "VÉRIFICATION");
            TextView d = U.tv(this, "Un code à 6 chiffres a été envoyé à ton adresse e-mail (regarde aussi les spams).\nCopie-le : il se remplit tout seul ici.", 14, false);
            d.setGravity(android.view.Gravity.CENTER);
            d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 14));
            k.addView(d);
            U.PBtn ok = U.valider(this, "Vérifier");
            otp = new OtpView(this, 6, () -> { if (!ok.busy) ok.performClick(); });
            k.addView(otp, new LinearLayout.LayoutParams(-1, -2));
            ok.setOnClickListener(v -> valider(otp.code().trim(), ok));
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(android.view.Gravity.CENTER);
            actions.setPadding(0, U.dp(this, 14), 0, 0);
            Button coller = U.btn(this, "📋 Coller le code");
            Button mail = U.btn(this, "📧 Ouvrir ma messagerie");
            coller.setTextSize(12);
            mail.setTextSize(12);
            coller.setOnClickListener(v -> { if (!otp.coller(this, true)) U.toast(this, "Aucun code à 6 chiffres copié. Copie-le depuis l'e-mail."); });
            mail.setOnClickListener(v -> ouvrirMessagerie());
            actions.addView(coller, new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(mail, new LinearLayout.LayoutParams(0, -2, 1));
            k.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            k.addView(CodeRecu.activation(this), new LinearLayout.LayoutParams(-1, -2));
            TextView renv = U.lien(this, "Tu n'as pas reçu le code ? <u>Renvoyer</u>");
            renv.setOnClickListener(v -> renvoyer(renv));
            k.addView(renv, new LinearLayout.LayoutParams(-1, -2));
            U.add(k, ok);
            U.add(root, U.retour(this, "← Retour (changer d'e-mail)", () -> {
                U.setId(this, null);
                startActivity(new Intent(this, InscriptionActivity.class));
                finish();
            }));
            otp.post(() -> otp.coller(this, false)); // code déjà copié depuis l'e-mail : saisie automatique
        } else if (s.equals("attente_video")) {
            root.addView(U.tv(this, "\nPour activer ton compte, enregistre une vidéo de 20 secondes (filmée en direct).", 16, false));
            U.PBtn vb = U.inscription(this, "Enregistrer ma vidéo");
            vb.setOnClickListener(v -> startActivity(new Intent(this, VideoActivity.class)));
            U.add(root, vb);
        } else if (s.equals("attente_validation")) {
            root.addView(U.tv(this, "\nTa vidéo a été envoyée. L'administrateur vérifie ton compte, reviens bientôt.", 16, false));
        } else {
            root.addView(U.tv(this, "\nTon compte a été refusé par l'administrateur.", 16, false));
        }
        U.add(root, rafr);
    }

    void renvoyer(TextView lien) {
        String id = U.id(this);
        lien.setEnabled(false);
        Api.bg(() -> {
            try {
                Api.call("POST", "/functions/v1/envoyer-code", "{\"membre_id\":\"" + id + "\"}", null);
                U.toast(this, "Code renvoyé par e-mail.");
            } catch (Exception e) {
                String msg = "Envoi impossible, réessaie.";
                try { msg = new JSONObject(e.getMessage()).optString("erreur", msg); } catch (Exception ignore) { }
                U.toast(this, msg);
            }
            runOnUiThread(() -> lien.postDelayed(() -> lien.setEnabled(true), 30000));
        });
    }

    void ouvrirMessagerie() {
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { U.toast(this, "Aucune application de messagerie trouvée."); }
    }

    void valider(String code, U.PBtn ok) {
        if (code.isEmpty()) { U.toast(this, "Entre le code reçu par e-mail."); return; }
        String id = U.id(this);
        ok.start("Vérification");
        Api.bg(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("p_id", id);
                o.put("p_code", code);
                String r = Api.call("POST", "/rest/v1/rpc/verifier_code", o.toString(), null);
                if (r.trim().equals("true")) runOnUiThread(() -> ok.succes(this::charger));
                else { U.toast(this, "Code incorrect ou expiré."); runOnUiThread(() -> { ok.stop(); if (otp != null) otp.vider(); }); }
            } catch (Exception e) { U.toast(this, "Erreur réseau."); runOnUiThread(ok::stop); }
        });
    }
}
