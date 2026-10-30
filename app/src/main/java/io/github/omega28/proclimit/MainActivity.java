package io.github.omega28.proclimit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView permissionView;
    private TextView grantCommandView;
    private Button copyButton;
    private TextView liveView;
    private TextView bootView;
    private TextView actionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setFitsSystemWindows(true);

        permissionView = text(root);
        grantCommandView = text(root);
        grantCommandView.setTypeface(Typeface.MONOSPACE);
        grantCommandView.setTextIsSelectable(true);
        copyButton = button(root, "Copy grant command", v -> copyGrantCommand());
        liveView = text(root);
        bootView = text(root);
        button(root, "Apply " + ProcLimit.TARGET_LIMIT + " now", v -> applyNow(ProcLimit.TARGET_LIMIT));
        button(root, "Reset to Standard", v -> applyNow(ProcLimit.STANDARD));
        actionView = text(root);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean granted = ProcLimit.hasPermission(this);
        permissionView.setText("Permission " + ProcLimit.PERMISSION + ": "
                + (granted ? "GRANTED" : "NOT granted — run once from the Mac or Termux:"));
        int grantVisibility = granted ? View.GONE : View.VISIBLE;
        grantCommandView.setText(ProcLimit.grantCommand(this));
        grantCommandView.setVisibility(grantVisibility);
        copyButton.setVisibility(grantVisibility);

        String live;
        try {
            live = ProcLimit.describe(ProcLimit.getLimit());
        } catch (Throwable t) {
            live = "unreadable: " + t;
        }
        liveView.setText("Current live limit: " + live);
        bootView.setText("Last boot apply: " + ProcLimit.bootResult(this));
    }

    private void applyNow(int limit) {
        actionView.setText("Last button result: " + ProcLimit.apply(limit));
        refresh();
    }

    private void copyGrantCommand() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("grant command", ProcLimit.grantCommand(this)));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private TextView text(LinearLayout parent) {
        TextView view = new TextView(this);
        view.setTextSize(16);
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view);
        return view;
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(label);
        view.setOnClickListener(listener);
        parent.addView(view);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
