package bj.rencontre;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/** Dernier code reçu par e-mail, détecté dans la notification Gmail de « Lyly Dating ». */
final class CodeRecu {
    private static volatile String code;
    private static volatile long quand;
    static volatile Consumer<String> abonne;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** Appelé par le service de notifications : garde le code et prévient l'écran ouvert. */
    static void recu(String c) {
        code = c;
        quand = System.currentTimeMillis();
        Consumer<String> a = abonne;
        if (a != null) UI.post(() -> a.accept(c));
    }

    /** Code détecté il y a moins de 30 min (durée de validité), consommé à la lecture ; sinon null. */
    static String prendre() {
        String c = code;
        if (c == null || System.currentTimeMillis() - quand > 30 * 60 * 1000L) return null;
        code = null;
        return c;
    }

    static boolean actif(Context c) {
        String l = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return l != null && l.contains(c.getPackageName());
    }

    /** Bouton d'activation de la détection automatique, ou une ligne « activée » si c'est déjà fait. */
    static View activation(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, U.dp(a, 12), 0, 0);
        if (actif(a)) {
            TextView t = U.tv(a, "✔ Détection automatique activée (e-mails « Lyly Dating » dans Gmail)", 12, false);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(0xFF22C55E);
            l.addView(t);
            return l;
        }
        Button b = U.btn(a, "🔔 Remplir le code automatiquement");
        b.setTextSize(12);
        b.setOnClickListener(v -> new android.app.AlertDialog.Builder(a)
                .setTitle("Détection automatique du code")
                .setMessage("Lyly Dating lira uniquement la notification Gmail dont l'expéditeur est « Lyly Dating » pour en extraire ton code à 6 chiffres. "
                        + "Les autres notifications sont ignorées et rien n'est enregistré.\n\n"
                        + "Dans l'écran suivant, active « Lyly Dating ».\n\n"
                        + "Si le réglage est grisé (Android 13 et plus) : Paramètres > Applications > Lyly Dating > menu ⋮ > « Autoriser les paramètres restreints », puis recommence.")
                .setPositiveButton("Continuer", (d, w) -> {
                    try { a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (Exception e) { U.toast(a, "Réglage indisponible sur ce téléphone."); }
                })
                .setNegativeButton("Plus tard", null).show());
        l.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }
}
