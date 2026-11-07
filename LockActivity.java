package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.widget.*;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Verrou de l'application : code PIN (4 à 6 chiffres) et/ou empreinte digitale.
 * Mode "creer" : définir le PIN. Mode "ouvrir" : le saisir (ou empreinte).
 * Une fois déverrouillée, l'application reste ouverte jusqu'à la déconnexion
 * (le verrou ne revient qu'au prochain lancement de l'application).
 */
public class LockActivity extends Activity {
    static boolean deverrouille = false;
    boolean creer;
    String premier = null;
    EditText pin;
    TextView msg;
    CheckBox bio;

    static SharedPreferences sp(android.content.Context c) { return c.getSharedPreferences("lock", 0); }
    static boolean pinDefini(android.content.Context c) { return sp(c).getString("h", null) != null; }
    static void effacer(android.content.Context c) { sp(c).edit().clear().apply(); deverrouille = false; }

    static String hash(String sel, String pin) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((sel + ":" + pin).getBytes("UTF-8"));
            StringBuilder s = new StringBuilder();
            for (byte x : d) s.append(String.format("%02x", x));
            return s.toString();
        } catch (Exception e) { return ""; }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        creer = "creer".equals(getIntent().getStringExtra("mode"));
        LinearLayout r = U.col(this);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        r.addView(U.header(this));
        r.addView(U.tv(this, creer ? "Crée ton code PIN" : "Entre ton code PIN", 20, true));
        msg = U.tv(this, creer ? "4 à 6 chiffres. Il protège l'accès à ton compte." : "", 13, false);
        r.addView(msg);
        pin = U.et(this, "PIN");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        pin.setGravity(Gravity.CENTER);
        U.add(r, pin);
        U.PBtn ok = U.valider(this, creer ? "Enregistrer" : "Déverrouiller");
        ok.setOnClickListener(v -> valider());
        U.add(r, ok);
        if (creer) {
            if (Build.VERSION.SDK_INT >= 28) {
                bio = new CheckBox(this);
                bio.setText("Utiliser aussi l'empreinte digitale");
                bio.setTextColor(U.TEXTE);
                bio.setChecked(true);
                r.addView(bio);
            }
            U.add(r, U.retour(this, "Plus tard", this::finish));
        } else if (Build.VERSION.SDK_INT >= 28 && sp(this).getBoolean("bio", false)) {
            U.PBtn e = U.pulse(this, "Utiliser l'empreinte");
            e.setOnClickListener(v -> empreinte());
            U.add(r, e);
        }
        setContentView(r);
        if (!creer && Build.VERSION.SDK_INT >= 28 && sp(this).getBoolean("bio", false)) empreinte();
    }

    void valider() {
        String p = pin.getText().toString();
        if (p.length() < 4) { msg.setText("Au moins 4 chiffres."); return; }
        SharedPreferences s = sp(this);
        if (creer) {
            if (premier == null) {
                premier = p; pin.setText(""); msg.setText("Confirme ton PIN.");
                return;
            }
            if (!premier.equals(p)) { premier = null; pin.setText(""); msg.setText("Les deux PIN sont différents. Recommence."); return; }
            byte[] r = new byte[8];
            new SecureRandom().nextBytes(r);
            String sel = android.util.Base64.encodeToString(r, android.util.Base64.NO_WRAP);
            s.edit().putString("sel", sel).putString("h", hash(sel, p))
                    .putBoolean("bio", bio != null && bio.isChecked()).apply();
            deverrouille = true;
            Toast.makeText(this, "PIN enregistré.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        long bloque = s.getLong("bloque", 0);
        if (System.currentTimeMillis() < bloque) {
            msg.setText("Trop d'essais. Réessaie dans " + ((bloque - System.currentTimeMillis()) / 1000 + 1) + " s.");
            return;
        }
        if (hash(s.getString("sel", ""), p).equals(s.getString("h", ""))) {
            s.edit().putInt("essais", 0).apply();
            ouvrir();
        } else {
            int n = s.getInt("essais", 0) + 1;
            SharedPreferences.Editor e = s.edit().putInt("essais", n);
            if (n >= 5) { e.putLong("bloque", System.currentTimeMillis() + 30000).putInt("essais", 0); msg.setText("PIN incorrect. Bloqué 30 secondes."); }
            else msg.setText("PIN incorrect (" + n + "/5).");
            e.apply();
            pin.setText("");
        }
    }

    void ouvrir() {
        deverrouille = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    void empreinte() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            android.hardware.biometrics.BiometricPrompt p = new android.hardware.biometrics.BiometricPrompt.Builder(this)
                    .setTitle("Lyly Dating").setSubtitle("Confirme ton identité")
                    .setNegativeButton("Utiliser le PIN", getMainExecutor(), (d, w) -> { })
                    .build();
            p.authenticate(new CancellationSignal(), getMainExecutor(),
                    new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) { ouvrir(); }
                    });
        } catch (Exception e) { msg.setText("Empreinte indisponible, utilise le PIN."); }
    }

    @Override public void onBackPressed() {
        if (creer) super.onBackPressed();
        else { Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
    }
}
