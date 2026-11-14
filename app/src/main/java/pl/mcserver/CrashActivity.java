package pl.mcserver;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Prosty ekran z tekstem bledu (bez layoutu XML, zeby sam nie mogl sie wywalic). */
public class CrashActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String t = getIntent().getStringExtra("trace");
        if (t == null) t = "(brak danych)";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        TextView title = new TextView(this);
        title.setText("MC Server - wystapil blad. Zrob zrzut ekranu tego ekranu i wyslij.\n(Zapisane tez w /sdcard/MCServer/crash.txt)");
        title.setTextSize(14);
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(10);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        root.addView(title);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
