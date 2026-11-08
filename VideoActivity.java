package bj.rencontre;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import java.io.*;
import org.json.*;

public class VideoActivity extends Activity {
    TextView etat;
    U.PBtn go;
    Uri sortie; // fichier où la caméra écrit la vidéo (certains téléphones ne renvoient rien sinon)

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        if (b != null && b.getString("sortie") != null) sortie = Uri.parse(b.getString("sortie"));
        ScrollView sv = new ScrollView(this);
        LinearLayout l = U.col(this);
        sv.addView(l);
        setContentView(sv);
        l.addView(U.tv(this, "Vidéo de vérification", 22, true));
        l.addView(U.tv(this, "\nFilme-toi en direct pendant 20 secondes (l'import depuis la galerie n'est pas possible).", 16, false));
        go = U.valider(this, "🎥 Démarrer l'enregistrement");
        go.setOnClickListener(v -> demarrer());
        U.add(l, go);
        U.add(l, U.accueil(this));
        U.add(l, U.retour(this));
        etat = U.tv(this, "", 14, false);
        l.addView(etat);
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        if (sortie != null) o.putString("sortie", sortie.toString());
    }

    /** Vérifie que le compte peut envoyer sa vidéo (code validé) avant d'ouvrir la caméra. */
    void demarrer() {
        String id = U.id(this);
        if (id == null) { U.erreur(this, "Session expirée. Reviens à l'accueil et reconnecte-toi."); return; }
        go.setEnabled(false);
        Api.bg(() -> {
            String s;
            try {
                JSONArray a = new JSONArray(Api.get("/rest/v1/membres?id=eq." + id + "&select=statut"));
                s = a.length() > 0 ? a.getJSONObject(0).optString("statut") : "";
            } catch (Exception e) {
                U.erreur(this, "Impossible de vérifier ton compte : " + U.msg(e, "erreur réseau."));
                runOnUiThread(() -> go.setEnabled(true));
                return;
            }
            if (s.isEmpty() || s.equals("refuse")) {
                U.erreur(this, "Compte introuvable ou refusé : impossible d'enregistrer la vidéo.");
                runOnUiThread(() -> go.setEnabled(true));
                return;
            }
            runOnUiThread(() -> { go.setEnabled(true); filmer(); });
        });
    }

    void filmer() {
        Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        i.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20);
        i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        sortie = null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, "lyly-" + System.currentTimeMillis() + ".mp4");
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LylyDating");
            sortie = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        } catch (Exception e) { sortie = null; }
        if (sortie != null) {
            i.putExtra(MediaStore.EXTRA_OUTPUT, sortie);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("video", sortie));
        }
        try { startActivityForResult(i, 2); }
        catch (Exception e) { supprimerSortie(); U.erreur(this, "Aucune application caméra trouvée sur ce téléphone."); }
    }

    void supprimerSortie() {
        try { if (sortie != null) getContentResolver().delete(sortie, null, null); } catch (Exception ignore) { }
        sortie = null;
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq != 2) return;
        if (rs != RESULT_OK) {
            supprimerSortie();
            U.dialogue(this, "Enregistrement annulé", "La vidéo n'a pas été enregistrée. Appuie sur « Démarrer l'enregistrement » pour recommencer.");
            return;
        }
        Uri u = (d != null && d.getData() != null) ? d.getData() : sortie;
        if (u == null) {
            U.erreur(this, "La caméra n'a pas renvoyé la vidéo. Réessaie, ou utilise l'application caméra par défaut du téléphone.");
            return;
        }
        go.start("Envoi");
        etat.setText("Envoi de la vidéo…");
        String id = U.id(this);
        Api.bg(() -> {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                if (in == null) throw new IOException("Vidéo introuvable sur le téléphone.");
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                if (o.size() == 0) throw new IOException("La vidéo enregistrée est vide. Réessaie.");
                Api.upload(id + ".mp4", o.toByteArray(), f -> runOnUiThread(() -> go.setProgress(f)));
                String url = Config.URL + "/storage/v1/object/public/videos/" + id + ".mp4";
                String r = Api.call("POST", "/rest/v1/rpc/soumettre_video_membre",
                        new JSONObject().put("p_id", id).put("p_url", url).toString(), null);
                if (!r.trim().equals("true")) throw new IOException("Compte introuvable ou refusé : la vidéo n'a pas pu être enregistrée.");
                runOnUiThread(() -> { supprimerSortie(); go.succes(this::finish); });
            } catch (Exception e) {
                U.erreur(this, "Échec de l'envoi de la vidéo :\n" + U.msg(e, "erreur inconnue."));
                runOnUiThread(() -> { go.stop(); etat.setText(""); });
            }
        });
    }
}
