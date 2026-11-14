package com.ammaar.fazail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Shows one page fitted to the view width, with pinch zoom, pan, double-tap and swipe. */
public class ZoomImageView extends View {
    public interface Listener { void onSwipe(int direction); }

    private Bitmap bmp;
    private float s = 1, tx, ty;
    private boolean night;
    private Listener listener;
    private final Matrix m = new Matrix();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint nightPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;

    public ZoomImageView(Context c) {
        super(c);
        nightPaint.setColorFilter(new ColorMatrixColorFilter(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0}));
        final float minFling = 700 * c.getResources().getDisplayMetrics().density;
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomTo(s * d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                tx -= dx;
                ty -= dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (s <= 1.01f && Math.abs(vx) > Math.abs(vy) * 1.5f && Math.abs(vx) > minFling
                        && listener != null) {
                    listener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoomTo(s > 1.01f ? 1 : 2.5f, e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setListener(Listener l) { listener = l; }

    public void setNight(boolean n) { night = n; invalidate(); }

    public void setBitmap(Bitmap b) {
        bmp = b;
        s = 1; tx = 0; ty = 0;
        clamp();
        invalidate();
    }

    private float base() {
        return bmp == null || getWidth() == 0 ? 1 : getWidth() / (float) bmp.getWidth();
    }

    private void zoomTo(float target, float fx, float fy) {
        float ns = Math.max(1, Math.min(5, target));
        float f = ns / s;
        tx = fx - (fx - tx) * f;
        ty = fy - (fy - ty) * f;
        s = ns;
        clamp();
        invalidate();
    }

    private void clamp() {
        if (bmp == null) return;
        float k = base() * s, cw = bmp.getWidth() * k, ch = bmp.getHeight() * k;
        int vw = getWidth(), vh = getHeight();
        tx = cw <= vw ? (vw - cw) / 2 : Math.max(vw - cw, Math.min(0, tx));
        ty = ch <= vh ? (vh - ch) / 2 : Math.max(vh - ch, Math.min(0, ty));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        clamp();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(night ? 0xFF000000 : 0xFFE0E0E0);
        if (bmp == null) return;
        float k = base() * s;
        m.reset();
        m.postScale(k, k);
        m.postTranslate(tx, ty);
        c.drawBitmap(bmp, m, night ? nightPaint : paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        return true;
    }
}
