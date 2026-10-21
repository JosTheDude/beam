package com.barnav.beam;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Self-updates from the server publish.sh runs. Only active when update.url is set in local.properties,
 * so builds from the public repo never phone anywhere.
 */
class Updater {
    private static final String ACTION_RESULT = "com.barnav.beam.INSTALL_RESULT";
    private static final long CHECK_EVERY_MS = 10 * 60 * 1000;

    private final Activity activity;
    private final String base;
    private long lastCheck;
    private boolean dismissed, busy;

    Updater(Activity activity) {
        this.activity = activity;
        this.base = BuildConfig.UPDATE_URL.replaceAll("/+$", "");
    }

    /** Called on start/resume; checks at most every 10 minutes, and not again once you say Later. */
    void maybeCheck() {
        if (base.isEmpty() || dismissed || busy || System.currentTimeMillis() - lastCheck < CHECK_EVERY_MS) return;
        lastCheck = System.currentTimeMillis();
        new Thread(() -> {
            try {
                int latest = new JSONObject(new String(get(base + "/version.json"), "UTF-8")).getInt("versionCode");
                if (latest > BuildConfig.VERSION_CODE) activity.runOnUiThread(() -> offer(latest));
            } catch (Exception ignored) {
                // Server not running: nothing to do
            }
        }).start();
    }

    private void offer(int latest) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage("Beam v" + latest + " is ready (you have v" + BuildConfig.VERSION_CODE + ").")
                .setPositiveButton("Update", (d, w) -> install())
                .setNegativeButton("Later", (d, w) -> dismissed = true)
                .show();
    }

    private void install() {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "Allow Beam to install apps, then go back", Toast.LENGTH_LONG).show();
            lastCheck = 0; // offer again when you come back
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (Exception e) {
                Toast.makeText(activity, "Couldn't open that setting on this device", Toast.LENGTH_LONG).show();
            }
            return;
        }
        busy = true;
        Toast.makeText(activity, "Downloading update…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] apk = get(base + "/beam.apk");
                PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(params);
                try (PackageInstaller.Session session = pi.openSession(id)) {
                    try (OutputStream out = session.openWrite("beam.apk", 0, apk.length)) {
                        out.write(apk);
                        session.fsync(out);
                    }
                    Intent result = new Intent(ACTION_RESULT).setPackage(activity.getPackageName());
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    activity.runOnUiThread(this::listenForResult);
                    session.commit(PendingIntent.getBroadcast(activity, id, result, flags).getIntentSender());
                }
            } catch (Exception e) {
                busy = false;
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // The installer reports back here; "pending user action" carries the system confirm screen to show
    private void listenForResult() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) activity.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
                busy = false;
                activity.unregisterReceiver(this);
                if (status != PackageInstaller.STATUS_SUCCESS) {
                    Toast.makeText(activity, "Update not installed: "
                            + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), Toast.LENGTH_LONG).show();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
