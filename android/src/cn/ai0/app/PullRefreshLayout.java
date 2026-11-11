package cn.ai0.app;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 极简下拉刷新：内容顶部下拉超过阈值 → onRefresh；调用 finishRefresh() 收起。
 * 不依赖 androidx。
 */
public class PullRefreshLayout extends FrameLayout {

    public interface OnRefresh { void onRefresh(); }

    private View content;
    private final TextView hint;
    private OnRefresh listener;
    private float startY = -1;
    private boolean dragging;
    private boolean refreshing;
    private final int threshold;
    private final int slop;

    public PullRefreshLayout(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        threshold = (int) (64 * d);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        hint = new TextView(c);
        hint.setText("下拉刷新");
        hint.setTextSize(12);
        hint.setTextColor(0x8cffffff);
        hint.setGravity(Gravity.CENTER);
        addView(hint, new LayoutParams(LayoutParams.MATCH_PARENT, dp(40), Gravity.TOP));
        hint.setAlpha(0f);
    }

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    public void setContent(View v, OnRefresh l) {
        content = v;
        listener = l;
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void finishRefresh() {
        refreshing = false;
        hint.setText("下拉刷新");
        animate().setDuration(180);
        if (content != null) content.animate().translationY(0).setDuration(180).start();
        hint.animate().alpha(0f).setDuration(150).start();
    }

    private boolean contentAtTop() {
        if (content instanceof ScrollView) return ((ScrollView) content).getScrollY() == 0;
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (refreshing || content == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = e.getY();
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (startY < 0) return false;
                float dy = e.getY() - startY;
                if (dy > slop && contentAtTop()) {
                    dragging = true;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                startY = -1;
                return false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (content == null) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (startY < 0) startY = e.getY();
                float dy = e.getY() - startY;
                if (dy < 0) dy = 0;
                float pull = Math.min(dy * 0.5f, threshold * 1.6f);
                content.setTranslationY(pull);
                hint.setAlpha(Math.min(1f, pull / threshold));
                hint.setText(dy * 0.5f >= threshold ? "松开刷新" : "下拉刷新");
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float pull = content.getTranslationY();
                if (pull >= threshold && listener != null) {
                    refreshing = true;
                    hint.setText("刷新中…");
                    hint.setAlpha(1f);
                    content.animate().translationY(dp(44)).setDuration(160).start();
                    listener.onRefresh();
                } else {
                    content.animate().translationY(0).setDuration(160).start();
                    hint.animate().alpha(0f).setDuration(120).start();
                }
                startY = -1;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
