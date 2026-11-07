package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;

public class AdminActivity extends Activity {
    LinearLayout root, zone;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = U.col(this);
        setContentView(root);
        login();
    }

    void login() {
        root.removeAllViews();
        root.addView(U.tv(this, "Administrateur", 22, true));
        EditText idf = U.et(this, "Identifiant");
        EditText mdp = U.et(this, "Mot de passe");
        mdp.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button ok = U.btn(this, "Connexion");
        ok.setOnClickListener(v -> Api.bg(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("p_identifiant", idf.getText().toString().trim());
                o.put("p_mdp", mdp.getText().toString());
                String r = Api.call("POST", "/rest/v1/rpc/admin_login", o.toString(), null);
                if (r.trim().equals("true")) runOnUiThread(this::tableau);
                else U.toast(this, "Identifiants incorrects.");
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        }));
        root.addView(idf); root.addView(mdp); root.addView(ok);
    }

    void tableau() {
        root.removeAllViews();
        root.addView(U.tv(this, "Panneau administrateur", 20, true));
        LinearLayout bar = new LinearLayout(this);
        Button b2 = U.btn(this, "Validations"), b3 = U.btn(this, "Discussions");
        for (Button x : new Button[]{b2, b3}) { x.setTextSize(12); bar.addView(x, new LinearLayout.LayoutParams(0, -2, 1)); }
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        zone = new LinearLayout(this);
        zone.setOrientation(LinearLayout.VERTICAL);
        sv.addView(zone);
        root.addView(sv);
        b2.setOnClickListener(v -> validations());
        b3.setOnClickListener(v -> discussions());
        validations();
    }

    View carte(Bitmap photo, String titre, String sous, String[] noms, Runnable[] actions) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(U.fond(0xFFF1F1F1, U.dp(this, 10)));
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
                JSONArray a = new JSONArray(Api.get("/rest/v1/membres?statut=eq.attente_validation&order=cree_le.asc&select=id,prenom,nom,pays,sexe,photo_url,video_url"));
                afficher(a, "Aucun compte à valider.", o -> {
                    String id = o.optString("id"), vid = o.optString("video_url");
                    return carte(U.b64(o.optString("photo_url")), o.optString("prenom") + " " + o.optString("nom"),
                            o.optString("pays") + " · " + o.optString("sexe"),
                            new String[]{"▶ Vidéo", "✔ Activer", "✖ Refuser"},
                            new Runnable[]{
                                    () -> startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(vid), "video/mp4")),
                                    () -> statut(id, "actif"),
                                    () -> statut(id, "refuse")});
                });
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        });
    }

    void statut(String id, String s) {
        Api.bg(() -> {
            try {
                Api.patch("/rest/v1/membres?id=eq." + id, "{\"statut\":\"" + s + "\"}");
                validations();
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        });
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
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        });
    }
}
