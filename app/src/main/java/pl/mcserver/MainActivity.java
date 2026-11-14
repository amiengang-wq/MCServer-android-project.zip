package pl.mcserver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity implements ServerService.Listener {

    private TextView tvIp, tvStatus, tvConsole;
    private EditText etRam, etCmd, etUrl;
    private ScrollView scroll;
    private final Handler ui = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tvIp = (TextView) findViewById(R.id.tvIp);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvConsole = (TextView) findViewById(R.id.tvConsole);
        etRam = (EditText) findViewById(R.id.etRam);
        etCmd = (EditText) findViewById(R.id.etCmd);
        etUrl = (EditText) findViewById(R.id.etUrl);
        etUrl.setText(getSharedPreferences("cfg", 0).getString("jreUrl", ""));
        scroll = (ScrollView) findViewById(R.id.scroll);

        ((Button) findViewById(R.id.btnInstall)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { install(); } });
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int ram = 256;
                try { ram = Integer.parseInt(etRam.getText().toString().trim()); } catch (Exception ignored) {}
                Intent i = new Intent(MainActivity.this, ServerService.class);
                i.putExtra("ram", ram);
                startService(i);
            } });
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ServerService.stopServer(); } });
        ((Button) findViewById(R.id.btnSend)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sendCmd(); } });
        etCmd.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_NULL) { sendCmd(); return true; }
                return false;
            } });
    }

    @Override protected void onResume() {
        super.onResume();
        ServerService.listener = this;
        synchronized (ServerService.class) { tvConsole.setText(ServerService.LOG.toString()); }
        refreshIp();
        onState();
        scrollDown();
    }

    @Override protected void onPause() {
        super.onPause();
        if (ServerService.listener == this) ServerService.listener = null;
    }

    private void sendCmd() {
        String c = etCmd.getText().toString().trim();
        if (c.length() > 0) ServerService.sendCommand(c);
        etCmd.setText("");
    }

    private void scrollDown() {
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    @Override public void onLine(String line) {
        tvConsole.append(line + "\n");
        if (tvConsole.length() > 40000) {
            CharSequence t = tvConsole.getText();
            tvConsole.setText(t.subSequence(t.length() - 30000, t.length()));
        }
        scrollDown();
    }

    @Override public void onState() {
        tvStatus.setText("Status: " + (ServerService.isRunning() ? "DZIALA" : "zatrzymany"));
        refreshIp();
    }

    private void refreshIp() {
        String ip = localIp();
        tvIp.setText("Lokalne IP: " + (ip == null ? "brak sieci" : ip + ":25565"));
    }

    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ---- instalacja ----
    private static final String SERVER_URL =
            "https://piston-data.mojang.com/v1/objects/886945bfb2b978778c3a0288fd7fab09d315b25f/server.jar";
    private static final String SERVER_SHA1 = "886945bfb2b978778c3a0288fd7fab09d315b25f";

    private static final Net.Progress PROG = new Net.Progress() {
        public void onProgress(int pct, long bytes) {
            ServerService.log("[app] pobrano " + (pct >= 0 ? pct + "% " : "") + "(" + bytes / 1048576 + " MB)");
        } };

    private void install() {
        final String jreUrl = etUrl.getText().toString().trim();
        getSharedPreferences("cfg", 0).edit().putString("jreUrl", jreUrl).commit();
        new Thread(new Runnable() { public void run() {
            try {
                if (ServerService.isRunning()) { ServerService.log("[app] najpierw zatrzymaj serwer"); return; }
                File sd = new File(Environment.getExternalStorageDirectory(), "MCServer");
                File sdir = ServerService.serverDir(MainActivity.this);
                sdir.mkdirs();

                // 1) serwer: z karty SD albo z Mojanga
                boolean have = false;
                for (String n : new String[]{"server.jar", "paper.jar"}) {
                    File l = new File(sd, n);
                    if (l.isFile()) { ServerService.log("[app] kopiuje " + n); copy(new FileInputStream(l), new File(sdir, n)); have = true; }
                }
                if (!have && !new File(sdir, "server.jar").isFile() && !new File(sdir, "paper.jar").isFile()) {
                    File out = new File(sdir, "server.jar");
                    ServerService.log("[app] pobieram server 1.12.2 z Mojang...");
                    String h;
                    try { h = Net.download(SERVER_URL, out, false, PROG); }
                    catch (javax.net.ssl.SSLException e) {
                        ServerService.log("[app] TLS: " + e.getMessage() + " - ponawiam z kontrola SHA-1");
                        h = Net.download(SERVER_URL, out, true, PROG);   // bezpieczne: sprawdzamy hash
                    }
                    if (!SERVER_SHA1.equalsIgnoreCase(h)) { out.delete(); throw new IOException("zly SHA-1: " + h); }
                    ServerService.log("[app] server.jar OK (SHA-1 sie zgadza)");
                }

                // 2) JRE: archiwum jre* z karty SD -> URL z pola -> wbudowane w aplikacje
                File jre = ServerService.jreDir(MainActivity.this);
                File arch = null; boolean tmp = false;
                File[] list = sd.listFiles();
                if (list != null) for (File f : list) {
                    String n = f.getName().toLowerCase();
                    if (n.contains("jre") && (n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".tar.xz")
                            || n.endsWith(".txz") || n.endsWith(".tar.gz") || n.endsWith(".tgz"))) { arch = f; break; }
                }
                if (arch == null && jreUrl.length() > 0) {
                    arch = new File(getFilesDir(), "jre.download"); tmp = true;
                    ServerService.log("[app] pobieram JRE: " + jreUrl);
                    Net.download(jreUrl, arch, false, PROG);
                }
                if (arch != null) {
                    ServerService.log("[app] rozpakowuje JRE z " + arch.getName() + " (chwila)...");
                    deleteRec(jre);
                    Archive.extract(arch, jre);
                    if (tmp) arch.delete();
                    chmodRec(jre);
                } else if (ServerService.findJava(jre, 3) == null) {
                    ServerService.log("[app] rozpakowuje wbudowane JRE 8 (ok. 100 MB, to potrwa 1-3 min)...");
                    deleteRec(jre);
                    Archive.extract(getAssets().open("jre8-arm.tar.gz"), jre);
                    chmodRec(jre);
                } else ServerService.log("[app] JRE juz zainstalowane");
                File j = ServerService.findJava(jre, 3);
                ServerService.log(j != null ? "[app] JRE OK: " + j : "[app] BRAK bin/java w JRE!");

                // 3) pliki konfiguracyjne
                File eula = new File(sdir, "eula.txt");
                if (!eula.isFile()) { write(eula, "eula=true\n");
                    ServerService.log("[app] zapisano eula=true (akceptujesz EULA Minecrafta)"); }
                File props = new File(sdir, "server.properties");
                if (!props.isFile()) { write(props, "server-port=25565\nonline-mode=false\nview-distance=4\n"
                        + "max-players=5\nspawn-protection=0\nmotd=Serwer na Androidzie\n");
                    ServerService.log("[app] zapisano server.properties (online-mode=false)"); }
                ServerService.log("[app] instalacja zakonczona");
            } catch (Throwable e) { ServerService.log("[app] blad instalacji: " + e); }
        }}).start();
    }

    private static void copy(InputStream in, File out) throws IOException {
        OutputStream os = new FileOutputStream(out);
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        os.close(); in.close();
    }

    private static void write(File f, String s) throws IOException {
        FileOutputStream o = new FileOutputStream(f); o.write(s.getBytes("UTF-8")); o.close();
    }

    private static void chmodRec(File f) {
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) chmodRec(x); }
        f.setReadable(true, false); f.setExecutable(true, false);
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) deleteRec(x); }
        f.delete();
    }
}
