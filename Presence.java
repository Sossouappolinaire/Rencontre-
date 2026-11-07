package bj.rencontre;

import android.os.Handler;
import android.os.Looper;

/** Signale toutes les 30 s que le membre est en ligne ; hors ligne quand l'écran se ferme. */
public class Presence {
    static final Handler H = new Handler(Looper.getMainLooper());
    static String id;
    static final Runnable TIC = new Runnable() {
        @Override public void run() {
            if (id == null) return;
            final String m = id;
            Api.bg(() -> { try { Api.call("POST", "/rest/v1/rpc/ping", "{\"p_id\":\"" + m + "\"}", null); } catch (Exception ignore) { } });
            H.postDelayed(this, 30000);
        }
    };

    static final Runnable DEPART = Presence::stop;

    /** À appeler dans onPause : hors ligne après 3 s si aucun autre écran de l'appli ne reprend la main. */
    static void leave() { H.removeCallbacks(DEPART); H.postDelayed(DEPART, 3000); }

    static void start(String membre) {
        if (membre == null) return;
        H.removeCallbacks(DEPART);
        H.removeCallbacks(TIC);
        id = membre;
        TIC.run();
    }

    static void stop() {
        H.removeCallbacks(TIC);
        H.removeCallbacks(DEPART);
        final String m = id;
        id = null;
        if (m == null) return;
        Api.bg(() -> { try { Api.call("POST", "/rest/v1/rpc/hors_ligne", "{\"p_id\":\"" + m + "\"}", null); } catch (Exception ignore) { } });
    }
}
