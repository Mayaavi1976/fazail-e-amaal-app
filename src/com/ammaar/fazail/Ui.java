package com.ammaar.fazail;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

final class Ui {
    static final int GREEN = 0xFF1B5E20, GREEN_DARK = 0xFF0D3B12;

    interface PageCallback { void go(int page); }

    static void style(Activity a, String title) {
        a.getWindow().setStatusBarColor(GREEN_DARK);
        ActionBar bar = a.getActionBar();
        if (bar != null) {
            bar.setBackgroundDrawable(new ColorDrawable(GREEN));
            bar.setTitle(title);
        }
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static void open(Context c, int page) {
        Intent i = new Intent(c, ReaderActivity.class);
        i.putExtra("page", page);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        c.startActivity(i);
    }

    static void askPage(final Activity a, final int max, final PageCallback cb) {
        final EditText in = new EditText(a);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setHint("1 - " + max);
        FrameLayout box = new FrameLayout(a);
        int p = dp(a, 20);
        box.setPadding(p, dp(a, 8), p, 0);
        box.addView(in);
        new AlertDialog.Builder(a)
                .setTitle("Go to page")
                .setView(box)
                .setPositiveButton("Go", (d, w) -> {
                    try {
                        int n = Integer.parseInt(in.getText().toString().trim());
                        if (n >= 1 && n <= max) { cb.go(n); return; }
                    } catch (NumberFormatException ignored) { }
                    Toast.makeText(a, "Enter a page from 1 to " + max, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
