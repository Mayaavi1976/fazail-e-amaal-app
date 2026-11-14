package com.ammaar.fazail;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Reads one section of the book as real text (English in a book font, Arabic as clear images). */
public class TextActivity extends Activity {
    private static final int M_MARK = 1, M_NIGHT = 2, M_BIG = 3, M_SMALL = 4, M_CONTENTS = 5,
            M_SEARCH = 6, M_GOTO = 7, M_MARKS = 8, M_SCAN = 9;
    private Book book;
    private Book.Sec sec;
    private WebView web;
    private TextView label;
    private LinearLayout bar;
    private MenuItem markItem;
    private int page = 1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.style(this, "Fazail-e-Amaal");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            book = Book.get(this);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Could not open the book"), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setBuiltInZoomControls(false);
        web.addJavascriptInterface(new Bridge(), "App");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("scan:")) {
                    try { Ui.openScan(TextActivity.this, Integer.parseInt(url.substring(5))); }
                    catch (NumberFormatException ignored) { }
                }
                return true;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = new Button(this);
        prev.setText(L.t("< Prev"));
        prev.setOnClickListener(v -> step(-1));
        Button next = new Button(this);
        next.setText(L.t("Next >"));
        next.setOnClickListener(v -> step(1));
        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(14);
        label.setOnClickListener(v -> Ui.askPage(this, book.pageCount(), p -> Ui.open(this, p)));
        bar.addView(prev);
        bar.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(next);
        root.addView(bar);
        setContentView(root);

        int start = b != null ? b.getInt("page", 1) : getIntent().getIntExtra("page", Book.lastPage(this));
        show(start);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (book != null) show(i.getIntExtra("page", page));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    private void step(int dir) {
        Book.Sec s = book.secNear(sec, dir);
        if (s == null) {
            Toast.makeText(this, dir < 0 ? L.t("This is the first section") : L.t("This is the last section"),
                    Toast.LENGTH_SHORT).show();
        } else {
            show(s.first);
        }
    }

    private void show(int p) {
        Book.Sec s = book.secFor(p);
        if (s == null) {                      // this page has no text version: show the scan
            Ui.openScan(this, p);
            if (sec == null) finish();
            return;
        }
        sec = s;
        page = p;
        Book.setLastPage(this, p);
        boolean night = Book.night(this);
        bar.setBackgroundColor(night ? 0xFF121212 : 0xFFF5F5F5);
        label.setTextColor(night ? 0xFFCCCCCC : 0xFF222222);
        web.setBackgroundColor(night ? 0xFF121212 : 0xFFFBF8F1);
        web.getSettings().setTextZoom(Book.prefs(this).getInt("zoom", 115));
        if (getActionBar() != null) getActionBar().setTitle(s.book);
        updateLabel();
        String body;
        try {
            body = book.readAsset("text/" + s.id + ".html");
        } catch (Exception e) {
            Ui.openScan(this, p);
            finish();
            return;
        }
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<link rel='stylesheet' href='../reader.css'></head><body class='"
                + (night ? "night" : "day") + "'>" + body
                + "<script>var want=" + p + ";</script><script src='../reader.js'></script></body></html>";
        web.loadDataWithBaseURL("file:///android_asset/text/", html, "text/html", "utf-8", null);
    }

    private void updateLabel() {
        label.setText(sec.title + "\n" + L.t("Page ") + page + L.t(" of ") + book.pageCount());
        if (markItem != null) markItem.setTitle(Book.bookmarks(this).contains(page) ? "★" : "☆");
    }

    private final class Bridge {
        @JavascriptInterface
        public void page(final int n) {
            runOnUiThread(() -> {
                if (isFinishing() || sec == null || n < sec.first || n > sec.last || n == page) return;
                page = n;
                Book.setLastPage(TextActivity.this, n);
                updateLabel();
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        markItem = menu.add(0, M_MARK, 0, "☆");
        markItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, M_BIG, 1, L.t("Bigger text"));
        menu.add(0, M_SMALL, 2, L.t("Smaller text"));
        menu.add(0, M_NIGHT, 3, L.t("Night mode"));
        menu.add(0, M_SCAN, 4, L.t("Show scanned page"));
        menu.add(0, M_CONTENTS, 5, L.t("Contents"));
        menu.add(0, M_SEARCH, 6, L.t("Search"));
        menu.add(0, M_GOTO, 7, L.t("Go to page"));
        menu.add(0, M_MARKS, 8, L.t("Bookmarks"));
        if (sec != null) updateLabel();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        switch (it.getItemId()) {
            case M_MARK:
                boolean on = Book.toggleBookmark(this, page);
                Toast.makeText(this, on ? L.t("Page ") + page + L.t(" bookmarked") : L.t("Bookmark removed"),
                        Toast.LENGTH_SHORT).show();
                updateLabel();
                return true;
            case M_BIG:
            case M_SMALL:
                int z = Book.prefs(this).getInt("zoom", 115) + (it.getItemId() == M_BIG ? 15 : -15);
                z = Math.max(70, Math.min(250, z));
                Book.prefs(this).edit().putInt("zoom", z).apply();
                web.getSettings().setTextZoom(z);
                return true;
            case M_NIGHT:
                Book.setNight(this, !Book.night(this));
                show(page);
                return true;
            case M_SCAN:
                Ui.openScan(this, page);
                return true;
            case M_CONTENTS:
                startActivity(new Intent(this, ListScreen.class).putExtra("mode", "contents"));
                return true;
            case M_SEARCH:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case M_GOTO:
                Ui.askPage(this, book.pageCount(), p -> Ui.open(this, p));
                return true;
            case M_MARKS:
                startActivity(new Intent(this, ListScreen.class).putExtra("mode", "bookmarks"));
                return true;
            default:
                return super.onOptionsItemSelected(it);
        }
    }
}
