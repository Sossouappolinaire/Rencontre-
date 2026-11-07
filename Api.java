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

    static void upload(String name, byte[] data) throws IOException {
        Request r = new Request.Builder().url(Config.URL + "/storage/v1/object/videos/" + name)
                .header("apikey", Config.KEY).header("Authorization", "Bearer " + Config.KEY)
                .header("x-upsert", "true")
                .post(RequestBody.create(data, MediaType.parse("video/mp4"))).build();
        try (Response x = C.newCall(r).execute()) {
            if (!x.isSuccessful()) throw new IOException(x.body().string());
        }
    }
    static void bg(Runnable r) { new Thread(r).start(); }
}
