package bj.rencontre;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Lit uniquement les notifications de Gmail dont l'expéditeur est « Lyly Dating »
 * et en extrait le code à 6 chiffres. Toute autre notification est ignorée sans être lue plus loin.
 */
public class CodeNotifListener extends NotificationListenerService {
    private static final String[] GMAIL = {"com.google.android.gm", "com.google.android.gm.lite"};
    private static final String EXPEDITEUR = "lyly dating";

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            boolean gmail = false;
            for (String p : GMAIL) if (p.equals(sbn.getPackageName())) gmail = true;
            if (!gmail) return;
            Bundle x = sbn.getNotification().extras;
            if (x == null) return;
            StringBuilder tout = new StringBuilder();
            boolean expediteurOk = false;
            CharSequence titre = x.getCharSequence(Notification.EXTRA_TITLE);
            if (titre != null) {
                tout.append(titre).append('\n');
                if (titre.toString().toLowerCase().contains(EXPEDITEUR)) expediteurOk = true;
            }
            CharSequence texte = x.getCharSequence(Notification.EXTRA_TEXT);
            if (texte != null) tout.append(texte).append('\n');
            CharSequence grand = x.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (grand != null) tout.append(grand).append('\n');
            // Notifications groupées : une ligne par e-mail, de la forme « Expéditeur  objet »
            CharSequence[] lignes = x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lignes != null) for (CharSequence l : lignes) {
                if (l == null) continue;
                if (l.toString().toLowerCase().startsWith(EXPEDITEUR)) expediteurOk = true;
                tout.append(l).append('\n');
            }
            if (!expediteurOk) return;
            String code = OtpView.extraire(tout, 6);
            if (code != null) CodeRecu.recu(code);
        } catch (Exception ignore) { }
    }
}
