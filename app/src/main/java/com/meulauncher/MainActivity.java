package com.meulauncher;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private WebView web;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private String httpGet(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        }
    }

    private File versionDir(String id) {
        return new File(getExternalFilesDir(null), "versions/" + id);
    }

    class Bridge {
        @JavascriptInterface
        public String getUser() {
            return getPreferences(MODE_PRIVATE).getString("user", "Steve");
        }

        @JavascriptInterface
        public void setUser(String u) {
            getPreferences(MODE_PRIVATE).edit().putString("user", u).apply();
        }

        @JavascriptInterface
        public boolean isInstalled(String id) {
            return new File(versionDir(id), id + ".jar").exists();
        }

        @JavascriptInterface
        public void loadVersions() {
            pool.execute(() -> {
                try {
                    js("onVersions(" + JSONObject.quote(httpGet(MANIFEST)) + ")");
                } catch (Exception e) {
                    js("onError(" + JSONObject.quote(String.valueOf(e)) + ")");
                }
            });
        }

        // Baixa o JSON da versão + o client.jar. Falta: libraries e assets (etapa 2b).
        @JavascriptInterface
        public void download(String id, String versionUrl) {
            pool.execute(() -> {
                try {
                    String vjson = httpGet(versionUrl);
                    JSONObject client = new JSONObject(vjson)
                            .getJSONObject("downloads").getJSONObject("client");
                    File dir = versionDir(id);
                    dir.mkdirs();
                    try (FileOutputStream fo = new FileOutputStream(new File(dir, id + ".json"))) {
                        fo.write(vjson.getBytes("UTF-8"));
                    }
                    HttpURLConnection c = (HttpURLConnection) new URL(client.getString("url")).openConnection();
                    long total = c.getContentLengthLong();
                    long done = 0;
                    int last = -1;
                    try (InputStream in = c.getInputStream();
                         FileOutputStream fo = new FileOutputStream(new File(dir, id + ".jar"))) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fo.write(buf, 0, n);
                            done += n;
                            int pct = total > 0 ? (int) (done * 100 / total) : 0;
                            if (pct != last) { last = pct; js("onProgress(" + pct + ")"); }
                        }
                    }
                    js("onDone(" + JSONObject.quote(id) + ")");
                } catch (Exception e) {
                    js("onError(" + JSONObject.quote(String.valueOf(e)) + ")");
                }
            });
        }

        @JavascriptInterface
        public void play(String id) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Falta o Java runtime + renderizador (etapas 4 a 6)", Toast.LENGTH_LONG).show());
        }
    }
                      }
