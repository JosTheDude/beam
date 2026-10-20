package com.barnav.vidsrctv;

import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lets the home page fetch metadata APIs that don't send CORS headers (IMDb suggestions).
 * Exposed as window.Native; results come back through window.__nativeCb(id, ok, body).
 */
public class Bridge {
    // The interface is visible to every page the WebView loads, so keep it to read-only metadata hosts.
    private static final String[] HOSTS = {"v3.sg.media-imdb.com", "v3-cinemeta.strem.io"};

    private final WebView web;

    Bridge(WebView web) { this.web = web; }

    @JavascriptInterface
    public void get(String url, int id) {
        String host = Uri.parse(url).getHost();
        boolean allowed = false;
        for (String h : HOSTS) if (h.equals(host)) allowed = true;
        if (!allowed) { reply(id, false, "host not allowed"); return; }

        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    reply(id, true, out.toString("UTF-8"));
                }
            } catch (Exception e) {
                reply(id, false, String.valueOf(e.getMessage()));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void reply(int id, boolean ok, String body) {
        String js = "window.__nativeCb&&__nativeCb(" + id + "," + ok + "," + JSONObject.quote(body) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }
}
