package cn.ai0.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

/** 扫码页：Camera 预览 + ZXing 解码，识别 ai0://bind 二维码 */
public class ScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final int REQ_CAM = 7;
    private Camera camera;
    private SurfaceView sv;
    private volatile boolean decoding = false;
    private volatile boolean finished = false;

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        sv = new SurfaceView(this);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.getHolder().addCallback(this);

        // 取景框
        FrameLayout box = new FrameLayout(this);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setColor(0x00000000);
        stroke.setStroke(dp(2), 0xff4ecdc4);
        stroke.setCornerRadius(dp(16));
        box.setBackground(stroke);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(240), dp(240), Gravity.CENTER);
        root.addView(box, blp);

        TextView hint = new TextView(this);
        hint.setText("将二维码对准取景框");
        hint.setTextColor(0xccffffff);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        hlp.bottomMargin = dp(56);
        root.addView(hint, hlp);

        // 关闭按钮
        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(0xffffffff);
        close.setPadding(dp(14), dp(14), dp(14), dp(14));
        close.setOnClickListener(v -> finish());
        root.addView(close, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.END));

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAM && (r.length == 0 || r[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openCamera(SurfaceHolder holder) {
        try {
            camera = Camera.open(0);
            Camera.Parameters p = camera.getParameters();
            p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            Camera.Size best = null;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (best == null || Math.abs(s.width * s.height - 1280 * 720) < Math.abs(best.width * best.height - 1280 * 720)) best = s;
            }
            if (best != null) p.setPreviewSize(best.width, best.height);
            camera.setParameters(p);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "相机启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) { openCamera(h); }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) { release(); }

    private void release() {
        try {
            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            }
        } catch (Exception ignored) { }
        camera = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        release();
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera cam) {
        if (decoding || finished || data == null) return;
        decoding = true;
        final Camera.Size size = cam.getParameters().getPreviewSize();
        new Thread(() -> {
            try {
                String text = decode(data, size.width, size.height);
                if (text != null && !finished) {
                    finished = true;
                    final String out = text;
                    runOnUiThread(() -> {
                        setResult(RESULT_OK, new Intent().putExtra("text", out));
                        finish();
                    });
                }
            } finally {
                decoding = false;
            }
        }).start();
    }

    private String decode(byte[] data, int w, int h) {
        try {
            String r = tryDecode(data, w, h);
            if (r != null) return r;
            // 预览数据是横向的，再试 90° 旋转
            byte[] rot = rotate90(data, w, h);
            return tryDecode(rot, h, w);
        } catch (Exception e) {
            return null;
        }
    }

    private String tryDecode(byte[] y, int w, int h) {
        try {
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false);
            BinaryBitmap bmp = new BinaryBitmap(new HybridBinarizer(src));
            return new MultiFormatReader().decode(bmp).getText();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] rotate90(byte[] src, int w, int h) {
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[x * h + (h - 1 - y)] = src[y * w + x];
            }
        }
        return out;
    }
}
