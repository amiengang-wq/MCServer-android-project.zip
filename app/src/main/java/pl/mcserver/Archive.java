package pl.mcserver;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.tukaani.xz.XZInputStream;

/** Rozpakowuje .zip, .tar, .tar.gz, .tar.xz (rozpoznaje po zawartosci, nie po nazwie). */
final class Archive {

    static void extract(File archive, File dest) throws IOException {
        extract(new FileInputStream(archive), dest);
    }

    static void extract(InputStream raw, File dest) throws IOException {
        dest.mkdirs();
        BufferedInputStream in = new BufferedInputStream(raw, 65536);
        try {
            in.mark(8);
            byte[] m = new byte[6];
            int n = in.read(m);
            in.reset();
            if (n >= 4 && m[0] == 'P' && m[1] == 'K') unzip(in, dest);
            else if (n >= 2 && (m[0] & 0xff) == 0x1f && (m[1] & 0xff) == 0x8b) untar(new GZIPInputStream(in, 65536), dest);
            else if (n >= 6 && (m[0] & 0xff) == 0xFD && m[1] == '7' && m[2] == 'z' && m[3] == 'X' && m[4] == 'Z')
                untar(new XZInputStream(in), dest);
            else untar(in, dest);
        } finally { in.close(); }
    }

    private static File safe(File dest, String name) throws IOException {
        File f = new File(dest, name);
        if (!f.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator)) return null; // zip-slip
        return f;
    }

    private static void unzip(InputStream raw, File dest) throws IOException {
        ZipInputStream z = new ZipInputStream(raw);
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            File f = safe(dest, e.getName());
            if (f == null) continue;
            if (e.isDirectory()) { f.mkdirs(); continue; }
            f.getParentFile().mkdirs();
            OutputStream os = new FileOutputStream(f);
            byte[] b = new byte[65536]; int n;
            while ((n = z.read(b)) > 0) os.write(b, 0, n);
            os.close();
        }
    }

    private static boolean readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }

    private static void skip(InputStream in, long n) throws IOException {
        byte[] b = new byte[8192];
        while (n > 0) { int r = in.read(b, 0, (int) Math.min(b.length, n)); if (r < 0) break; n -= r; }
    }

    private static String str(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && b[e] != 0) e++;
        try { return new String(b, off, e - off, "UTF-8"); } catch (Exception x) { return ""; }
    }

    private static long oct(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            int c = b[i];
            if (c == 0 || c == ' ') { if (v > 0) break; else continue; }
            if (c < '0' || c > '7') break;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    /** Minimalny czytnik tar (ustar + GNU longname). Dowiazania symboliczne pomija. */
    private static void untar(InputStream in, File dest) throws IOException {
        byte[] h = new byte[512];
        String longName = null;
        while (readFully(in, h)) {
            if (h[0] == 0) break;
            String name = str(h, 0, 100);
            String prefix = str(h, 345, 155);
            if (prefix.length() > 0) name = prefix + "/" + name;
            long size = oct(h, 124, 12);
            long mode = oct(h, 100, 8);
            char type = (char) h[156];
            long padded = (size + 511) / 512 * 512;

            if (type == 'L') {                       // GNU dluga nazwa
                byte[] nb = new byte[(int) size];
                readFully(in, nb);
                longName = str(nb, 0, nb.length);
                skip(in, padded - size);
                continue;
            }
            if (longName != null) { name = longName; longName = null; }
            if (type == 'x' || type == 'g') { skip(in, padded); continue; } // pax
            File f = safe(dest, name);
            if (f == null || type == '2' || type == '1' || type == '3' || type == '4' || type == '6') {
                skip(in, padded); continue;
            }
            if (type == '5') { f.mkdirs(); continue; }
            f.getParentFile().mkdirs();
            OutputStream os = new FileOutputStream(f);
            byte[] b = new byte[65536];
            long left = size;
            while (left > 0) {
                int n = in.read(b, 0, (int) Math.min(b.length, left));
                if (n < 0) throw new IOException("ucieta paczka tar");
                os.write(b, 0, n); left -= n;
            }
            os.close();
            skip(in, padded - size);
            f.setReadable(true, false);
            if ((mode & 0111) != 0) f.setExecutable(true, false);
        }
    }
}
