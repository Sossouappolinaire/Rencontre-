package bj.rencontre;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import org.json.*;

/** Paramètres du membre : version installée et mise à jour téléchargeable. */
public class ParametresActivity extends Activity {
    LinearLayout zone;
    final Handler h = new Handler(Looper.getMainLooper());
    DownloadManager dm;
    JSONObject maj;
    long dlId = -1, taille = 0, dernierT, dernierB, debut;
    float vitesse = 0f;
    boolean pret = false, enCours = false;
    View barre, reste;
    TextView pct, vit, tRestant, tStatut, tTemps;
    Button bouton;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.fenetre(this);
        dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = U.col(this);
        sv.addView(root);
        setContentView(sv);
        root.addView(U.tv(this, "⚙ Paramètres", 22, true));
        U.add(root, U.retour(this));
        root.addView(U.tv(this, "\nMon compte", 16, true));
        U.PBtn vid = U.inscription(this, "🎥 Ma vidéo de vérification");
        vid.setOnClickListener(v -> startActivity(new Intent(this, VideoActivity.class)));
        U.add(root, vid);
        U.PBtn out = U.btn(this, "Déconnexion");
        out.setOnClickListener(v -> U.deconnecter(this));
        U.add(root, out);
        root.addView(U.tv(this, "\nMise à jour de l'application", 16, true));
        zone = new LinearLayout(this);
        zone.setOrientation(LinearLayout.VERTICAL);
        root.addView(zone);
        verifier();
    }

    @Override protected void onDestroy() { super.onDestroy(); h.removeCallbacksAndMessages(null); }

    long versionCode() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    void verifier() {
        zone.removeAllViews();
        zone.addView(U.tv(this, "Version installée : " + versionName() + "\nRecherche d'une nouvelle version…", 14, false));
        Api.bg(() -> {
            try {
                JSONArray a = new JSONArray(Api.get("/rest/v1/app_versions?select=version_code,version_name,notes,apk_url,sha256,taille_octets&order=version_code.desc&limit=1"));
                runOnUiThread(() -> {
                    zone.removeAllViews();
                    JSONObject o = a.optJSONObject(0);
                    if (o == null || o.optLong("version_code") <= versionCode()) {
                        zone.addView(U.tv(this, "✔ Tu as la dernière version (" + versionName() + ").", 15, false));
                        U.PBtn rf = U.pulse(this, "Vérifier à nouveau");
                        rf.setOnClickListener(v -> verifier());
                        U.add(zone, rf);
                    } else carte(o);
                });
            } catch (Exception e) {
                U.toast(this, "Erreur réseau.");
                runOnUiThread(() -> {
                    zone.removeAllViews();
                    U.PBtn rf = U.pulse(this, "Réessayer");
                    rf.setOnClickListener(v -> verifier());
                    U.add(zone, rf);
                });
            }
        });
    }

    View tuile(String label, String valeur, TextView[] ref) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.fond(0x1AFFFFFF, U.dp(this, 16)));
        int p = U.dp(this, 8);
        t.setPadding(p, p * 2, p, p * 2);
        TextView l = U.tv(this, label, 12, true);
        l.setTextColor(U.MUTED);
        l.setGravity(Gravity.CENTER);
        TextView v = U.tv(this, valeur, 14, true);
        v.setGravity(Gravity.CENTER);
        t.addView(l);
        t.addView(v);
        ref[0] = v;
        return t;
    }

    void barre(float f) {
        f = Math.max(0f, Math.min(1f, f));
        ((LinearLayout.LayoutParams) barre.getLayoutParams()).weight = f * 1000;
        ((LinearLayout.LayoutParams) reste.getLayoutParams()).weight = 1000 - f * 1000;
        barre.requestLayout();
    }

    void carte(JSONObject o) {
        maj = o;
        taille = o.optLong("taille_octets", 0);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawableHelper.carte(c, this);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView ico = U.tv(this, "📦", 34, false);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(U.fond(0x1AFFFFFF, U.dp(this, 18)));
        top.addView(ico, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(U.dp(this, 12), 0, 0, 0);
        tt.addView(U.tv(this, "Lyly Dating " + o.optString("version_name"), 18, true));
        TextView sz = U.tv(this, taille > 0 ? fmt(taille) : "Nouvelle version disponible", 13, false);
        sz.setTextColor(U.MUTED);
        tt.addView(sz);
        top.addView(tt);
        c.addView(top);
        String notes = o.isNull("notes") ? "" : o.optString("notes");
        if (!notes.isEmpty()) {
            TextView n = U.tv(this, "\n" + notes, 13, false);
            n.setTextColor(U.MUTED);
            c.addView(n);
        }
        LinearLayout piste = new LinearLayout(this);
        piste.setWeightSum(1000);
        piste.setBackground(U.fond(0x33FFFFFF, U.dp(this, 8)));
        barre = new View(this);
        barre.setBackground(U.degrade(0xFFD81E5B, 0xFFFF5C8A, U.dp(this, 8)));
        reste = new View(this);
        piste.addView(barre, new LinearLayout.LayoutParams(0, U.dp(this, 10), 0));
        piste.addView(reste, new LinearLayout.LayoutParams(0, U.dp(this, 10), 1000));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.setMargins(0, U.dp(this, 16), 0, U.dp(this, 6));
        c.addView(piste, pl);
        LinearLayout st = new LinearLayout(this);
        pct = U.tv(this, "0%", 13, false);
        vit = U.tv(this, "", 13, false);
        vit.setGravity(Gravity.END);
        st.addView(pct, new LinearLayout.LayoutParams(0, -2, 1));
        st.addView(vit, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(st);
        LinearLayout tl = new LinearLayout(this);
        TextView[] r = new TextView[1];
        View t1 = tuile("Restant", taille > 0 ? fmt(taille) : "—", r); tRestant = r[0];
        View t2 = tuile("Statut", "Prêt", r); tStatut = r[0];
        View t3 = tuile("Temps", "—", r); tTemps = r[0];
        for (View t : new View[]{t1, t2, t3}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(U.dp(this, 3), U.dp(this, 12), U.dp(this, 3), U.dp(this, 6));
            tl.addView(t, lp);
        }
        c.addView(tl);
        bouton = U.connexion(this, "⬇ Télécharger la mise à jour");
        bouton.setOnClickListener(v -> { if (pret) installer(); else if (!enCours) telecharger(); });
        U.add(c, bouton);
        U.add(zone, c);
    }

    void telecharger() {
        String url = maj.optString("apk_url");
        String nom = "lyly-" + maj.optLong("version_code") + ".apk";
        File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), nom);
        if (f.exists()) f.delete();
        try {
            DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url));
            rq.setTitle("Lyly Dating " + maj.optString("version_name"));
            rq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            rq.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, nom);
            dlId = dm.enqueue(rq);
        } catch (Exception e) { U.toast(this, "Téléchargement impossible."); return; }
        enCours = true;
        bouton.setEnabled(false);
        bouton.setText("Téléchargement…");
        tStatut.setText("Téléchargement");
        debut = dernierT = System.currentTimeMillis();
        dernierB = 0; vitesse = 0f;
        h.post(sondage);
    }

    final Runnable sondage = new Runnable() {
        @Override public void run() {
            try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(dlId))) {
                if (c != null && c.moveToFirst()) {
                    int s = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (tot <= 0) tot = taille;
                    long now = System.currentTimeMillis();
                    if (now - dernierT >= 1000) {
                        float v = (got - dernierB) * 1000f / (now - dernierT);
                        vitesse = vitesse == 0f ? v : 0.6f * vitesse + 0.4f * v;
                        dernierT = now; dernierB = got;
                    }
                    if (tot > 0) {
                        float f = (float) got / tot;
                        barre(f);
                        pct.setText(Math.round(f * 100) + "%");
                        tRestant.setText(fmt(Math.max(0, tot - got)));
                        if (vitesse > 0) tTemps.setText(Math.round((tot - got) / vitesse) + " sec");
                    }
                    vit.setText(vitesse > 0 ? String.format(java.util.Locale.FRANCE, "%.2f Mo/s", vitesse / 1048576f) : "");
                    if (s == DownloadManager.STATUS_SUCCESSFUL) { verifierFichier(); return; }
                    if (s == DownloadManager.STATUS_FAILED) { echec("Échec du téléchargement."); return; }
                }
            } catch (Exception ignore) { }
            h.postDelayed(this, 500);
        }
    };

    void echec(String m) {
        enCours = false; pret = false;
        tStatut.setText("Échec");
        bouton.setEnabled(true);
        bouton.setText("⬇ Réessayer");
        U.toast(this, m);
    }

    /** Contrôle l'empreinte SHA-256 publiée par l'administrateur avant toute installation. */
    void verifierFichier() {
        tStatut.setText("Vérification");
        barre(1f); pct.setText("100%");
        Api.bg(() -> {
            try {
                Uri u = dm.getUriForDownloadedFile(dlId);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                }
                StringBuilder sb = new StringBuilder();
                for (byte x : md.digest()) sb.append(String.format("%02x", x));
                String attendu = maj.optString("sha256").toLowerCase();
                if (!attendu.isEmpty() && !attendu.equals(sb.toString())) {
                    dm.remove(dlId);
                    runOnUiThread(() -> echec("Fichier corrompu : téléchargement annulé."));
                    return;
                }
                runOnUiThread(() -> {
                    enCours = false; pret = true;
                    tStatut.setText("Terminé");
                    tRestant.setText("0 Mo");
                    bouton.setEnabled(true);
                    bouton.setText("Installer");
                    installer();
                });
            } catch (Exception e) { runOnUiThread(() -> echec("Vérification impossible.")); }
        });
    }

    void installer() {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                U.toast(this, "Autorise Lyly Dating à installer des applications, puis reviens appuyer sur « Installer ».");
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(dm.getUriForDownloadedFile(dlId), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { U.toast(this, "Installation impossible."); }
    }

    static String fmt(long o) { return String.format(java.util.Locale.FRANCE, "%.1f Mo", o / 1048576.0); }

    /** Fond de carte arrondi avec contour or rosé. */
    static class GradientDrawableHelper {
        static void carte(LinearLayout c, Context x) {
            android.graphics.drawable.GradientDrawable g = U.fond(0x33000000, U.dp(x, 24));
            g.setStroke(U.dp(x, 1), 0x55F2C6B8);
            c.setBackground(g);
            int p = U.dp(x, 16);
            c.setPadding(p, p, p, p);
        }
    }
}
