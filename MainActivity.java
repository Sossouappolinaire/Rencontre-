package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(Api.get("/rest/v1/membres?id=eq." + id + "&select=statut"));
                if (a.length() == 0) { U.setId(this, null); runOnUiThread(this::charger); return; }
                String s = a.getJSONObject(0).getString("statut");
                runOnUiThread(() -> ecran(s));
            } catch (Exception e) { U.toast(this, "Erreur réseau, réessaie."); }
        });
    }

    void ecran(String s) {
        root.removeAllViews();
        if (s.equals("actif")) {
            startActivity(new Intent(this, ListeActivity.class));
            finish();
            return;
        }
        root.addView(U.tv(this, "Bienvenue 💞", 24, true));
        Button rafr = U.btn(this, "Actualiser");
        rafr.setOnClickListener(v -> charger());
        if (s.equals("demande_code") || s.equals("code_envoye")) {
            root.addView(U.tv(this, "\nUn code de confirmation a été envoyé à ton adresse e-mail (regarde aussi les spams). Copie-le puis colle-le ici.", 16, false));
            EditText code = U.et(this, "Code de confirmation");
            root.addView(code);
            Button ok = U.btn(this, "Valider le code");
            ok.setOnClickListener(v -> valider(code.getText().toString().trim()));
            root.addView(ok);
            Button re = U.btn(this, "Renvoyer le code par e-mail");
            re.setOnClickListener(v -> renvoyer());
            root.addView(re);
        } else if (s.equals("attente_video")) {
            root.addView(U.tv(this, "\nPour activer ton compte, enregistre une vidéo de 20 secondes (filmée en direct).", 16, false));
            Button vb = U.btn(this, "Enregistrer ma vidéo");
            vb.setOnClickListener(v -> startActivity(new Intent(this, VideoActivity.class)));
            root.addView(vb);
        } else if (s.equals("attente_validation")) {
            root.addView(U.tv(this, "\nTa vidéo a été envoyée. L'administrateur vérifie ton compte, reviens bientôt.", 16, false));
        } else {
            root.addView(U.tv(this, "\nTon compte a été refusé par l'administrateur.", 16, false));
        }
        root.addView(rafr);
    }

    void renvoyer() {
        String id = U.id(this);
        Api.bg(() -> {
            try {
                Api.call("POST", "/functions/v1/envoyer-code", "{\"membre_id\":\"" + id + "\"}", null);
                U.toast(this, "Code renvoyé par e-mail.");
            } catch (Exception e) { U.toast(this, "Attends une minute avant de redemander un code."); }
        });
    }

    void valider(String code) {
        String id = U.id(this);
        Api.bg(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("p_id", id);
                o.put("p_code", code);
                String r = Api.call("POST", "/rest/v1/rpc/verifier_code", o.toString(), null);
                if (r.trim().equals("true")) runOnUiThread(this::charger);
                else U.toast(this, "Code incorrect ou expiré.");
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        });
    }
}
