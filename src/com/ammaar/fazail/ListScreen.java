package com.ammaar.fazail;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Contents list or bookmarks list, depending on the "mode" extra. */
public class ListScreen extends Activity {
    private boolean bookmarks;
    private ListView list;
    private TextView empty;
    private Book book;
    private final List<String> labels = new ArrayList<>();
    private final List<Integer> pageOf = new ArrayList<>();
    private final List<Integer> levelOf = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bookmarks = "bookmarks".equals(getIntent().getStringExtra("mode"));
        Ui.style(this, bookmarks ? L.t("Bookmarks") : L.t("Contents"));
        try {
            book = Book.get(this);
        } catch (Exception e) {
            finish();
            return;
        }
        list = new ListView(this);
        empty = new TextView(this);
        empty.setGravity(Gravity.CENTER);
        empty.setTextSize(16);
        int p = Ui.dp(this, 32);
        empty.setPadding(p, p, p, p);
        empty.setText(bookmarks
                ? L.t("No bookmarks yet.\nOpen a page and tap the star to add one.")
                : L.t("This book has no contents list."));
        empty.setVisibility(View.GONE);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(list);
        root.addView(empty);
        setContentView(root);

        list.setOnItemClickListener((av, v, pos, id) -> Ui.open(this, pageOf.get(pos)));
        if (bookmarks) {
            list.setOnItemLongClickListener((av, v, pos, id) -> {
                Book.toggleBookmark(this, pageOf.get(pos));
                Toast.makeText(this, L.t("Bookmark removed"), Toast.LENGTH_SHORT).show();
                load();
                return true;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (book != null) load();
    }

    private void load() {
        labels.clear(); pageOf.clear(); levelOf.clear();
        if (bookmarks) {
            for (int pg : Book.bookmarks(this)) {
                String sec = book.titleFor(pg);
                labels.add(L.t("Page ") + pg + (sec.isEmpty() ? "" : "\n" + sec));
                pageOf.add(pg); levelOf.add(0);
            }
        } else {
            for (Book.Entry e : book.toc()) {
                labels.add(e.title + "   (p. " + e.page + ")");
                pageOf.add(e.page); levelOf.add(e.level);
            }
        }
        empty.setVisibility(labels.isEmpty() ? View.VISIBLE : View.GONE);
        final int base = Ui.dp(this, 16), step = Ui.dp(this, 18);
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, cv, parent);
                int lvl = levelOf.get(pos);
                tv.setPadding(base + lvl * step, tv.getPaddingTop(), base, tv.getPaddingBottom());
                tv.setTextSize(lvl == 0 ? 17 : 15);
                return tv;
            }
        });
    }
}
