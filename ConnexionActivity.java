package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.IOException;
import org.json.*;

/** Connexion à un compte existant : e-mail, puis code reçu par e-mail. */
public class ConnexionActivity extends Activity {
    LinearLayout root;
    String email = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        root = U.col(this);
        setContentView(root);
        etapeEmail();
    }

    /** Carte centrale arrondie (style des maquettes), le contenu s'y ajoute. */
    LinearLayout carte(String titre) {
        root.removeAllViews();
        root.addView(U.header(this));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        android.graphics.drawable.GradientDrawable g = U.fond(0x33000000, U.dp(this, 28));
        g.setStroke(U.dp(this, 1), 0x44F2C6B8);
        c.setBackground(g);
        c.setElevation(U.dp(this, 12));
        int p = U.dp(this, 20);
        c.setPadding(p, p, p, p);
        TextView t = U.tv(this, titre, 24, true);
        t.setTextColor(U.OR);
        t.setLetterSpacing(0.08f);
        t.setGravity(android.view.Gravity.CENTER);
        c.addView(t, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, U.dp(this, 8), 0, U.dp(this, 8));
        root.addView(c, lp);
        return c;
    }

    /** Champ en forme de pilule. */
    void pilule(EditText e) {
        android.graphics.drawable.GradientDrawable g = U.fond(0x22FFFFFF, U.dp(this, 26));
        g.setStroke(U.dp(this, 1), 0x66F2C6B8);
        e.setBackground(g);
        int h = U.dp(this, 18), v = U.dp(this, 12);
        e.setPadding(h, v, h, v);
    }

    void etapeEmail() {
        LinearLayout k = carte("CONNEXION");
        TextView d = U.tv(this, "Entre l'e-mail de ton compte, nous t'envoyons un code.", 14, false);
        d.setGravity(android.view.Gravity.CENTER);
        d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 6));
        k.addView(d);
        EditText em = U.et(this, "E-mail");
        em.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        em.setText(email);
        pilule(em);
        U.add(k, em);
        U.PBtn ok = U.connexion(this, "Recevoir le code");
        ok.setOnClickListener(v -> {
            email = em.getText().toString().trim();
            if (email.isEmpty()) { U.toast(this, "E-mail invalide."); return; }
            // Sans « @ » : l'application cherche un compte administrateur dans la base
            if (!email.contains("@")) { etapeAdmin(email); return; }
            ok.start("Envoi");
            Api.bg(() -> {
                try {
                    Api.call("POST", "/functions/v1/envoyer-connexion", new JSONObject().put("email", email).toString(), null);
                    runOnUiThread(() -> ok.succes(this::etapeCode));
                } catch (Exception e) { U.toast(this, "Envoi impossible, réessaie."); runOnUiThread(ok::stop); }
            });
        });
        U.add(k, ok);
        U.add(root, U.retour(this));
    }

    /** Compte administrateur : reconnu uniquement s'il existe dans la base (table admins). Aucun bouton visible pour y accéder. */
    void etapeAdmin(String identifiant) {
        LinearLayout k = carte("MOT DE PASSE");
        EditText mdp = U.et(this, "Mot de passe");
        mdp.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pilule(mdp);
        U.add(k, mdp);
        U.PBtn ok = U.connexion(this, "Connexion");
        ok.setOnClickListener(v -> {
            final String pw = mdp.getText().toString();
            ok.start("Connexion");
            Api.bg(() -> {
                try {
                    String r = Api.call("POST", "/rest/v1/rpc/admin_login",
                            new JSONObject().put("p_identifiant", identifiant).put("p_mdp", pw).toString(), null);
                    if (r.trim().equals("true")) runOnUiThread(() -> ok.succes(() ->
                            startActivity(new Intent(this, AdminActivity.class).putExtra("aid", identifiant).putExtra("amdp", pw))));
                    else { U.toast(this, "Identifiants incorrects."); runOnUiThread(ok::stop); }
                } catch (Exception e) { U.toast(this, "Erreur réseau."); runOnUiThread(ok::stop); }
            });
        });
        U.add(k, ok);
        U.add(root, U.retour(this, "← Retour", this::etapeEmail));
    }

    void envoyerCode(TextView lien) {
        lien.setEnabled(false);
        Api.bg(() -> {
            try {
                Api.call("POST", "/functions/v1/envoyer-connexion", new JSONObject().put("email", email).toString(), null);
                U.toast(this, "Nouveau code envoyé.");
            } catch (Exception e) { U.toast(this, "Envoi impossible, réessaie."); }
            runOnUiThread(() -> lien.postDelayed(() -> lien.setEnabled(true), 30000));
        });
    }

    void etapeCode() {
        LinearLayout k = carte("CODE DE CONNEXION");
        TextView d = U.tv(this, "Si un compte existe pour " + email + ", un code vient d'être envoyé (regarde aussi les spams).", 13, false);
        d.setGravity(android.view.Gravity.CENTER);
        d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 14));
        k.addView(d);

        // Six cases visuelles ; un champ invisible par-dessus capte la saisie.
        FrameLayout zoneCode = new FrameLayout(this);
        LinearLayout ligne = new LinearLayout(this);
        ligne.setGravity(android.view.Gravity.CENTER);
        final TextView[] cases = new TextView[6];
        for (int n = 0; n < 6; n++) {
            TextView t = new TextView(this);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(22);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setTextColor(U.TEXTE);
            cases[n] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(this, 42), U.dp(this, 52));
            lp.setMargins(U.dp(this, 4), 0, U.dp(this, 4), 0);
            ligne.addView(t, lp);
        }
        EditText c = new EditText(this);
        c.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        c.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        c.setBackground(null);
        c.setCursorVisible(false);
        c.setTextColor(0x00000000);
        zoneCode.addView(ligne, new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER));
        zoneCode.addView(c, new FrameLayout.LayoutParams(-1, U.dp(this, 52), android.view.Gravity.CENTER));
        k.addView(zoneCode, new LinearLayout.LayoutParams(-1, -2));

        U.PBtn ok = U.valider(this, "Se connecter");
        Runnable maj = () -> {
            String v = c.getText().toString();
            for (int n = 0; n < 6; n++) {
                cases[n].setText(n < v.length() ? String.valueOf(v.charAt(n)) : "");
                android.graphics.drawable.GradientDrawable g = U.fond(0x22FFFFFF, U.dp(this, 12));
                g.setStroke(U.dp(this, n == v.length() ? 2 : 1), n == v.length() ? U.ROSE : 0x88F2C6B8);
                cases[n].setBackground(g);
            }
        };
        maj.run();
        c.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b2, int c2, int d2) { }
            public void onTextChanged(CharSequence a, int b2, int c2, int d2) { }
            public void afterTextChanged(android.text.Editable e) {
                maj.run();
                if (e.length() == 6 && !ok.busy) ok.performClick();
            }
        });

        TextView renv = new TextView(this);
        renv.setText(android.text.Html.fromHtml("Tu n'as pas reçu le code ? <u>Renvoyer</u>", android.text.Html.FROM_HTML_MODE_LEGACY));
        renv.setTextColor(U.TEXTE);
        renv.setTextSize(13);
        renv.setGravity(android.view.Gravity.CENTER);
        renv.setPadding(0, U.dp(this, 16), 0, U.dp(this, 8));
        renv.setOnClickListener(v -> envoyerCode(renv));
        k.addView(renv, new LinearLayout.LayoutParams(-1, -2));

        ok.setOnClickListener(v -> {
            ok.start("Vérification");
            Api.bg(() -> {
                try {
                    String r = Api.call("POST", "/rest/v1/rpc/verifier_connexion",
                            new JSONObject().put("p_email", email).put("p_code", c.getText().toString().trim()).toString(), null).trim();
                    if (r.isEmpty() || r.equals("null")) throw new IOException("{\"erreur\":\"Code incorrect ou expiré.\"}");
                    String id = r.replace("\"", "");
                    U.setId(this, id);
                    runOnUiThread(() -> ok.succes(() -> {
                        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                        finish();
                    }));
                } catch (Exception e) {
                    String m = "Code incorrect ou expiré.";
                    try { m = new JSONObject(e.getMessage()).optString("erreur", m); } catch (Exception ignore) { }
                    U.toast(this, m);
                    runOnUiThread(() -> { ok.stop(); c.setText(""); });
                }
            });
        });
        U.add(k, ok);
        U.add(root, U.retour(this, "← Changer d'e-mail", this::etapeEmail));
        c.requestFocus();
    }
}
