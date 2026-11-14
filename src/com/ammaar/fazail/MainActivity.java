package com.ammaar.fazail;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private ListView list;
    private TextView status;
    private Book book;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.style(this, "Fazail-e-Amaal");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView head = new TextView(this);
        head.setText("Fazail-e-Amaal");
        head.setTextSize(28);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(0xFFFFFFFF);
        head.setGravity(Gravity.CENTER);
        head.setBackgroundColor(Ui.GREEN);
        int p = Ui.dp(this, 28);
        head.setPadding(p, p, p, Ui.dp(this, 6));
        root.addView(head);

        status = new TextView(this);
        status.setText("Preparing the book...");
        status.setTextColor(0xFFE8F5E9);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Ui.GREEN);
        status.setPadding(p, 0, p, Ui.dp(this, 22));
        root.addView(status);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        new Thread(() -> {
            try {
                final Book bk = Book.get(this);
                runOnUiThread(() -> { book = bk; refresh(); });
            } catch (final Throwable e) {
                runOnUiThread(() -> status.setText("Could not open the book: " + e));
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (book != null) refresh();
    }

    private void refresh() {
        int last = Math.min(Book.lastPage(this), book.pageCount());
        status.setText("English  -  " + book.pageCount() + " pages");
        String sec = book.titleFor(last);
        String[] items = {
                "Continue reading  -  page " + last + (sec.isEmpty() ? "" : "\n" + sec),
                "Contents",
                "Go to page",
                "Search",
                "Bookmarks (" + Book.bookmarks(this).size() + ")",
                "Night mode: " + (Book.night(this) ? "On" : "Off"),
                "About"
        };
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
        list.setOnItemClickListener((av, v, pos, id) -> {
            switch (pos) {
                case 0: Ui.open(this, Book.lastPage(this)); break;
                case 1: startActivity(new Intent(this, ListScreen.class).putExtra("mode", "contents")); break;
                case 2: Ui.askPage(this, book.pageCount(), page -> Ui.open(this, page)); break;
                case 3: startActivity(new Intent(this, SearchActivity.class)); break;
                case 4: startActivity(new Intent(this, ListScreen.class).putExtra("mode", "bookmarks")); break;
                case 5: Book.setNight(this, !Book.night(this)); refresh(); break;
                default:
                    new AlertDialog.Builder(this).setTitle("Fazail-e-Amaal")
                            .setMessage("Offline reader for the English edition of Fazail-e-Amaal.\n\n"
                                    + "Swipe left or right to turn pages, pinch or double-tap to zoom. "
                                    + "Use the menu to bookmark a page, search the text, or switch to night mode.")
                            .setPositiveButton("OK", null).show();
            }
        });
    }
}
