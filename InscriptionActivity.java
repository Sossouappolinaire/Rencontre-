package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

public class InscriptionActivity extends Activity {
    EditText nom, prenom, email, wa;
    Spinner pays, sexe, cherche;
    ImageView img;
    String photo;
    ArrayList<String[]> L = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout l = U.col(this);
        sv.addView(l);
        setContentView(sv);
        l.addView(U.tv(this, "Créer mon compte", 22, true));

        img = new ImageView(this);
        img.setImageResource(android.R.drawable.ic_menu_camera);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        l.addView(img, new LinearLayout.LayoutParams(U.dp(this, 120), U.dp(this, 120)));
        Button bp = U.btn(this, "Ma photo de profil (réelle, pas d'IA)");
        bp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, 1);
        });
        l.addView(bp);

        nom = U.et(this, "Nom");
        prenom = U.et(this, "Prénom");
        email = U.et(this, "E-mail");
        wa = U.et(this, "Numéro WhatsApp avec indicatif (ex : 22990000000)");
        wa.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        l.addView(nom); l.addView(prenom); l.addView(email);

        for (String c : Locale.getISOCountries()) {
            String n = new Locale("", c).getDisplayCountry(Locale.FRENCH);
            if (!n.isEmpty()) L.add(new String[]{c, n});
        }
        Collections.sort(L, (x, y) -> x[1].compareToIgnoreCase(y[1]));
        ArrayList<String> items = new ArrayList<>();
        for (String[] c : L) items.add(U.drapeau(c[0]) + "  " + c[1]);
        pays = new Spinner(this);
        pays.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        l.addView(U.tv(this, "Pays", 14, true));
        l.addView(pays);
        l.addView(wa);

        sexe = new Spinner(this);
        sexe.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Je suis un homme", "Je suis une femme"}));
        cherche = new Spinner(this);
        cherche.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Je cherche un homme", "Je cherche une femme"}));
        cherche.setSelection(1);
        l.addView(sexe);
        l.addView(cherche);

        Button ok = U.btn(this, "S'inscrire");
        ok.setOnClickListener(v -> envoyer(ok));
        l.addView(ok);
        TextView adm = U.tv(this, "\nEspace administrateur", 13, false);
        adm.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        l.addView(adm);
    }

    void envoyer(Button ok) {
        String num = wa.getText().toString().replaceAll("[^0-9]", "");
        if (photo == null || nom.getText().toString().trim().isEmpty() || prenom.getText().toString().trim().isEmpty()
                || !email.getText().toString().contains("@") || num.length() < 8) {
            U.toast(this, "Remplis tous les champs et ajoute ta photo.");
            return;
        }
        String[] p = L.get(pays.getSelectedItemPosition());
        JSONObject o = new JSONObject();
        try {
            o.put("nom", nom.getText().toString().trim());
            o.put("prenom", prenom.getText().toString().trim());
            o.put("pays", p[1]);
            o.put("pays_code", p[0]);
            o.put("email", email.getText().toString().trim());
            o.put("whatsapp", num);
            o.put("sexe", sexe.getSelectedItemPosition() == 0 ? "homme" : "femme");
            o.put("cherche", cherche.getSelectedItemPosition() == 0 ? "homme" : "femme");
            o.put("photo_url", photo);
        } catch (JSONException e) { return; }
        ok.setEnabled(false);
        Api.bg(() -> {
            try {
                JSONArray r = new JSONArray(Api.post("/rest/v1/membres?select=id", o.toString()));
                String nid = r.getJSONObject(0).getString("id");
                U.setId(this, nid);
                try { Api.call("POST", "/functions/v1/envoyer-code", "{\"membre_id\":\"" + nid + "\"}", null); } catch (Exception ignore) { }
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            } catch (Exception e) {
                U.toast(this, "Erreur : " + e.getMessage());
                runOnUiThread(() -> ok.setEnabled(true));
            }
        });
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == 1 && rs == RESULT_OK && d != null && d.getData() != null) {
            try {
                android.graphics.Bitmap bm = U.decode(this, d.getData(), 400);
                img.setImageBitmap(bm);
                photo = U.enc(bm);
            } catch (Exception e) { U.toast(this, "Photo illisible."); }
        }
    }
}
