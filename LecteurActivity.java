package bj.rencontre;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;

/** Lit une vidéo déjà enregistrée sur le téléphone de l'administrateur. */
public class LecteurActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        LinearLayout l = U.col(this);
        l.addView(U.tv(this, String.valueOf(getIntent().getStringExtra("titre")), 18, true));
        VideoView vv = new VideoView(this);
        l.addView(vv, new LinearLayout.LayoutParams(-1, U.dp(this, 420)));
        U.add(l, U.retour(this));
        setContentView(l);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(vv);
        vv.setMediaController(mc);
        vv.setOnErrorListener((m, w, e) -> {
            U.erreur(this, "Lecture impossible ici. La vidéo est bien enregistrée sur ton téléphone.");
            return true;
        });
        vv.setVideoURI(Uri.parse(getIntent().getStringExtra("uri")));
        vv.start();
    }
}
