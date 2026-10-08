package __APP_PKG__.termux;

import android.content.Context;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the Termux bootstrap (shipped as an asset) into the app's files directory and
 * installs the `eg` and `acode-t` helper commands into $PREFIX/bin.
 *
 * The package name of this app must be com.termux, because the bootstrap binaries and scripts
 * have /data/data/com.termux/files/... paths compiled in.
 */
public final class TermuxBootstrap {

    private static final String ASSET = "termux/bootstrap-arm64.zip";

    private TermuxBootstrap() {
    }

    public static File prefix(Context c) {
        return new File(c.getFilesDir(), "usr");
    }

    public static File home(Context c) {
        return new File(c.getFilesDir(), "home");
    }

    public static boolean isInstalled(Context c) {
        return new File(prefix(c), "bin/bash").exists() || new File(prefix(c), "bin/sh").exists();
    }

    public static synchronized void ensure(Context c) {
        if (!isInstalled(c)) {
            extract(c);
        }
        installCommands(c);
    }

    private static void extract(Context c) {
        File prefix = prefix(c);
        File staging = new File(c.getFilesDir(), "usr-staging");
        try {
            staging.deleteRecursively();
            staging.mkdirs();
            prefix.deleteRecursively();
            prefix.mkdirs();
            home(c).mkdirs();

            InputStream raw = c.getAssets().open(ASSET);
            ZipInputStream zis = new ZipInputStream(raw);
            byte[] buf = new byte[8192];
            List<String[]> symlinks = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("SYMLINKS.txt")) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] parts = line.split("\u2190");
                        if (parts.length == 2) symlinks.add(new String[]{parts[0], parts[1]});
                    }
                    continue;
                }
                File target = new File(staging, name);
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                FileOutputStream out = new FileOutputStream(target);
                int n;
                while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                if (name.startsWith("bin/") || name.startsWith("libexec")
                        || name.startsWith("lib/apt/")) {
                    try {
                        Os.chmod(target.getAbsolutePath(), 0700);
                    } catch (Exception ignored) {
                    }
                }
            }
            zis.close();

            if (!staging.renameTo(prefix)) {
                throw new Exception("could not move staging to prefix");
            }
            for (String[] s : symlinks) {
                try {
                    Os.symlink(s[0], new File(prefix, s[1]).getAbsolutePath());
                } catch (Exception ignored) {
                }
            }
            new File(prefix, "tmp").mkdirs();
        } catch (Exception ignored) {
        }
    }

    private static void installCommands(Context c) {
        try {
            File bin = new File(prefix(c), "bin");
            if (!bin.exists()) return;
            write(bin, "eg", EG_SCRIPT);
            write(bin, "acode-t", ACODE_SCRIPT);
        } catch (Exception ignored) {
        }
    }

    private static void write(File bin, String name, String body) throws Exception {
        File f = new File(bin, name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(body.getBytes("UTF-8"));
        out.close();
        Os.chmod(f.getAbsolutePath(), 0700);
    }

    private static final String EG_SCRIPT =
        "#!/data/data/com.termux/files/usr/bin/sh\n" +
        "if [ -z \"$1\" ]; then\n" +
        "  echo \"usage: eg <filename>    e.g. eg fast.py\"\n" +
        "  exit 1\n" +
        "fi\n" +
        "case \"$1\" in\n" +
        "  /*) T=\"$1\" ;;\n" +
        "  *)  T=\"$(pwd)/$1\" ;;\n" +
        "esac\n" +
        "echo \"$T|$$\" > \"$HOME/.eg_open\"\n" +
        "echo \"Editor khul raha hai: $T\"\n";

    private static final String ACODE_SCRIPT =
        "#!/data/data/com.termux/files/usr/bin/sh\n" +
        "echo \"$$\" > \"$HOME/.acode_open\"\n" +
        "echo \"Acode khul raha hai...\"\n";

    /** Environment for the terminal session. */
    public static String[] env(Context c) {
        String p = prefix(c).getAbsolutePath();
        String home = home(c).getAbsolutePath();
        List<String> list = new ArrayList<>();
        list.add("PREFIX=" + p);
        list.add("HOME=" + home);
        list.add("PATH=" + p + "/bin:" + p + "/bin/applets");
        list.add("LD_LIBRARY_PATH=" + p + "/lib");
        list.add("TMPDIR=" + p + "/tmp");
        list.add("SHELL=" + p + "/bin/bash");
        list.add("TERM=xterm-256color");
        list.add("LANG=en_US.UTF-8");
        list.add("ANDROID_ROOT=/system");
        list.add("ANDROID_DATA=/data");
        return list.toArray(new String[0]);
    }
}
