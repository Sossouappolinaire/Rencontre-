package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;

public class ChatActivity extends Activity {
    String cid, me, membreA = "";
    boolean ro, actif = true;
    LinearLayout box;
    ScrollView sv;
    EditText in;
    TextView etat;
    String last = "";
    Handler h = new Handler(Looper.getMainLooper());
    Runnable poll;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        cid = getIntent().getStringExtra("cid");
        ro = getIntent().getBooleanExtra("ro", false);
        me = ro ? null : U.id(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1E070D);

        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(0xFF5A0F24);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int p = U.dp(this, 10);
        top.setPadding(p, p, p, p);
        TextView t = U.tv(this, getIntent().getStringExtra("titre"), 18, true);
        t.setTextColor(Color.WHITE);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (!ro) {
            Button ok = U.btn(this, "✔ D'accord"), no = U.btn(this, "✖ Rejeter");
            ok.setTextSize(12); no.setTextSize(12);
            ok.setBackground(U.fond(0xFF25D366, U.dp(this, 20)));
            no.setBackground(U.fond(0xFFC0392B, U.dp(this, 20)));
            ok.setOnClickListener(v -> decision(true));
            no.setOnClickListener(v -> decision(false));
            top.addView(ok); top.addView(no);
        }
        root.addView(top);
        etat = U.tv(this, "", 12, false);
        etat.setGravity(Gravity.CENTER);
        root.addView(etat);

