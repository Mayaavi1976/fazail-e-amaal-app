package com.ammaar.fazail;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends Activity {
    private static final int M_MARK = 1, M_NIGHT = 2, M_CONTENTS = 3, M_SEARCH = 4, M_GOTO = 5, M_MARKS = 6;
    private Book book;
    private ZoomImageView view;
    private TextView label;
    private LinearLayout bar;
    private MenuItem markItem;
    private int page = 1, width;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.style(this, "Fazail-e-Amaal");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            book = Book.get(this);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the book", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        width = Math.min(1800, Math.round(Math.min(dm.widthPixels, dm.heightPixels) * 1.5f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        view = new ZoomImageView(this);
        view.setListener(dir -> show(page + dir));
        root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = new Button(this);
        prev.setText("< Prev");
        prev.setOnClickListener(v -> show(page - 1));
        Button next = new Button(this);
        next.setText("Next >");
        next.setOnClickListener(v -> show(page + 1));
        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(16);
        label.setOnClickListener(v -> Ui.askPage(this, book.pageCount(), this::show));
        bar.addView(prev);
        bar.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(next);
        root.addView(bar);
        setContentView(root);

        applyNight();
        int start = b != null ? b.getInt("page", 1) : getIntent().getIntExtra("page", Book.lastPage(this));
        show(start);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (book != null) {
            applyNight();
            show(i.getIntExtra("page", page));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }

    private void applyNight() {
        boolean n = Book.night(this);
        view.setNight(n);
        bar.setBackgroundColor(n ? 0xFF121212 : 0xFFF5F5F5);
        label.setTextColor(n ? 0xFFCCCCCC : 0xFF222222);
    }

    private void show(int p) {
        if (p < 1 || p > book.pageCount()) {
            Toast.makeText(this, p < 1 ? "This is the first page" : "This is the last page",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        page = p;
        Book.setLastPage(this, p);
        label.setText("Page " + p + " / " + book.pageCount());
        String sec = book.titleFor(p);
        if (getActionBar() != null) getActionBar().setTitle(sec.isEmpty() ? "Fazail-e-Amaal" : sec);
        updateMark();
        final int want = p;
        exec.execute(() -> {
            Bitmap bm;
            try {
                bm = book.render(want, width);
            } catch (OutOfMemoryError oom) {
                book.clearCache();
                width = Math.max(600, width * 2 / 3);
                try {
                    bm = book.render(want, width);
                } catch (Throwable t) {
                    return;
                }
            } catch (Throwable t) {
                return;
            }
            final Bitmap done = bm;
            runOnUiThread(() -> { if (want == page && !isFinishing()) view.setBitmap(done); });
            if (want < book.pageCount()) {
                try { book.render(want + 1, width); } catch (Throwable ignored) { }
            }
        });
    }

    private void updateMark() {
        if (markItem != null) {
            markItem.setTitle(Book.bookmarks(this).contains(page) ? "★" : "☆");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        markItem = menu.add(0, M_MARK, 0, "☆");
        markItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, M_NIGHT, 1, "Night mode");
        menu.add(0, M_CONTENTS, 2, "Contents");
        menu.add(0, M_SEARCH, 3, "Search");
        menu.add(0, M_GOTO, 4, "Go to page");
        menu.add(0, M_MARKS, 5, "Bookmarks");
        updateMark();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        switch (it.getItemId()) {
            case M_MARK:
                boolean on = Book.toggleBookmark(this, page);
                Toast.makeText(this, on ? "Page " + page + " bookmarked" : "Bookmark removed",
                        Toast.LENGTH_SHORT).show();
                updateMark();
                return true;
            case M_NIGHT:
                Book.setNight(this, !Book.night(this));
                applyNight();
                return true;
            case M_CONTENTS:
                startActivity(new Intent(this, ListScreen.class).putExtra("mode", "contents"));
                return true;
            case M_SEARCH:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case M_GOTO:
                Ui.askPage(this, book.pageCount(), this::show);
                return true;
            case M_MARKS:
                startActivity(new Intent(this, ListScreen.class).putExtra("mode", "bookmarks"));
                return true;
            default:
                return super.onOptionsItemSelected(it);
        }
    }
}
