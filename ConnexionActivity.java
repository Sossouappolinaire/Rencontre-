package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.io.IOException;
import org.json.*;

/** Connexion à un compte existant : e-mail + mot de passe. Mot de passe oublié : code reçu par e-mail. */
public class ConnexionActivity extends Activity {
    LinearLayout root;
    String email = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        ScrollView sv = new ScrollView(this);
        root = U.col(this);
        sv.addView(root);
        setContentView(sv);
        String e0 = getIntent().getStringExtra("email");
        if (e0 != null) email = e0;
        etapeConnexion();
        if (getIntent().getBooleanExtra("nouveau", false))
            U.dialogue(this, "Compte créé ✅", "Connecte-toi avec ton e-mail et ton mot de passe. L'administrateur validera ton compte et t'enverra un code sur WhatsApp.");
    }

    // ---------- Outils d'interface ----------

    /** Carte centrale arrondie (style des maquettes), le contenu s'y ajoute. */
    LinearLayout carte(String titre) {
        root.removeAllViews();
        root.addView(U.header(this));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        android.graphics.drawable.GradientDrawable g = U.fond(0x33000000, U.dp(this, 28));
        g.setStroke(U.dp(this, 1), 0x44F2C6B8);
        c.setBackground(g);
        c.setElevation(U.dp(this, 12));
        int p = U.dp(this, 20);
        c.setPadding(p, p, p, p);
        TextView t = U.tv(this, titre, 24, true);
        t.setTextColor(U.OR);
        t.setLetterSpacing(0.08f);
        t.setGravity(Gravity.CENTER);
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

    EditText champMdp(String hint) {
        EditText e = U.et(this, hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pilule(e);
        return e;
    }

    TextView lien(String html) {
        TextView t = new TextView(this);
        t.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY));
        t.setTextColor(U.TEXTE);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, U.dp(this, 16), 0, U.dp(this, 8));
        return t;
    }

    String msg(Exception e, String def) {
        try {
            JSONObject o = new JSONObject(e.getMessage());
            String m = o.optString("erreur", o.optString("message", def));
            return m.isEmpty() ? def : m;
        } catch (Exception x) { return def; }
    }

    void entrer(String id) {
        U.setId(this, id);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    // ---------- Étape 1 : e-mail + mot de passe ----------

    void etapeConnexion() {
        LinearLayout k = carte("CONNEXION");
        TextView d = U.tv(this, "Entre l'e-mail et le mot de passe de ton compte.", 14, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 6));
        k.addView(d);
        EditText em = U.et(this, "E-mail");
        em.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        em.setText(email);
        pilule(em);
        U.add(k, em);
        EditText mdp = champMdp("Mot de passe");
        U.add(k, mdp);
        U.PBtn ok = U.connexion(this, "Se connecter");
        ok.setOnClickListener(v -> {
            email = em.getText().toString().trim();
            final String pw = mdp.getText().toString();
            if (email.isEmpty() || pw.isEmpty()) { U.erreur(this, "Entre ton e-mail et ton mot de passe."); return; }
            ok.start("Connexion");
            Api.bg(() -> {
                // 1) Compte administrateur : reconnu uniquement s'il existe dans la table admins (identifiant avec ou sans « @ »)
                try {
                    String a = Api.call("POST", "/rest/v1/rpc/admin_login",
                            new JSONObject().put("p_identifiant", email).put("p_mdp", pw).toString(), null);
                    if (a.trim().equals("true")) {
                        final String idAdmin = email;
                        runOnUiThread(() -> ok.succes(() ->
                                startActivity(new Intent(this, AdminActivity.class).putExtra("aid", idAdmin).putExtra("amdp", pw))));
                        return;
                    }
                } catch (Exception ignore) { }
                if (!email.contains("@")) {
                    U.erreur(this, "Identifiants incorrects.");
                    runOnUiThread(ok::stop);
                    return;
                }
                // 2) Compte membre : e-mail + mot de passe
                try {
                    String r = Api.call("POST", "/rest/v1/rpc/connexion_mdp",
                            new JSONObject().put("p_email", email).put("p_mdp", pw).toString(), null).trim();
                    if (r.isEmpty() || r.equals("null"))
                        throw new IOException("{\"erreur\":\"E-mail ou mot de passe incorrect.\"}");
                    final String id = r.replace("\"", "");
                    runOnUiThread(() -> ok.succes(() -> entrer(id)));
                } catch (Exception e) {
                    U.erreur(this, msg(e, "Erreur réseau."));
                    runOnUiThread(ok::stop);
                }
            });
        });
        U.add(k, ok);
        TextView oubli = lien("Mot de passe oublié ? <u>Recevoir un code</u>");
        oubli.setOnClickListener(v -> {
            email = em.getText().toString().trim();
            if (!email.contains("@")) { U.erreur(this, "Entre d'abord ton e-mail."); return; }
            oubli.setEnabled(false);
            Api.bg(() -> {
                try {
                    Api.call("POST", "/functions/v1/envoyer-connexion", new JSONObject().put("email", email).toString(), null);
                    runOnUiThread(this::etapeCode);
                } catch (Exception e) {
                    U.erreur(this, "Envoi impossible, réessaie.");
                    runOnUiThread(() -> oubli.setEnabled(true));
                }
            });
        });
        k.addView(oubli, new LinearLayout.LayoutParams(-1, -2));
        U.add(root, U.accueil(this));
        U.add(root, U.retour(this));
    }

    // ---------- Mot de passe oublié : code reçu par e-mail ----------

    void envoyerCode(TextView lien) {
        lien.setEnabled(false);
        Api.bg(() -> {
            try {
                Api.call("POST", "/functions/v1/envoyer-connexion", new JSONObject().put("email", email).toString(), null);
                U.toast(this, "Nouveau code envoyé.");
            } catch (Exception e) { U.erreur(this, "Envoi impossible, réessaie."); }
            runOnUiThread(() -> lien.postDelayed(() -> lien.setEnabled(true), 30000));
        });
    }

    void etapeCode() {
        LinearLayout k = carte("CODE REÇU");
        TextView d = U.tv(this, "Si un compte existe pour " + email + ", un code vient d'être envoyé (regarde aussi les spams).", 13, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 14));
        k.addView(d);

        U.PBtn ok = U.valider(this, "Continuer");
        OtpView otp = new OtpView(this, 6, () -> ok.performClick());
        k.addView(otp, new LinearLayout.LayoutParams(-1, -2));
        Button coller = U.btn(this, "📋 Coller le code");
        coller.setTextSize(12);
        coller.setOnClickListener(v -> { if (!otp.coller(this, true)) U.erreur(this, "Aucun code à 6 chiffres copié."); });
        LinearLayout ac = new LinearLayout(this);
        ac.setGravity(Gravity.CENTER);
        ac.setPadding(0, U.dp(this, 14), 0, 0);
        ac.addView(coller, new LinearLayout.LayoutParams(0, -2, 1));
        k.addView(ac, new LinearLayout.LayoutParams(-1, -2));
        k.addView(CodeRecu.activation(this), new LinearLayout.LayoutParams(-1, -2));
        otp.post(() -> otp.coller(this, false)); // code déjà copié depuis l'e-mail

        TextView renv = lien("Tu n'as pas reçu le code ? <u>Renvoyer</u>");
        renv.setOnClickListener(v -> envoyerCode(renv));
        k.addView(renv, new LinearLayout.LayoutParams(-1, -2));

        ok.setOnClickListener(v -> {
            String code = otp.code().trim();
            if (code.length() != 6) { U.erreur(this, "Le code contient 6 chiffres."); return; }
            etapeNouveauMdp(code);
        });
        U.add(k, ok);
        U.add(root, U.retour(this, "← Retour", this::etapeConnexion));
    }

    void etapeNouveauMdp(String code) {
        LinearLayout k = carte("NOUVEAU MOT DE PASSE");
        TextView d = U.tv(this, "Choisis un mot de passe (8 caractères minimum).", 13, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, U.dp(this, 10), 0, U.dp(this, 6));
        k.addView(d);
        EditText m1 = champMdp("Nouveau mot de passe");
        EditText m2 = champMdp("Confirmer le mot de passe");
        U.add(k, m1);
        U.add(k, m2);
        U.PBtn ok = U.valider(this, "Enregistrer");
        ok.setOnClickListener(v -> {
            final String pw = m1.getText().toString();
            if (pw.length() < 8) { U.erreur(this, "8 caractères minimum."); return; }
            if (!pw.equals(m2.getText().toString())) { U.erreur(this, "Les deux mots de passe sont différents."); return; }
            ok.start("Enregistrement");
            Api.bg(() -> {
                try {
                    String r = Api.call("POST", "/rest/v1/rpc/reinitialiser_mdp",
                            new JSONObject().put("p_email", email).put("p_code", code).put("p_mdp", pw).toString(), null).trim();
                    if (!r.equals("true")) {
                        U.erreur(this, "Code incorrect ou expiré.");
                        runOnUiThread(this::etapeCode);
                        return;
                    }
                    String id = Api.call("POST", "/rest/v1/rpc/connexion_mdp",
                            new JSONObject().put("p_email", email).put("p_mdp", pw).toString(), null).trim().replace("\"", "");
                    runOnUiThread(() -> ok.succes(() -> entrer(id)));
                } catch (Exception e) {
                    U.erreur(this, msg(e, "Erreur réseau."));
                    runOnUiThread(ok::stop);
                }
            });
        });
        U.add(k, ok);
        U.add(root, U.retour(this, "← Retour", this::etapeConnexion));
    }
}