        sv = new ScrollView(this);
        box = U.col(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!ro) {
            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            Button ph = new Button(this);
            ph.setText("📎");
            ph.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                startActivityForResult(i, 5);
            });
            in = new EditText(this);
            in.setHint("Message");
            in.setBackground(U.fond(0x22FFFFFF, U.dp(this, 20)));
            in.setHintTextColor(0x99E8C4CB);
            in.setTextColor(U.TEXTE);
            in.setPadding(p, p, p, p);
            Button se = U.btn(this, "➤");
            se.setOnClickListener(v -> envoyer());
            bar.addView(ph);
            bar.addView(in, new LinearLayout.LayoutParams(0, -2, 1));
            bar.addView(se);
            root.addView(bar);
        }
        setContentView(root);
        poll = () -> {
            if (!actif) return;
            Api.bg(() -> {
                try { charger(); } catch (Exception e) { }
                h.postDelayed(poll, 3000);
            });
        };
        h.post(poll);
    }

    @Override protected void onResume() { super.onResume(); if (!ro) Presence.start(me); }
    @Override protected void onPause() { super.onPause(); if (!ro) Presence.leave(); }
    @Override protected void onDestroy() { super.onDestroy(); actif = false; h.removeCallbacks(poll); }

    void charger() throws Exception {
        JSONObject cv = new JSONArray(Api.get("/rest/v1/conversations?id=eq." + cid
                + "&select=*,a:membres!membre_a(prenom),b:membres!membre_b(prenom)")).getJSONObject(0);
        membreA = cv.getString("membre_a");
        String nA = cv.getJSONObject("a").optString("prenom"), nB = cv.getJSONObject("b").optString("prenom");
        String st = cv.getString("statut");
        boolean aA = cv.getBoolean("accord_a"), aB = cv.getBoolean("accord_b");
        if (!ro && st.equals("rejetee")) { U.toast(this, "Discussion fermée."); runOnUiThread(this::finish); actif = false; return; }
        if (!ro && st.equals("accord")) { U.toast(this, "🎉 Vous êtes d'accord : un couple est formé !"); runOnUiThread(this::finish); actif = false; return; }
        boolean iAmA = membreA.equals(me);
        String txt;
        if (ro) txt = nA + (aA ? " ✔" : " …") + "  ·  " + nB + (aB ? " ✔" : " …") + "  ·  " + st;
        else if ((iAmA ? aA : aB) && !(iAmA ? aB : aA)) txt = "Tu es d'accord. En attente de l'autre personne.";
        else if (!(iAmA ? aA : aB) && (iAmA ? aB : aA)) txt = "L'autre personne est d'accord. À toi de répondre.";
        else txt = "";

        JSONArray m = new JSONArray(Api.get("/rest/v1/messages?conversation_id=eq." + cid
                + "&order=cree_le.asc&select=id,expediteur,texte,a_photo,cree_le"));
        for (int i = 0; i < m.length(); i++) {
            JSONObject o = m.getJSONObject(i);
            File f = new File(getCacheDir(), o.getString("id") + ".jpg");
            if (!ro && o.optBoolean("a_photo") && !f.exists() && !o.getString("expediteur").equals(me)) {
                JSONArray p = new JSONArray(Api.get("/rest/v1/messages?id=eq." + o.getString("id") + "&select=photo_b64"));
                String bb = p.getJSONObject(0).optString("photo_b64", "");
                if (!bb.isEmpty() && !bb.equals("null")) {
                    try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(android.util.Base64.decode(bb, android.util.Base64.DEFAULT)); }
                    Api.patch("/rest/v1/messages?id=eq." + o.getString("id"), "{\"photo_b64\":null}");
                }
            }
        }
        String sig = m.toString() + txt;
        if (!sig.equals(last)) {
            last = sig;
            runOnUiThread(() -> rendre(m, nA, nB, txt));
        }
    }

    void rendre(JSONArray m, String nA, String nB, String txt) {
        etat.setText(txt);
        box.removeAllViews();
        for (int i = 0; i < m.length(); i++) {
            JSONObject o = m.optJSONObject(i);
            String exp = o.optString("expediteur");
            boolean mine = ro ? exp.equals(membreA) : exp.equals(me);
            LinearLayout w = new LinearLayout(this);
            w.setGravity(mine ? Gravity.END : Gravity.START);
            LinearLayout bub = new LinearLayout(this);
            bub.setOrientation(LinearLayout.VERTICAL);
            bub.setBackground(U.fond(mine ? 0xFFB3123F : 0x33FFFFFF, U.dp(this, 14)));
            int p = U.dp(this, 8);
            bub.setPadding(p, p, p, p);
            if (ro) bub.addView(U.tv(this, mine ? nA : nB, 12, true));
            if (o.optBoolean("a_photo")) {
                File f = new File(getCacheDir(), o.optString("id") + ".jpg");
                if (f.exists()) {
                    ImageView iv = new ImageView(this);
                    iv.setAdjustViewBounds(true);
                    iv.setImageBitmap(BitmapFactory.decodeFile(f.getPath()));
                    bub.addView(iv, new LinearLayout.LayoutParams(U.dp(this, 220), -2));
                } else bub.addView(U.tv(this, "📷 Photo (non conservée)", 14, false));
            }
            String tx = o.optString("texte");
            if (!tx.isEmpty()) bub.addView(U.tv(this, tx, 16, false));
            TextView hr = U.tv(this, U.heure(o.optString("cree_le")), 10, false);
            hr.setTextColor(0xFFE8C4CB);
            bub.addView(hr);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (mine) lp.setMargins(U.dp(this, 48), 0, 0, 0); else lp.setMargins(0, 0, U.dp(this, 48), 0);
            w.addView(bub, lp);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.setMargins(0, U.dp(this, 3), 0, U.dp(this, 3));
            box.addView(w, wl);
        }
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    void envoyer() {
        String t = in.getText().toString().trim();
        if (t.isEmpty()) return;
        in.setText("");
        Api.bg(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("conversation_id", cid); o.put("expediteur", me); o.put("texte", t);
                Api.post("/rest/v1/messages", o.toString());
                charger();
            } catch (Exception e) { U.toast(this, "Message non envoyé."); }
        });
    }

    void decision(boolean oui) {
        Api.bg(() -> {
            try {
                String champ = membreA.equals(me) ? "accord_a" : "accord_b";
                Api.patch("/rest/v1/conversations?id=eq." + cid,
                        oui ? "{\"" + champ + "\":true}" : "{\"statut\":\"rejetee\"}");
                charger();
            } catch (Exception e) { U.toast(this, "Erreur réseau."); }
        });
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq != 5 || rs != RESULT_OK || d == null || d.getData() == null) return;
        Api.bg(() -> {
            try {
                Bitmap bm = U.decode(this, d.getData(), 600);
                JSONObject o = new JSONObject();
                o.put("conversation_id", cid); o.put("expediteur", me); o.put("texte", "");
                o.put("a_photo", true); o.put("photo_b64", U.enc(bm));
                String id = new JSONArray(Api.post("/rest/v1/messages", o.toString())).getJSONObject(0).getString("id");
                try (FileOutputStream fo = new FileOutputStream(new File(getCacheDir(), id + ".jpg"))) {
                    bm.compress(Bitmap.CompressFormat.JPEG, 80, fo);
                }
                charger();
            } catch (Exception e) { U.toast(this, "Photo non envoyée."); }
        });
    }
}
