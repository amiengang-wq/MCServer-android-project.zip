package pl.mcserver;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

public class ServerService extends Service {

    public interface Listener { void onLine(String line); void onState(); }

    public static final StringBuilder LOG = new StringBuilder();
    public static volatile Process proc;
    public static volatile Listener listener;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wl;

    public static boolean isRunning() { return proc != null; }

    public static synchronized void log(final String s) {
        LOG.append(s).append('\n');
        if (LOG.length() > 40000) LOG.delete(0, LOG.length() - 30000);
        H.post(new Runnable() { public void run() {
            Listener l = listener; if (l != null) l.onLine(s);
        }});
    }

    private static void state() {
        H.post(new Runnable() { public void run() {
            Listener l = listener; if (l != null) l.onState();
        }});
    }

    public static void sendCommand(String cmd) {
        Process p = proc;
        if (p == null) { log("[app] serwer nie dziala"); return; }
        try {
            OutputStream os = p.getOutputStream();
            os.write((cmd + "\n").getBytes("UTF-8"));
            os.flush();
            log("> " + cmd);
        } catch (Exception e) { log("[app] blad wysylania: " + e); }
    }

    public static File serverDir(Context c) { return new File(c.getFilesDir(), "server"); }
    public static File jreDir(Context c) { return new File(c.getFilesDir(), "jre"); }

    /** Szuka bin/java w katalogu JRE (maks. 3 poziomy w dol). */
    public static File findJava(File dir, int depth) {
        File j = new File(dir, "bin/java");
        if (j.isFile()) return j;
        if (depth <= 0) return null;
        File[] ch = dir.listFiles();
        if (ch == null) return null;
        for (File f : ch) if (f.isDirectory()) {
            File r = findJava(f, depth - 1);
            if (r != null) return r;
        }
        return null;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (proc != null) return START_NOT_STICKY;
        final int ram = intent != null ? intent.getIntExtra("ram", 256) : 256;

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, 0);
        Notification n = new Notification.Builder(this)
                .setContentTitle("Serwer Minecraft")
                .setContentText("Dziala w tle")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).build();
        startForeground(1, n);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mcserver");
        wl.acquire();

        new Thread(new Runnable() { public void run() { runServer(ram); } }).start();
        return START_NOT_STICKY;
    }

    private void runServer(int ram) {
        try {
            File jre = jreDir(this);
            File java = findJava(jre, 3);
            File dir = serverDir(this);
            File jar = new File(dir, "server.jar");
            if (!jar.isFile()) jar = new File(dir, "paper.jar");
            if (java == null) { log("[app] Brak JRE - wpisz URL JRE albo wgraj archiwum jre* do /sdcard/MCServer i kliknij Instaluj"); return; }
            if (!jar.isFile()) { log("[app] Brak server.jar - kliknij Instaluj"); return; }
            java.setExecutable(true, false);
            File home = java.getParentFile().getParentFile();

            ProcessBuilder pb = new ProcessBuilder(
                    java.getAbsolutePath(),
                    "-Xms" + Math.max(32, ram / 2) + "M",
                    "-Xmx" + ram + "M",
                    "-Djava.io.tmpdir=" + new File(dir, "tmp").getAbsolutePath(),
                    "-jar", jar.getName(), "nogui");
            new File(dir, "tmp").mkdirs();
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.put("JAVA_HOME", home.getAbsolutePath());
            String lib = home + "/lib/aarch32/jli:" + home + "/lib/aarch32:" + home + "/lib/aarch32/client:"
                    + home + "/lib/arm:" + home + "/lib/arm/server:" + home + "/lib/arm/client:"
                    + home + "/lib:" + home + "/lib/server";
            String old = env.get("LD_LIBRARY_PATH");
            env.put("LD_LIBRARY_PATH", old == null ? lib : lib + ":" + old);
            env.put("HOME", dir.getAbsolutePath());

            log("[app] start: " + java + " -Xmx" + ram + "M -jar " + jar.getName());
            proc = pb.start();
            state();

            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) log(line);
            int code = proc.waitFor();
            log("[app] serwer zakonczony, kod " + code);
        } catch (Throwable e) {
            log("[app] blad: " + e);
        } finally {
            proc = null;
            state();
            if (wl != null && wl.isHeld()) wl.release();
            stopForeground(true);
            stopSelf();
        }
    }

    /** Lagodne zatrzymanie: komenda stop, a po 20 s ubicie procesu. */
    public static void stopServer() {
        final Process p = proc;
        if (p == null) return;
        sendCommand("stop");
        new Thread(new Runnable() { public void run() {
            try { Thread.sleep(20000); } catch (InterruptedException ignored) {}
            if (proc == p) { log("[app] wymuszam zamkniecie"); p.destroy(); }
        }}).start();
    }
}
