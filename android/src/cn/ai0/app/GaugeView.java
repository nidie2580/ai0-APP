package cn.ai0.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 圆弧仪表盘（仿宝塔：负载/CPU/内存/磁盘） */
public class GaugeView extends View {
    private float pct = 0f;
    private String label = "";
    private int color = 0xff22c55e;

    private final Paint arcBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcVal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GaugeView(Context c) {
        super(c);
        arcBg.setStyle(Paint.Style.STROKE);
        arcBg.setStrokeCap(Paint.Cap.ROUND);
        arcVal.setStyle(Paint.Style.STROKE);
        arcVal.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        sub.setTextAlign(Paint.Align.CENTER);
    }

    public void set(float p, String l) {
        pct = Math.max(0f, Math.min(100f, p));
        label = l == null ? "" : l;
        color = pct < 70 ? 0xff22c55e : (pct < 90 ? 0xfff59e0b : 0xffef4444);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float stroke = 6 * density;
        float pad = stroke + 2 * density;
        RectF rect = new RectF(pad, pad, w - pad, h - pad - 14 * density);

        arcBg.setStrokeWidth(stroke);
        arcBg.setColor(0x22ffffff);
        canvas.drawArc(rect, 135, 270, false, arcBg);

        arcVal.setStrokeWidth(stroke);
        arcVal.setColor(color);
        canvas.drawArc(rect, 135, 270f * pct / 100f, false, arcVal);

        txt.setColor(Color.WHITE);
        txt.setTextSize(15 * density);
        float cy = rect.centerY();
        canvas.drawText((int) pct + "%", w / 2f, cy + 5 * density, txt);

        sub.setColor(0x88ffffff);
        sub.setTextSize(10 * density);
        canvas.drawText(label, w / 2f, h - 4 * density, sub);
    }
}
