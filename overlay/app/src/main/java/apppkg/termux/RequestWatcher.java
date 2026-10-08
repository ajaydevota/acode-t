package __APP_PKG__.termux;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * Watches the request files written by the `eg` and `acode-t` commands and opens the matching
 * screen. `am` is not used on purpose: /system/bin/am does not work from an app uid on Android 8+.
 */
public final class RequestWatcher {

    private static final String LOG_TAG = "RequestWatcher";
    private static final long POLL_MS = 300L;

    private static boolean sStarted = false;
    private static String sLastEg = null;
    private static String sLastAcode = null;

    private RequestWatcher() {
    }

    public static synchronized void start(final Context context) {
        if (sStarted) return;
        sStarted = true;

        final Context app = context.getApplicationContext();
        final File home = TermuxBootstrap.home(app);
        final File egFile = new File(home, ".eg_open");
        final File acodeFile = new File(home, ".acode_open");

        sLastEg = read(egFile);
        sLastAcode = read(acodeFile);

        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                checkEg(app, egFile);
                checkAcode(app, acodeFile);
            }
        }, "request-watcher");
        t.setDaemon(true);
        t.start();
    }

    private static void checkEg(Context app, File f) {
        String cur = read(f);
        if (cur == null) return;
        cur = cur.trim();
        if (cur.isEmpty() || cur.equals(sLastEg)) return;
        sLastEg = cur;

        String path = cur;
        int sep = cur.lastIndexOf('|');
        if (sep > 0) path = cur.substring(0, sep);
        path = path.trim();
        if (path.isEmpty()) return;

        try {
            Intent i = new Intent();
            i.setClassName(app.getPackageName(), __APP_PKG__.termux.EditorActivity.class.getName());
            i.putExtra("file", path);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
        } catch (Exception e) {
            Log.e(LOG_TAG, "eg open failed: " + e);
        }
    }

    private static void checkAcode(Context app, File f) {
        String cur = read(f);
        if (cur == null) return;
        cur = cur.trim();
        if (cur.isEmpty() || cur.equals(sLastAcode)) return;
        sLastAcode = cur;

        try {
            // The launcher activity is Termux now, so target Acode's Cordova activity directly.
            Intent i = new Intent();
            i.setClassName(app.getPackageName(), app.getPackageName() + ".MainActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            app.startActivity(i);
        } catch (Exception e) {
            Log.e(LOG_TAG, "acode open failed: " + e);
        }
    }

    private static String read(File file) {
        if (!file.exists() || !file.isFile()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }
}
