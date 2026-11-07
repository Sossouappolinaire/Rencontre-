package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root;
    U.PBtn rafr;

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
            root.addView(U.tv(this, "\nUn code de confirmation a été envoyé à ton adresse e-mail (regarde aussi les spams). Copie-le puis colle-le ici.", 16, false));
            EditText code = U.et(this, "Code de confirmation");
            U.add(root, code);
            U.PBtn ok = U.valider(this, "Valider le code");
            ok.setOnClickListener(v -> valider(code.getText().toString().trim(), ok));
            U.add(root, ok);
            U.PBtn re = U.pulse(this, "Renvoyer le code par e-mail");
            re.setOnClickListener(v -> renvoyer(re));
            U.add(root, re);
            U.add(root, U.retour(this, "← Retour (changer d'e-mail)", () -> {
                U.setId(this, null);
                startActivity(new Intent(this, InscriptionActivity.class));
                finish();
            }));
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

    void renvoyer(U.PBtn re) {
        String id = U.id(this);
        re.start("Envoi");
        Api.bg(() -> {
            try {
                Api.call("POST", "/functions/v1/envoyer-code", "{\"membre_id\":\"" + id + "\"}", null);
                U.toast(this, "Code renvoyé par e-mail.");
                runOnUiThread(() -> re.succes(null));
            } catch (Exception e) {
                String msg = "Envoi impossible, réessaie.";
                try { msg = new JSONObject(e.getMessage()).optString("erreur", msg); } catch (Exception ignore) { }
                U.toast(this, msg);
                runOnUiThread(re::stop);
            }
        });
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
                else { U.toast(this, "Code incorrect ou expiré."); runOnUiThread(ok::stop); }
            } catch (Exception e) { U.toast(this, "Erreur réseau."); runOnUiThread(ok::stop); }
        });
    }
}
