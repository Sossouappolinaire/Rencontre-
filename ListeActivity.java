package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.*;

public class ListeActivity extends Activity {
    LinearLayout list;
    TextView couples;
    String me;
    JSONObject moi;
    boolean disc = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        me = U.id(this);
        LinearLayout r = U.col(this);
        couples = U.tv(this, "💑", 16, true);
        couples.setGravity(Gravity.CENTER);
        r.addView(couples);
        LinearLayout bar = new LinearLayout(this);
        Button b1 = U.btn(this, "Membres"), b2 = U.btn(this, "Discussions");
        bar.addView(b1, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(b2, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(bar);
        LinearLayout bar2 = new LinearLayout(this);
        Button sec = U.btn(this, "🔒 Sécurité"), prm = U.btn(this, "⚙ Paramètres"), out = U.btn(this, "Déconnexion");
        sec.setTextSize(12); prm.setTextSize(12); out.setTextSize(12);
        bar2.addView(sec, new LinearLayout.LayoutParams(0, -2, 1));
        bar2.addView(prm, new LinearLayout.LayoutParams(0, -2, 1));
        bar2.addView(out, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(bar2);
        sec.setOnClickListener(v -> startActivity(new Intent(this, LockActivity.class).putExtra("mode", "creer")));
        prm.setOnClickListener(v -> startActivity(new Intent(this, ParametresActivity.class)));
        out.setOnClickListener(v -> U.deconnecter(this));
        b1.setOnClickListener(v -> { disc = false; charger(); });
        b2.setOnClickListener(v -> { disc = true; charger(); });
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        r.addView(sv);
        setContentView(r);
    }
    @Override protected void onResume() {
        super.onResume();
        Presence.start(me);
        charger();
        if (!LockActivity.pinDefini(this) && !getSharedPreferences("lock", 0).getBoolean("refuse", false)) {
            getSharedPreferences("lock", 0).edit().putBoolean("refuse", true).apply();
            startActivity(new Intent(this, LockActivity.class).putExtra("mode", "creer"));
        }
    }
    @Override protected void onPause() { super.onPause(); Presence.leave(); }

    void charger() {
        list.removeAllViews();
        Api.bg(() -> {
            try {
                if (moi == null)
                    moi = new JSONArray(Api.get("/rest/v1/membres?id=eq." + me + "&select=pays,pays_code,sexe,cherche")).getJSONObject(0);
                int n = new JSONArray(Api.get("/rest/v1/couples_public?select=id")).length();
                String t = "💑 " + n + " couple(s) formé(s)   " + U.drapeau(moi.getString("pays_code")) + " " + moi.getString("pays");
                runOnUiThread(() -> couples.setText(t));
                if (disc) discussions(); else membres();
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        });
    }

    void membres() throws Exception {
        java.util.HashSet<String> enLigne = new java.util.HashSet<>();
        try {
            JSONArray on = new JSONArray(Api.get("/rest/v1/presence_publique?pays_code=eq." + moi.getString("pays_code") + "&en_ligne=eq.true&select=id"));
            for (int i = 0; i < on.length(); i++) enLigne.add(on.getJSONObject(i).optString("id"));
        } catch (Exception ignore) { }
        JSONArray a = new JSONArray(Api.get("/rest/v1/membres?pays_code=eq." + moi.getString("pays_code")
                + "&sexe=eq." + moi.getString("cherche") + "&statut=eq.actif&en_couple=eq.false&id=neq." + me
                + "&select=id,prenom,photo_url,age,taille_cm,profession,description&order=cree_le.desc"));
        runOnUiThread(() -> {
            list.removeAllViews();
            if (a.length() == 0) list.addView(U.tv(this, "\nPersonne pour le moment dans ton pays.", 16, false));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                list.addView(ligne(o.optString("prenom"), o.optString("photo_url"), enLigne.contains(o.optString("id")), apercu(o), () -> ouvrir(o.optString("id"), o.optString("prenom"))));
            }
        });
    }

    void discussions() throws Exception {
        JSONArray a = new JSONArray(Api.get("/rest/v1/conversations?or=(membre_a.eq." + me + ",membre_b.eq." + me
                + ")&statut=eq.ouverte&order=cree_le.desc&select=id,membre_a,a:membres!membre_a(prenom,photo_url),b:membres!membre_b(prenom,photo_url)"));
        runOnUiThread(() -> {
            list.removeAllViews();
            if (a.length() == 0) list.addView(U.tv(this, "\nAucune discussion ouverte.", 16, false));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                JSONObject autre = o.optString("membre_a").equals(me) ? o.optJSONObject("b") : o.optJSONObject("a");
                String cid = o.optString("id"), nom = autre.optString("prenom");
                list.addView(ligne(nom, autre.optString("photo_url"), false, "", () -> chat(cid, nom)));
            }
        });
    }

    /** « 28 ans · 175 cm · Infirmière » puis la description (anciens membres : champs vides ignorés). */
    String apercu(JSONObject o) {
        StringBuilder b = new StringBuilder();
        int a = o.optInt("age", 0), t = o.optInt("taille_cm", 0);
        String p = o.isNull("profession") ? "" : o.optString("profession");
        String d = o.isNull("description") ? "" : o.optString("description");
        if (a > 0) b.append(a).append(" ans");
        if (t > 0) b.append(b.length() > 0 ? " · " : "").append(t).append(" cm");
        if (!p.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(p);
        if (!d.isEmpty()) b.append(b.length() > 0 ? "\n" : "").append("« ").append(d).append(" »");
        return b.toString();
    }

    LinearLayout ligne(String titre, String photo, boolean enLigne, String sous, Runnable clic) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        U.rond(iv);
        Bitmap bm = photo.isEmpty() ? null : U.b64(photo);
        if (bm != null) iv.setImageBitmap(bm);
        h.addView(iv, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(this, 10), 0, 0, 0);
        LinearLayout l1 = new LinearLayout(this);
        l1.setGravity(Gravity.CENTER_VERTICAL);
        l1.addView(U.tv(this, titre, 18, true));
        if (enLigne) {
            TextView on = U.tv(this, "  ● en ligne", 12, false);
            on.setTextColor(0xFF22C55E);
            l1.addView(on);
        }
        col.addView(l1);
        if (!sous.isEmpty()) {
            TextView s = U.tv(this, sous, 13, false);
            s.setTextColor(U.MUTED);
            s.setMaxLines(4);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(s);
        }
        h.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        h.setOnClickListener(v -> clic.run());
        return h;
    }

    void chat(String cid, String nom) {
        startActivity(new Intent(this, ChatActivity.class).putExtra("cid", cid).putExtra("titre", nom));
    }

    void ouvrir(String autre, String nom) {
        Api.bg(() -> {
            try {
                boolean moiA = me.compareTo(autre) < 0;
                String a = moiA ? me : autre, b = moiA ? autre : me;
                JSONArray c = new JSONArray(Api.get("/rest/v1/conversations?membre_a=eq." + a + "&membre_b=eq." + b + "&select=id,statut"));
                String cid, st;
                if (c.length() == 0) {
                    JSONObject o = new JSONObject();
                    o.put("membre_a", a);
                    o.put("membre_b", b);
                    JSONArray r = new JSONArray(Api.post("/rest/v1/conversations", o.toString()));
                    cid = r.getJSONObject(0).getString("id");
                    st = "ouverte";
                } else {
                    cid = c.getJSONObject(0).getString("id");
                    st = c.getJSONObject(0).getString("statut");
                }
                if (!st.equals("ouverte")) { U.toast(this, "Cette discussion est fermée."); return; }
                runOnUiThread(() -> chat(cid, nom));
            } catch (Exception e) { U.toast(this, "Erreur : " + e.getMessage()); }
        });
    }
}
