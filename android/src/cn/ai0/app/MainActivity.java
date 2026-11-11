package cn.ai0.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * AI0 助手：WebView 壳
 * - 默认加载 ai0-plugin 网页后台（登录一次，Cookie 持久化）
 * - 长按页面任意位置可修改服务器地址
 * - 返回键优先后退网页
 */
public class MainActivity extends Activity {

    private static final String DEFAULT_URL = "http://45.192.98.189:9178/";
    private WebView web;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("ai0", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0b0d10"));

        TextView bar = new TextView(this);
        bar.setText("AI0 助手");
        bar.setTextColor(Color.parseColor("#e9eef5"));
        bar.setTextSize(15);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        bar.setBackgroundColor(Color.parseColor("#141920"));
        bar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showServerDialog(); return true; }
        });
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " AI0App/1.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showServerDialog(); return true; }
        });

        web.loadUrl(sp.getString("url", DEFAULT_URL));
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private void showServerDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(sp.getString("url", DEFAULT_URL));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("服务器地址")
                .setMessage("填写网页后台地址，例如 http://45.192.98.189:9178/")
                .setView(input)
                .setPositiveButton("保存并打开", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String url = input.getText().toString().trim();
                        if (url.isEmpty()) return;
                        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
                        if (!url.endsWith("/")) url = url + "/";
                        sp.edit().putString("url", url).apply();
                        web.loadUrl(url);
                    }
                })
                .setNeutralButton("恢复默认", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        sp.edit().putString("url", DEFAULT_URL).apply();
                        web.loadUrl(DEFAULT_URL);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
