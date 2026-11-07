package bj.rencontre;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
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

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }
    static TextView tv(Context c, String t, int sp, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t); v.setTextSize(sp); v.setTextColor(0xFF222222);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }
    static EditText et(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        return e;
    }
    static GradientDrawable fond(int color, int rad) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(rad);
        return g;
    }
    static Button btn(Context c, String t) {
        Button b = new Button(c);
        b.setText(t); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackground(fond(0xFF075E54, dp(c, 24)));
        return b;
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
