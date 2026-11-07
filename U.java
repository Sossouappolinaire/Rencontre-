package bj.rencontre;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.net.Uri;
import android.util.Base64;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class U {
    static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density); }
    static String id(Context c) { return c.getSharedPreferences("s", 0).getString("id", null); }
    static void setId(Context c, String v) { c.getSharedPreferences("s", 0).edit().putString("id", v).apply(); }

    /** Déconnexion : efface la session, le PIN et le statut en ligne. */
    static void deconnecter(Activity a) {
        Presence.stop();
        LockActivity.effacer(a);
        setId(a, null);
        a.startActivity(new android.content.Intent(a, MainActivity.class).addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        a.finish();
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }
    static TextView tv(Context c, String t, int sp, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t); v.setTextSize(sp); v.setTextColor(TEXTE);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }
    static EditText et(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0x99E8C4CB);
        e.setTextColor(TEXTE);
        champ(e);
        return e;
    }
    static GradientDrawable fond(int color, int rad) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(rad);
        return g;
    }
    // ---------- Thème Lyly Dating (bordeaux, rose, or rosé) ----------
    static final int TEXTE = 0xFFFFF1F3, MUTED = 0xFFE8C4CB, OR = 0xFFF2C6B8, ROSE = 0xFFE8335F;
    static final int FX_NONE = 0, FX_FILL = 1, FX_SHIMMER = 2, FX_PULSE = 3;

    static void fenetre(Activity a) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14040A, 0xFF3A0A16});
        a.getWindow().setBackgroundDrawable(g);
        a.getWindow().setStatusBarColor(0xFF14040A);
        a.getWindow().setNavigationBarColor(0xFF3A0A16);
    }

    static void champ(View v) {
        GradientDrawable g = fond(0x22FFFFFF, dp(v.getContext(), 14));
        g.setStroke(dp(v.getContext(), 1), 0x55F2C6B8);
        v.setBackground(g);
        int p = dp(v.getContext(), 12);
        v.setPadding(p, p, p, p);
    }

    static void rond(View v) {
        GradientDrawable o = new GradientDrawable();
        o.setShape(GradientDrawable.OVAL);
        o.setColor(0x33FFFFFF);
        o.setStroke(dp(v.getContext(), 2), OR);
        v.setBackground(o);
        v.setClipToOutline(true);
    }

    /** En-tête façon logo : « Lyly » en script rose doré, DATING espacé, cœur. */
    static View header(Context c) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(0, dp(c, 8), 0, dp(c, 12));
        TextView ly = new TextView(c);
        ly.setText("Lyly");
        ly.setTextSize(60);
        ly.setGravity(Gravity.CENTER);
        ly.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        ly.getPaint().setShader(new LinearGradient(0, 0, 0, dp(c, 78), 0xFFFFE6DC, 0xFFE57C93, Shader.TileMode.CLAMP));
        ly.setShadowLayer(dp(c, 12), 0, 0, 0x99E8335F);
        h.addView(ly);
        TextView d = new TextView(c);
        d.setText("DATING");
        d.setTextSize(15);
        d.setLetterSpacing(0.45f);
        d.setGravity(Gravity.CENTER);
        d.setTextColor(0xFFF2D4CC);
        h.addView(d);
        TextView hr = new TextView(c);
        hr.setText("──────  ♥  ──────");
        hr.setTextSize(12);
        hr.setGravity(Gravity.CENTER);
        hr.setTextColor(ROSE);
        h.addView(hr);
        TextView tag = new TextView(c);
        tag.setText("Des rencontres vraies, près de chez toi");
        tag.setTextSize(12);
        tag.setGravity(Gravity.CENTER);
        tag.setTextColor(MUTED);
        h.addView(tag);
        return h;
    }

    static GradientDrawable degrade(int a, int b, int rad) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{a, b});
        g.setCornerRadius(rad);
        return g;
    }

    /**
     * Bouton avec un effet propre à son action :
     *  FX_FILL    = barre de progression qui se remplit (téléchargement)
     *  FX_SHIMMER = reflet lumineux qui balaie le bouton
     *  FX_PULSE   = battement de cœur
     *  FX_NONE    = simple retour tactile (le bouton Retour glisse en plus)
     */
    static class PBtn extends Button {
        final int rad, fx;
        float prog = 0f;
        boolean busy = false;
        CharSequence txt;
        String label = "";
        ValueAnimator va;
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clip = new Path();
        final RectF rf = new RectF();

        PBtn(Context c, String t, int a, int b, int textColor, int fx, boolean ghost) {
            super(c);
            this.fx = fx;
            rad = dp(c, 24);
            setText(t); setAllCaps(false); setTextColor(textColor);
            setTypeface(null, Typeface.BOLD);
            setMinHeight(dp(c, 50));
            GradientDrawable contenu;
            if (ghost) {
                contenu = fond(0x1AFFFFFF, rad);
                contenu.setStroke(dp(c, 1), OR);
            } else contenu = degrade(a, b, rad);
            setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), contenu, fond(Color.WHITE, rad)));
            pt.setColor(Color.argb(255, (int) (Color.red(a) * 0.65f), (int) (Color.green(a) * 0.65f), (int) (Color.blue(a) * 0.65f)));
            setOnTouchListener((v, e) -> {
                int ac = e.getAction();
                if (ac == MotionEvent.ACTION_DOWN) animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return false;
            });
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (fx == FX_FILL && prog > 0f) {
                rf.set(0, 0, w, h);
                clip.reset();
                clip.addRoundRect(rf, rad, rad, Path.Direction.CW);
                cv.save();
                cv.clipPath(clip);
                cv.drawRect(0, 0, w * prog, h, pt);
                cv.restore();
            } else if (fx == FX_SHIMMER && busy) {
                rf.set(0, 0, w, h);
                clip.reset();
                clip.addRoundRect(rf, rad, rad, Path.Direction.CW);
                float bw = w * 0.4f, x = -bw + (w + bw) * prog;
                ps.setShader(new LinearGradient(x, 0, x + bw, 0, new int[]{0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                cv.save();
                cv.clipPath(clip);
                cv.drawRect(x, 0, x + bw, h, ps);
                cv.restore();
            }
            super.onDraw(cv);
        }

        void anim(float a, float b, long ms, TimeInterpolator ip) {
            if (va != null) va.cancel();
            va = ValueAnimator.ofFloat(a, b);
            va.setDuration(ms);
            va.setInterpolator(ip);
            va.addUpdateListener(x -> { prog = (float) x.getAnimatedValue(); invalidate(); });
            va.start();
        }

        /** À appeler sur le thread UI. */
        void start(String lab) {
            if (busy) return;
            busy = true; txt = getText(); label = lab;
            setText(fx == FX_PULSE ? "♥ " + lab + "…" : lab + "…");
            setEnabled(false);
            if (fx == FX_FILL) anim(0f, 0.9f, 12000, new DecelerateInterpolator(2f));
            else if (fx == FX_SHIMMER) {
                anim(0f, 1f, 1100, new LinearInterpolator());
                va.setRepeatCount(ValueAnimator.INFINITE);
            } else if (fx == FX_PULSE) {
                if (va != null) va.cancel();
                va = ValueAnimator.ofFloat(1f, 1.07f);
                va.setDuration(420);
                va.setRepeatCount(ValueAnimator.INFINITE);
                va.setRepeatMode(ValueAnimator.REVERSE);
                va.addUpdateListener(x -> { float s = (float) x.getAnimatedValue(); setScaleX(s); setScaleY(s); });
                va.start();
            }
        }

        /** Progression réelle (0 à 1) pour FX_FILL, thread UI. */
        void setProgress(float f) {
            if (!busy || fx != FX_FILL) return;
            if (va != null) va.cancel();
            prog = Math.max(0f, Math.min(1f, f));
            setText(label + " " + Math.round(prog * 100) + " %");
            invalidate();
        }

        /** Termine l'effet par un ✔ puis exécute la suite. */
        void succes(Runnable apres) {
            if (!busy) { if (apres != null) apres.run(); return; }
            if (fx == FX_FILL) anim(prog, 1f, 250, new DecelerateInterpolator());
            else {
                if (va != null) va.cancel();
                setScaleX(1f); setScaleY(1f);
                animate().scaleX(1.12f).scaleY(1.12f).setDuration(120)
                        .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
            }
            setText("✔");
            postDelayed(() -> { stop(); if (apres != null) apres.run(); }, 380);
        }

        /** Remet le bouton à l'état normal. */
        void stop() {
            if (va != null) va.cancel();
            animate().cancel();
            prog = 0f; busy = false;
            setScaleX(1f); setScaleY(1f);
            if (txt != null) setText(txt);
            setEnabled(true);
            invalidate();
        }
    }

    // Inscription : rose vif, barre de progression
    static PBtn inscription(Context c, String t) { return new PBtn(c, t, 0xFFD81E5B, 0xFFFF5C8A, Color.WHITE, FX_FILL, false); }
    // Connexion : or rosé, reflet lumineux
    static PBtn connexion(Context c, String t) { return new PBtn(c, t, 0xFFF3C9B8, 0xFFD98C7A, 0xFF3A0A16, FX_SHIMMER, false); }
    // Validation : vert, barre de progression avec pourcentage
    static PBtn valider(Context c, String t) { return new PBtn(c, t, 0xFF16A34A, 0xFF22C55E, Color.WHITE, FX_FILL, false); }
    // Renvoyer / secondaire : prune, battement de cœur
    static PBtn pulse(Context c, String t) { return new PBtn(c, t, 0xFF8E2A6E, 0xFFC2417A, Color.WHITE, FX_PULSE, false); }
    // Bouton neutre : bordeaux, simple retour tactile
    static PBtn btn(Context c, String t) { return new PBtn(c, t, 0xFF7A1632, 0xFFA11D45, Color.WHITE, FX_NONE, false); }
    // Retour : contour or rosé, glisse vers la gauche puis disparaît
    static PBtn retour(Context c, String t, Runnable action) {
        PBtn b = new PBtn(c, t, 0xFF000000, 0xFF000000, OR, FX_NONE, true);
        b.setOnClickListener(v -> b.animate().translationX(-dp(c, 18)).alpha(0.25f).setDuration(150).withEndAction(action).start());
        return b;
    }
    static PBtn retour(Activity a) { return retour(a, "← Retour", a::finish); }

    static void add(LinearLayout l, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(l.getContext(), 6), 0, dp(l.getContext(), 6));
        l.addView(v, lp);
    }
    static Bitmap decode(Context c, Uri u, int max) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream i = c.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(i, null, o); }
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > max * 2) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        Bitmap b;
        try (InputStream i = c.getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(i, null, o); }
        float f = (float) max / Math.max(b.getWidth(), b.getHeight());
        if (f < 1) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * f), Math.round(b.getHeight() * f), true);
        return b;
    }
    static String enc(Bitmap b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 60, o);
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }
    static Bitmap b64(String s) {
        try {
            byte[] d = Base64.decode(s, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (Exception e) { return null; }
    }
    static void toast(Activity a, String m) {
        a.runOnUiThread(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
    }
    static String heure(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.substring(0, 19));
            return new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(d);
        } catch (Exception e) { return ""; }
    }
    static String drapeau(String code) {
        return new String(Character.toChars(0x1F1E6 + code.charAt(0) - 'A'))
                + new String(Character.toChars(0x1F1E6 + code.charAt(1) - 'A'));
    }
}
