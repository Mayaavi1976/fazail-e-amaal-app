package com.ammaar.fazail;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** The bundled book: PDF pages, table of contents, page text, and reader state. */
public final class Book {
    public static final class Entry {
        public final String title;
        public final int page, level;
        Entry(String t, int p, int l) { title = t; page = p; level = l; }
    }

    private static Book inst;
    private final Context app;
    private PdfRenderer renderer;
    private int count;
    private final List<Entry> toc = new ArrayList<>();
    private String[] pages;
    private final LruCache<Integer, Bitmap> cache = new LruCache<>(3);

    private Book(Context c) { app = c; }

    public static synchronized Book get(Context c) throws Exception {
        if (inst == null) {
            Book b = new Book(c.getApplicationContext());
            b.open();
            inst = b;
        }
        return inst;
    }

    private void open() throws Exception {
        File f = new File(app.getFilesDir(), "book.pdf");
        SharedPreferences p = prefs(app);
        int version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionCode;
        if (!f.exists() || f.length() == 0 || p.getInt("copied", -1) != version) {
            File tmp = new File(app.getFilesDir(), "book.tmp");
            try (InputStream in = app.getAssets().open("book.pdf");
                 OutputStream out = new FileOutputStream(tmp)) {
                copy(in, out);
            }
            if (f.exists()) f.delete();
            if (!tmp.renameTo(f)) throw new IOException("could not store the book");
            p.edit().putInt("copied", version).apply();
        }
        renderer = new PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY));
        count = renderer.getPageCount();
        try {
            JSONArray a = new JSONArray(readAsset("toc.json"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                toc.add(new Entry(o.getString("title"), o.getInt("page"), o.optInt("level", 0)));
            }
        } catch (Exception ignored) { }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = app.getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            copy(in, bo);
            return bo.toString("UTF-8");
        }
    }

    public int pageCount() { return count; }
    public List<Entry> toc() { return toc; }

    /** Section title that the given page falls under, or "". */
    public String titleFor(int page) {
        String t = "";
        for (Entry e : toc) {
            if (e.page <= page) t = e.title; else break;
        }
        return t;
    }

    /** Renders a 1-based page at the given pixel width. Call off the UI thread. */
    public synchronized Bitmap render(int page, int width) {
        Bitmap b = cache.get(page);
        if (b != null && b.getWidth() == width) return b;
        PdfRenderer.Page pg = renderer.openPage(page - 1);
        try {
            int h = Math.max(1, Math.round(width * (pg.getHeight() / (float) pg.getWidth())));
            b = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        } finally {
            pg.close();
        }
        cache.put(page, b);
        return b;
    }

    public void clearCache() { cache.evictAll(); }

    /** Text of every page (index 0 = page 1). May be all empty for scanned PDFs. */
    public synchronized String[] pages() {
        if (pages == null) {
            try {
                pages = readAsset("pages.txt").split("\f", -1);
            } catch (IOException e) {
                pages = new String[0];
            }
        }
        return pages;
    }

    // ---- reader state ----
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("reader", Context.MODE_PRIVATE);
    }

    public static int lastPage(Context c) { return prefs(c).getInt("last", 1); }
    public static void setLastPage(Context c, int p) { prefs(c).edit().putInt("last", p).apply(); }
    public static boolean night(Context c) { return prefs(c).getBoolean("night", false); }
    public static void setNight(Context c, boolean n) { prefs(c).edit().putBoolean("night", n).apply(); }

    public static TreeSet<Integer> bookmarks(Context c) {
        TreeSet<Integer> s = new TreeSet<>();
        for (String x : prefs(c).getString("marks", "").split(",")) {
            try { s.add(Integer.parseInt(x.trim())); } catch (NumberFormatException ignored) { }
        }
        return s;
    }

    /** Adds or removes a bookmark; returns true if the page is now bookmarked. */
    public static boolean toggleBookmark(Context c, int page) {
        TreeSet<Integer> s = bookmarks(c);
        boolean added = s.add(page);
        if (!added) s.remove(page);
        StringBuilder sb = new StringBuilder();
        for (int v : s) { if (sb.length() > 0) sb.append(','); sb.append(v); }
        prefs(c).edit().putString("marks", sb.toString()).apply();
        return added;
    }
}
