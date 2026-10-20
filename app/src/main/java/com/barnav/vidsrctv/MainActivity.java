package com.barnav.vidsrctv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private static final String HOME = "file:///android_asset/home.html";
    // Top-level navigation is only allowed to these hosts; everything else is treated as an ad redirect.
    private static final String[] ALLOWED_HOSTS = {"vidsrc.to", "vidsrc.me", "vidsrc.xyz", "vidsrc.net", "vidsrc.cc"};

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        cursor = new CursorView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cursor, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true); // so onCreateWindow fires and we can refuse popups
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // Desktop UA: the mobile site is worse on a big screen
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Bridge(web), "Native");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // Only police top-level navigations; the player itself lives in third-party iframes
                return req.isForMainFrame() && !isAllowed(req.getUrl());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, android.os.Message msg) {
                return false; // block popups / pop-unders
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
                fullscreenView = view;
                fullscreenCallback = callback;
                root.addView(view, root.indexOfChild(cursor), new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenView == null) return;
                root.removeView(fullscreenView);
                fullscreenView = null;
                fullscreenCallback.onCustomViewHidden();
                web.setVisibility(View.VISIBLE);
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOME);
    }

    private static boolean isAllowed(Uri uri) {
        if (uri.toString().startsWith("file:///android_asset/")) return true;
        String host = uri.getHost();
        if (host == null) return false;
        for (String h : ALLOWED_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) return true;
            if (fullscreenView != null) { web.getWebChromeClient().onHideCustomView(); return true; }
            if (web.canGoBack()) { web.goBack(); return true; }
            return super.dispatchKeyEvent(e);
        }

        // Play/pause remote buttons -> toggle the first video on the page (including iframes we can reach)
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down) web.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.paused?v.play():v.pause();}})()", null);
            return true;
        }

        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SEARCH) {
            if (down) { if (fullscreenView != null) web.getWebChromeClient().onHideCustomView(); web.loadUrl(HOME); }
            return true;
        }

        // The home page is built for the D-pad, so no cursor there
        if (onHome()) {
            cursor.setVisibility(View.GONE);
            return super.dispatchKeyEvent(e);
        }
        cursor.setVisibility(View.VISIBLE);

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) moveCursor(code, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && e.getRepeatCount() == 0) click();
                return true;
        }
        // Let text input (on-screen keyboard etc.) through to the WebView
        return super.dispatchKeyEvent(e);
    }

    private boolean onHome() {
        String url = web.getUrl();
        return url == null || url.startsWith(HOME);
    }

    private void moveCursor(int code, int repeat) {
        float step = Math.min(12 + repeat * 4, 60) * getResources().getDisplayMetrics().density / 2f;
        float dx = 0, dy = 0;
        if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -step;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = step;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -step;
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = step;

        float nx = cursor.x + dx, ny = cursor.y + dy;
        int w = root.getWidth(), h = root.getHeight();
        // Hitting the top/bottom edge scrolls the page instead
        if (ny < 0 && fullscreenView == null) { web.scrollBy(0, (int) dy * 3); ny = 0; }
        if (ny > h - 1 && fullscreenView == null) { web.scrollBy(0, (int) dy * 3); ny = h - 1; }
        cursor.moveTo(Math.max(0, Math.min(w - 1, nx)), Math.max(0, Math.min(h - 1, ny)));
    }

    private void click() {
        View target = fullscreenView != null ? fullscreenView : web;
        long t = SystemClock.uptimeMillis();
        float x = cursor.x, y = cursor.y;
        MotionEvent downEv = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent upEv = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0);
        downEv.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        upEv.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(downEv);
        target.dispatchTouchEvent(upEv);
        downEv.recycle();
        upEv.recycle();
        cursor.pulse();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() {
        ((ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
