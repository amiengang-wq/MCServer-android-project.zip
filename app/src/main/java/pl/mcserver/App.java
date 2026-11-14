package pl.mcserver;

import android.app.Application;
import android.content.Intent;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Lapie kazdy nieobsluzony blad, zapisuje go i pokazuje na ekranie (CrashActivity w osobnym procesie). */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String trace = "Watek: " + t.getName() + "\nAndroid: " + android.os.Build.VERSION.RELEASE
                            + " / " + android.os.Build.MODEL + " / " + android.os.Build.CPU_ABI + "\n\n" + sw;
                    try {
                        File d = new File(Environment.getExternalStorageDirectory(), "MCServer");
                        d.mkdirs();
                        FileWriter w = new FileWriter(new File(d, "crash.txt"));
                        w.write(trace); w.close();
                    } catch (Throwable ignored) {}
                    Intent i = new Intent(App.this, CrashActivity.class);
                    i.putExtra("trace", trace.length() > 8000 ? trace.substring(0, 8000) : trace);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }
}
