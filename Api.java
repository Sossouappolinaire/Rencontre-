package bj.rencontre;

import java.io.IOException;
import okhttp3.*;

public class Api {
    static final OkHttpClient C = new OkHttpClient();
    static final MediaType J = MediaType.parse("application/json");

    static String call(String m, String path, String json, String prefer) throws IOException {
        Request.Builder b = new Request.Builder().url(Config.URL + path)
                .header("apikey", Config.KEY).header("Authorization", "Bearer " + Config.KEY);
        if (prefer != null) b.header("Prefer", prefer);
        if (m.equals("GET")) b.get();
        else b.method(m, RequestBody.create(json == null ? "" : json, J));
        try (Response r = C.newCall(b.build()).execute()) {
            String s = r.body().string();
            if (!r.isSuccessful()) throw new IOException(s);
            return s;
        }
    }
    static String get(String p) throws IOException { return call("GET", p, null, null); }
    static String post(String p, String j) throws IOException { return call("POST", p, j, "return=representation"); }
    static void patch(String p, String j) throws IOException { call("PATCH", p, j, null); }

    interface Prog { void on(float f); }

    static void upload(String name, byte[] data) throws IOException { upload(name, data, null); }

    static void upload(String name, byte[] data, Prog p) throws IOException {
        upload("videos", name, data, "video/mp4", true, p);
    }

    static void upload(String bucket, String name, byte[] data, String mime, boolean upsert, Prog p) throws IOException {
        final MediaType mt = MediaType.parse(mime);
        RequestBody body = new RequestBody() {
            @Override public MediaType contentType() { return mt; }
            @Override public long contentLength() { return data.length; }
            @Override public void writeTo(okio.BufferedSink s) throws IOException {
                int off = 0, last = -1;
                while (off < data.length) {
                    int n = Math.min(8192, data.length - off);
                    s.write(data, off, n);
                    off += n;
                    int pc = (int) (100L * off / data.length);
                    if (p != null && pc != last) { last = pc; p.on(pc / 100f); }
                }
            }
        };
        Request r = new Request.Builder().url(Config.URL + "/storage/v1/object/" + bucket + "/" + name)
                .header("apikey", Config.KEY).header("Authorization", "Bearer " + Config.KEY)
                .header("x-upsert", upsert ? "true" : "false")
                .post(body).build();
        try (Response x = C.newCall(r).execute()) {
            if (!x.isSuccessful()) throw new IOException(x.body().string());
        }
    }
    /** Télécharge une URL et écrit son contenu dans out (sans tout garder en mémoire). */
    static void download(String url, java.io.OutputStream out) throws IOException {
        Request r = new Request.Builder().url(url).get().build();
        try (Response x = C.newCall(r).execute()) {
            if (!x.isSuccessful()) throw new IOException("Téléchargement impossible (code " + x.code() + ").");
            try (java.io.InputStream in = x.body().byteStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }
    static void bg(Runnable r) { new Thread(r).start(); }
}
