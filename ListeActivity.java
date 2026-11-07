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
        b1.setOnClickListener(v -> { disc = false; charger(); });
        b2.setOnClickListener(v -> { disc = true; charger(); });
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        r.addView(sv);
        setContentView(r);
    }
    @Override protected void onResume() { super.onResume(); charger(); }

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
        JSONArray a = new JSONArray(Api.get("/rest/v1/membres?pays_code=eq." + moi.getString("pays_code")
                + "&sexe=eq." + moi.getString("cherche") + "&statut=eq.actif&en_couple=eq.false&id=neq." + me
                + "&select=id,prenom,photo_url&order=cree_le.desc"));
        runOnUiThread(() -> {
            list.removeAllViews();
            if (a.length() == 0) list.addView(U.tv(this, "\nPersonne pour le moment dans ton pays.", 16, false));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                list.addView(ligne(o.optString("prenom"), o.optString("photo_url"), () -> ouvrir(o.optString("id"), o.optString("prenom"))));
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
                list.addView(ligne(nom, autre.optString("photo_url"), () -> chat(cid, nom)));
            }
        });
    }

    LinearLayout ligne(String titre, String photo, Runnable clic) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bm = photo.isEmpty() ? null : U.b64(photo);
        if (bm != null) iv.setImageBitmap(bm);
        h.addView(iv, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        TextView t = U.tv(this, "  " + titre, 18, true);
        h.addView(t);
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
