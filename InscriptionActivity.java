package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

public class InscriptionActivity extends Activity {
    EditText nom, prenom, email, wa, age, profession, desc;
    Spinner taille;
    static final int TAILLE_MIN = 100, TAILLE_MAX = 230, TAILLE_PAS = 1; // en cm
    Spinner pays, sexe, cherche;
    ImageView img;
    String photo;
    ArrayList<String[]> L = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout l = U.col(this);
        sv.addView(l);
        setContentView(sv);
        l.addView(U.header(this));
        l.addView(U.tv(this, "Créer mon compte", 20, true));

        img = new ImageView(this);
        img.setImageResource(android.R.drawable.ic_menu_camera);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        U.rond(img);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(U.dp(this, 120), U.dp(this, 120));
        ip.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        ip.setMargins(0, U.dp(this, 8), 0, U.dp(this, 8));
        l.addView(img, ip);
        Button bp = U.btn(this, "Ma photo de profil (réelle, pas d'IA)");
        bp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, 1);
        });
        U.add(l, bp);

        nom = U.et(this, "Nom");
        prenom = U.et(this, "Prénom");
        email = U.et(this, "E-mail");
        wa = U.et(this, "Numéro WhatsApp avec indicatif (ex : 22990000000)");
        wa.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        U.add(l, nom); U.add(l, prenom); U.add(l, email);

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
        U.champ(pays);
        U.add(l, pays);
        U.add(l, wa);

        sexe = new Spinner(this);
        sexe.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Je suis un homme", "Je suis une femme"}));
        cherche = new Spinner(this);
        cherche.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Je cherche un homme", "Je cherche une femme"}));
        cherche.setSelection(1);
        U.champ(sexe); U.champ(cherche);
        U.add(l, sexe);
        U.add(l, cherche);

        age = U.et(this, "Âge (18 ans minimum)");
        age.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        age.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        ArrayList<String> tailles = new ArrayList<>();
        for (int cm = TAILLE_MIN; cm <= TAILLE_MAX; cm += TAILLE_PAS)
            tailles.add((cm / 100) + "m" + (cm % 100 == 0 ? "" : String.format(Locale.US, "%02d", cm % 100)));
        taille = new Spinner(this);
        taille.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tailles));
        taille.setSelection((170 - TAILLE_MIN) / TAILLE_PAS);
        U.champ(taille);
        profession = U.et(this, "Profession");
        profession.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        desc = U.et(this, "Décris la personne que tu cherches : ses qualités, ses valeurs…");
        desc.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        desc.setMinLines(4);
        desc.setGravity(android.view.Gravity.TOP);
        desc.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        l.addView(U.tv(this, "À propos de toi", 14, true));
        U.add(l, age);
        l.addView(U.tv(this, "Taille", 14, true));
        U.add(l, taille);
        U.add(l, profession);
        l.addView(U.tv(this, "Les qualités que tu recherches", 14, true));
        U.add(l, desc);

        U.PBtn ok = U.inscription(this, "S'inscrire");
        ok.setOnClickListener(v -> envoyer(ok));
        U.add(l, ok);
        U.PBtn deja = U.pulse(this, "J'ai déjà un compte");
        deja.setOnClickListener(v -> startActivity(new Intent(this, ConnexionActivity.class)));
        U.add(l, deja);
    }

    static int nombre(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return 0; }
    }

    void envoyer(U.PBtn ok) {
        String num = wa.getText().toString().replaceAll("[^0-9]", "");
        if (photo == null || nom.getText().toString().trim().isEmpty() || prenom.getText().toString().trim().isEmpty()
                || !email.getText().toString().contains("@") || num.length() < 8) {
            U.toast(this, "Remplis tous les champs et ajoute ta photo.");
            return;
        }
        int a = nombre(age), t = TAILLE_MIN + taille.getSelectedItemPosition() * TAILLE_PAS;
        if (a < 18 || a > 99) { U.toast(this, "L'inscription est réservée aux personnes de 18 ans et plus."); return; }
        if (profession.getText().toString().trim().isEmpty()) { U.toast(this, "Indique ta profession."); return; }
        if (desc.getText().toString().trim().length() < 10) { U.toast(this, "Décris en quelques mots les qualités que tu recherches."); return; }
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
            o.put("age", a);
            o.put("taille_cm", t);
            o.put("profession", profession.getText().toString().trim());
            o.put("description", desc.getText().toString().trim());
        } catch (JSONException e) { return; }
        ok.start("Inscription");
        Api.bg(() -> {
            try {
                JSONArray r = new JSONArray(Api.post("/rest/v1/membres?select=id", o.toString()));
                String nid = r.getJSONObject(0).getString("id");
                U.setId(this, nid);
                boolean codePart = true;
                try { Api.call("POST", "/functions/v1/envoyer-code", "{\"membre_id\":\"" + nid + "\"}", null); }
                catch (Exception ex) { codePart = false; }
                if (!codePart) U.toast(this, "Compte créé, mais l'e-mail n'a pas pu partir. Appuie sur « Renvoyer le code ».");
                runOnUiThread(() -> ok.succes(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                }));
            } catch (Exception e) {
                U.toast(this, "Erreur : " + e.getMessage());
                runOnUiThread(ok::stop);
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
