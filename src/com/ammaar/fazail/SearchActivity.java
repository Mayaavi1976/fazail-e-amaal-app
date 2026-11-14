package com.ammaar.fazail;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends Activity {
    private static final int MAX = 300;
    private EditText box;
    private TextView info;
    private ListView list;
    private Book book;
    private final List<Integer> pageOf = new ArrayList<>();
    private int run;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.style(this, "Search");
        try {
            book = Book.get(this);
        } catch (Exception e) {
            finish();
            return;
        }
        int p = Ui.dp(this, 12);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(p, p, p, 0);
        box = new EditText(this);
        box.setHint("Word or phrase");
        box.setSingleLine(true);
        box.setInputType(InputType.TYPE_CLASS_TEXT);
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        row.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        Button go = new Button(this);
        go.setText("Search");
        row.addView(go);
        root.addView(row);
        info = new TextView(this);
        info.setPadding(p + Ui.dp(this, 4), Ui.dp(this, 6), p, Ui.dp(this, 6));
        root.addView(info);
        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        go.setOnClickListener(v -> search());
        box.setOnEditorActionListener((TextView v, int action, KeyEvent ev) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || ev != null) { search(); return true; }
            return false;
        });
        list.setOnItemClickListener((av, v, pos, id) -> Ui.open(this, pageOf.get(pos)));
        box.requestFocus();
    }

    private void search() {
        final String q = box.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) { info.setText("Type at least 2 characters."); return; }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        info.setText("Searching...");
        final int mine = ++run;
        new Thread(() -> {
            final List<String> rows = new ArrayList<>();
            final List<Integer> pgs = new ArrayList<>();
            String[] pages = book.pages();
            boolean anyText = false;
            int hits = 0;
            for (int i = 0; i < pages.length; i++) {
                String t = pages[i];
                if (t.isEmpty()) continue;
                anyText = true;
                String low = t.toLowerCase(Locale.ROOT);
                int at = low.indexOf(q);
                if (at < 0) continue;
                hits++;
                if (rows.size() >= MAX) continue;
                // lower-casing can change length for a few characters; clamp to stay safe
                int s = Math.max(0, Math.min(at, t.length()) - 60);
                int e = Math.min(t.length(), at + q.length() + 80);
                rows.add("Page " + (i + 1) + "\n" + (s > 0 ? "..." : "") + t.substring(s, e)
                        + (e < t.length() ? "..." : ""));
                pgs.add(i + 1);
            }
            final boolean has = anyText;
            final int total = hits;
            runOnUiThread(() -> {
                if (mine != run || isFinishing()) return;
                pageOf.clear();
                pageOf.addAll(pgs);
                list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
                if (!has) info.setText("This book's pages are images, so the text cannot be searched.");
                else if (total == 0) info.setText("No results found.");
                else info.setText(total + " page" + (total == 1 ? "" : "s") + " found"
                            + (total > MAX ? " (showing first " + MAX + ")" : ""));
            });
        }).start();
    }
}
