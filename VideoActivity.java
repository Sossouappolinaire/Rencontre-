package bj.rencontre;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import java.io.*;

public class VideoActivity extends Activity {
    TextView etat;
    U.PBtn go;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        LinearLayout l = U.col(this);
        setContentView(l);
        l.addView(U.tv(this, "Vidéo de vérification", 22, true));
        l.addView(U.tv(this, "\nFilme-toi en direct pendant 20 secondes (l'import depuis la galerie n'est pas possible).", 16, false));
        go = U.valider(this, "🎥 Démarrer l'enregistrement");
        go.setOnClickListener(v -> {
            Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20);
            i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            try { startActivityForResult(i, 2); }
            catch (Exception e) { U.toast(this, "Aucune application caméra trouvée."); }
        });
        U.add(l, go);
        U.add(l, U.retour(this));
        etat = U.tv(this, "", 14, false);
        l.addView(etat);
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq != 2 || rs != RESULT_OK || d == null || d.getData() == null) return;
        Uri u = d.getData();
        go.start("Envoi");
        etat.setText("Envoi de la vidéo…");
        String id = U.id(this);
        Api.bg(() -> {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                Api.upload(id + ".mp4", o.toByteArray(), f -> runOnUiThread(() -> go.setProgress(f)));
                String url = Config.URL + "/storage/v1/object/public/videos/" + id + ".mp4";
                Api.patch("/rest/v1/membres?id=eq." + id,
                        "{\"statut\":\"attente_validation\",\"video_url\":\"" + url + "\"}");
                runOnUiThread(() -> go.succes(this::finish));
            } catch (Exception e) {
                U.toast(this, "Échec de l'envoi : " + e.getMessage());
                runOnUiThread(() -> { go.stop(); etat.setText(""); });
            }
        });
    }
}
