package bj.rencontre;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saisie d'un code en cases séparées (comme la maquette OTP), avec détection du code copié depuis l'e-mail. */
class OtpView extends FrameLayout {
    final int n;
    final TextView[] cases;
    final EditText saisie;
    static String derniereCollee = "";
    private boolean sansAuto = false;

    OtpView(Context c, int n, Runnable complet) {
        super(c);
        this.n = n;
        cases = new TextView[n];
        LinearLayout ligne = new LinearLayout(c);
        ligne.setGravity(Gravity.CENTER);
        for (int i = 0; i < n; i++) {
            TextView t = new TextView(c);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(22);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(U.TEXTE);
            cases[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(c, 42), U.dp(c, 52));
            lp.setMargins(U.dp(c, 4), 0, U.dp(c, 4), 0);
            ligne.addView(t, lp);
        }
        saisie = new EditText(c);
        saisie.setInputType(InputType.TYPE_CLASS_NUMBER);
        saisie.setFilters(new InputFilter[]{new InputFilter.LengthFilter(n)});
        saisie.setBackground(null);
        saisie.setCursorVisible(false);
        saisie.setTextColor(0x00000000);
        addView(ligne, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        addView(saisie, new FrameLayout.LayoutParams(-1, U.dp(c, 52), Gravity.CENTER));
        maj();
        saisie.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int d, int e) { }
            public void onTextChanged(CharSequence a, int b, int d, int e) { }
            public void afterTextChanged(Editable e) {
                maj();
                if (e.length() == OtpView.this.n && complet != null && !sansAuto) complet.run();
            }
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Code détecté dans la notification Gmail de « Lyly Dating » : remplissage puis validation automatiques
        CodeRecu.abonne = c -> set(c);
        String deja = CodeRecu.prendre();
        if (deja != null) post(() -> set(deja));
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        CodeRecu.abonne = null;
    }

    String code() { return saisie.getText().toString(); }

    void set(String s) {
        saisie.setText(s);
        saisie.setSelection(saisie.length());
    }

    void vider() { saisie.setText(""); }

    private void maj() {
        String v = code();
        Context c = getContext();
        for (int i = 0; i < n; i++) {
            cases[i].setText(i < v.length() ? String.valueOf(v.charAt(i)) : "");
            GradientDrawable g = U.fond(0x22FFFFFF, U.dp(c, 12));
            boolean actif = i == v.length();
            g.setStroke(U.dp(c, actif ? 2 : 1), actif ? U.ROSE : 0x88F2C6B8);
            cases[i].setBackground(g);
        }
    }

    /** Premier nombre de n chiffres isolé dans un texte (ex : le corps de l'e-mail copié). */
    static String extraire(CharSequence t, int n) {
        if (t == null) return null;
        Matcher m = Pattern.compile("(?<!\\d)\\d{" + n + "}(?!\\d)").matcher(t);
        return m.find() ? m.group() : null;
    }

    /** Remplit les cases avec le code présent dans le presse-papiers. force=false : ignore un code déjà collé. */
    boolean coller(Activity a, boolean force) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return false;
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return false;
            // Détection automatique : seulement un code copié il y a moins de 30 min (durée de validité du code)
            if (!force && android.os.Build.VERSION.SDK_INT >= 26) {
                long copie = d.getDescription().getTimestamp();
                if (copie > 0 && System.currentTimeMillis() - copie > 30 * 60 * 1000L) return false;
            }
            String code = extraire(d.getItemAt(0).coerceToText(a), n);
            if (code == null || (!force && code.equals(derniereCollee))) return false;
            derniereCollee = code;
            // Remplissage automatique sans envoi : on valide soi-même (évite d'envoyer un code étranger copié par erreur)
            sansAuto = !force;
            set(code);
            sansAuto = false;
            return true;
        } catch (Exception e) { return false; }
    }
}
