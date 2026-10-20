package com.barnav.vidsrctv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Mouse-style pointer drawn over the WebView, driven by the D-pad. Fades out when idle. */
public class CursorView extends View {
    float x = -1, y = -1;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final Runnable fade = () -> animate().alpha(0f).setDuration(400);

    public CursorView(Context c) {
        super(c);
        radius = 10 * c.getResources().getDisplayMetrics().density;
        fill.setColor(Color.argb(200, 255, 255, 255));
        ring.setColor(Color.BLACK);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(radius / 4);
        setFocusable(false);
        setClickable(false);
        setAlpha(0f);
    }

    void moveTo(float nx, float ny) {
        x = nx;
        y = ny;
        wake();
        invalidate();
    }

    void pulse() {
        wake();
        animate().alpha(0.3f).setDuration(80).withEndAction(() -> animate().alpha(1f).setDuration(80));
    }

    private void wake() {
        removeCallbacks(fade);
        animate().cancel();
        setAlpha(1f);
        postDelayed(fade, 3000);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (x < 0) { x = w / 2f; y = h / 2f; }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawCircle(x, y, radius, fill);
        c.drawCircle(x, y, radius, ring);
    }
}
