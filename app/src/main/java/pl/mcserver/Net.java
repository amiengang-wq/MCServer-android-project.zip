package pl.mcserver;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Pobieranie przez HTTPS na Androidzie 4.4 (TLS 1.2 trzeba wlaczyc recznie). */
final class Net {
    interface Progress { void onProgress(int percent, long bytes); }

    private static class Tls12Factory extends SSLSocketFactory {
        private final SSLSocketFactory d;
        Tls12Factory(SSLSocketFactory d) { this.d = d; }
        private Socket fix(Socket s) {
            if (s instanceof SSLSocket)
                ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"});
            return s;
        }
        public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
        public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException { return fix(d.createSocket(s, h, p, a)); }
        public Socket createSocket(String h, int p) throws IOException { return fix(d.createSocket(h, p)); }
        public Socket createSocket(String h, int p, InetAddress la, int lp) throws IOException { return fix(d.createSocket(h, p, la, lp)); }
        public Socket createSocket(InetAddress h, int p) throws IOException { return fix(d.createSocket(h, p)); }
        public Socket createSocket(InetAddress h, int p, InetAddress la, int lp) throws IOException { return fix(d.createSocket(h, p, la, lp)); }
    }

    private static SSLSocketFactory factory(boolean trustAll) throws Exception {
        SSLContext ctx;
        try { ctx = SSLContext.getInstance("TLSv1.2"); } catch (Exception e) { ctx = SSLContext.getInstance("TLS"); }
        TrustManager[] tm = null;
        if (trustAll) tm = new TrustManager[]{ new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        ctx.init(null, tm, new SecureRandom());
        return new Tls12Factory(ctx.getSocketFactory());
    }

    /** Pobiera url do pliku out. Zwraca SHA-1 (hex). trustAll = tylko gdy potem sprawdzasz hash! */
    static String download(String url, File out, boolean trustAll, Progress p) throws Exception {
        String cur = url;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            if (c instanceof HttpsURLConnection) {
                HttpsURLConnection h = (HttpsURLConnection) c;
                h.setSSLSocketFactory(factory(trustAll));
                if (trustAll) h.setHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String s, SSLSession ss) { return true; } });
            }
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "MCServerApp/1.0");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                if (loc == null) throw new IOException("redirect bez Location");
                cur = new URL(new URL(cur), loc).toString();
                c.disconnect();
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code + " dla " + cur);
            long total = c.getContentLength();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            InputStream in = c.getInputStream();
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[32768];
            long got = 0; int n, last = -10;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n); md.update(buf, 0, n); got += n;
                if (p != null) {
                    int pct = total > 0 ? (int) (got * 100 / total) : -1;
                    if (pct >= last + 10 || (total <= 0 && got / 1048576 != (got - n) / 1048576)) {
                        last = pct; p.onProgress(pct, got);
                    }
                }
            }
            os.close(); in.close(); c.disconnect();
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        }
        throw new IOException("za duzo przekierowan");
    }
}
