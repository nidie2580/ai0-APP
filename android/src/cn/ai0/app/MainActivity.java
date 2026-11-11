package cn.ai0.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI0 管理台
 * - 无登录页：直接进首页；未绑定时首页显示空态，右下角 + 号绑定
 * - 绑定：绑定码 / 扫码（系统相机或内置扫码页）
 * - 图标全部为矢量 drawable，不使用 emoji
 */
public class MainActivity extends Activity {

    private static final int BG = 0xff000000, CARD = 0xff0f0f12, HAIR = 0x1affffff;
    private static final int FG = 0xffffffff, MUT = 0x8cffffff, TEAL = 0xff4ecdc4, RED = 0xffff6b6b;
    private static final int REQ_SCAN = 21;

    private FrameLayout contentWrap;
    private LinearLayout nav;
    private ImageView[] navIcons = new ImageView[3];
    private TextView[] navLabels = new TextView[3];
    private View fab;
    private TextView statusText;
    private int cur = -1;

    private ScrollView homePage, modelsPage, settingsPage;
    private LinearLayout modelsList;
    private TextView vDefModel, vModelCount, vAgent, vGroups, vLoad, vUptime;

    private String pendingUrl, pendingCode;

    /* ================= 基础样式 ================= */

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private GradientDrawable bg(int color, float radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private ImageView icon(int res, int tint, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    private TextView primaryBtn(String s) {
        TextView t = text(s, 14, 0xff06201e, true);
        t.setBackground(bg(TEAL, 12, 0));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        return t;
    }

    private TextView ghostBtn(String s) {
        TextView t = text(s, 14, FG, false);
        t.setBackground(bg(0x14ffffff, 12, HAIR));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(CARD, 18, 0x1affffff));
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView cardTitle(String s) {
        TextView t = text(s, 12, MUT, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    /** 一行键值；value 存在 row.tag 里 */
    private LinearLayout row(String key, boolean divider) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(11), 0, dp(11));
        r.addView(text(key, 13, MUT, false));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        r.addView(spacer);
        TextView v = text("—", 14, FG, true);
        r.addView(v);
        r.setTag(v);
        return r;
    }

    private void kv(LinearLayout row, String val) { ((TextView) row.getTag()).setText(val); }

    private ScrollView scrollWrap(LinearLayout page) {
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(BG);
        s.setFillViewport(true);
        s.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private boolean bound() { return !Api.session().isEmpty(); }

    /* ================= 生命周期 ================= */

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Api.init(this);
        buildShell();
        show(0);
        handleBind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleBind(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SCAN && res == RESULT_OK && data != null) {
            String text = data.getStringExtra("text");
            if (text != null) onScanText(text);
        }
    }

    /* ================= 壳 ================= */

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(10));
        header.addView(text("AI0", 22, FG, true));
        View hs = new View(this);
        hs.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(hs);
        statusText = text(bound() ? "已连接" : "未绑定", 12, bound() ? TEAL : MUT, false);
        header.addView(statusText);
        col.addView(header);

        contentWrap = new FrameLayout(this);
        col.addView(contentWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View hair = new View(this);
        hair.setBackgroundColor(0x1affffff);
        col.addView(hair, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(BG);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        int[] icons = {R.drawable.ic_home, R.drawable.ic_model, R.drawable.ic_settings};
        String[] labels = {"首页", "模型", "设置"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView ic = icon(icons[i], 0x66ffffff, 22);
            ic.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView lb = text(labels[i], 10, 0x66ffffff, false);
            lb.setPadding(0, dp(3), 0, 0);
            item.addView(ic);
            item.addView(lb);
            item.setOnClickListener(v -> show(idx));
            navIcons[i] = ic;
            navLabels[i] = lb;
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        col.addView(nav);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 右下角 + 号（未绑定时出现）
        FrameLayout fabWrap = new FrameLayout(this);
        fabWrap.setBackground(bg(TEAL, 28, 0));
        ImageView plus = icon(R.drawable.ic_plus, 0xff06201e, 24);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        fabWrap.addView(plus, plp);
        fabWrap.setOnClickListener(v -> showBindDialog(null, null));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
        flp.rightMargin = dp(20);
        flp.bottomMargin = dp(80);
        fab = fabWrap;
        root.addView(fab, flp);

        setContentView(root);
    }

    private void refreshStatus() {
        if (statusText == null) return;
        statusText.setText(bound() ? "已连接" : "未绑定");
        statusText.setTextColor(bound() ? TEAL : MUT);
        if (fab != null) fab.setVisibility(cur == 0 && !bound() ? View.VISIBLE : View.GONE);
    }

    private void show(int idx) {
        if (idx == cur) return;
        cur = idx;
        for (int i = 0; i < 3; i++) {
            boolean on = i == idx;
            navIcons[i].setColorFilter(on ? TEAL : 0x66ffffff);
            navLabels[i].setTextColor(on ? TEAL : 0x66ffffff);
        }
        contentWrap.removeAllViews();
        if (idx == 0) {
            homePage = buildHome();
            contentWrap.addView(homePage);
            if (bound()) loadHome();
        } else if (idx == 1) {
            modelsPage = buildModels();
            contentWrap.addView(modelsPage);
            if (bound()) loadModels();
        } else {
            settingsPage = buildSettings();
            contentWrap.addView(settingsPage);
        }
        refreshStatus();
    }

    private void reload() { if (cur == 0 && bound()) loadHome(); }

    /* ================= 首页 ================= */

    private ScrollView buildHome() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(6), dp(16), dp(24));

        if (!bound()) {
            LinearLayout c = card();
            c.addView(text("还没有绑定服务器", 17, FG, true));
            TextView d = text("在网页后台「扩展 → App 管理」生成绑定码或二维码，点右下角 + 号绑定。", 13, MUT, false);
            d.setPadding(0, dp(6), 0, dp(16));
            c.addView(d);
            TextView b = primaryBtn("立即绑定");
            b.setOnClickListener(v -> showBindDialog(null, null));
            c.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            page.addView(c);
            return scrollWrap(page);
        }

        LinearLayout status = card();
        status.addView(cardTitle("运行状态"));
        LinearLayout r1 = row("默认模型", true);
        LinearLayout r2 = row("模型数量", true);
        LinearLayout r3 = row("Agent", true);
        LinearLayout r4 = row("AI 群", false);
        status.addView(r1);
        status.addView(divider());
        status.addView(r2);
        status.addView(divider());
        status.addView(r3);
        status.addView(divider());
        status.addView(r4);
        vDefModel = (TextView) r1.getTag();
        vModelCount = (TextView) r2.getTag();
        vAgent = (TextView) r3.getTag();
        vGroups = (TextView) r4.getTag();
        page.addView(status);

        LinearLayout srv = card();
        srv.addView(cardTitle("服务器"));
        LinearLayout s1 = row("负载", true);
        LinearLayout s2 = row("运行时长", false);
        srv.addView(s1);
        srv.addView(divider());
        srv.addView(s2);
        vLoad = (TextView) s1.getTag();
        vUptime = (TextView) s2.getTag();
        page.addView(srv);

        LinearLayout act = card();
        act.addView(cardTitle("操作"));
        TextView rf = ghostBtn("刷新数据");
        rf.setText("刷新数据");
        rf.setOnClickListener(v -> reload());
        act.addView(rf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(act);

        return scrollWrap(page);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0x14ffffff);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private void loadHome() {
        Api.req("GET", "api/ext/app-manager/status", null, j -> {
            if (authFailed(j)) return;
            if (!j.optBoolean("ok")) { toast(j.optString("msg", "加载失败")); return; }
            JSONObject p = j.optJSONObject("plugin");
            if (p == null) return;
            kvText(vDefModel, p.optString("defaultModel", "—"));
            kvText(vModelCount, String.valueOf(p.optInt("modelCount", 0)));
            kvText(vAgent, p.optBoolean("agentEnabled") ? "已开启" : "已关闭");
            kvText(vGroups, p.optInt("groupCount", 0) + " 个");
        });
        Api.req("GET", "api/ext/app-manager/metrics", null, j -> {
            if (authFailed(j)) return;
            if (!j.optBoolean("ok")) return;
            kvText(vLoad, String.valueOf(j.optDouble("load", 0)));
            kvText(vUptime, fmtUptime(j.optLong("uptime", 0)));
        });
    }

    private void kvText(TextView t, String s) { if (t != null) t.setText(s); }

    private String fmtUptime(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + " 天 " + h + " 小时";
        if (h > 0) return h + " 小时 " + m + " 分";
        return m + " 分钟";
    }

    private boolean authFailed(JSONObject j) {
        if (j.optInt("status", 0) == 401 || j.optBoolean("auth")) {
            Api.clearAuth();
            toast("登录已失效，请重新绑定");
            show(0);
            return true;
        }
        return false;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /* ================= 模型 ================= */

    private ScrollView buildModels() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(6), dp(16), dp(24));

        if (!bound()) {
            LinearLayout c = card();
            c.addView(text("尚未绑定", 16, FG, true));
            TextView d = text("绑定后才能查看和管理模型。", 13, MUT, false);
            d.setPadding(0, dp(6), 0, dp(14));
            c.addView(d);
            TextView b = primaryBtn("去绑定");
            b.setOnClickListener(v -> showBindDialog(null, null));
            c.addView(b);
            page.addView(c);
            return scrollWrap(page);
        }

        modelsList = new LinearLayout(this);
        modelsList.setOrientation(LinearLayout.VERTICAL);
        page.addView(modelsList);
        return scrollWrap(page);
    }

    private void loadModels() {
        modelsList.removeAllViews();
        modelsList.addView(text("加载中…", 13, MUT, false));
        Api.req("GET", "api/ext/app-manager/models", null, j -> {
            if (authFailed(j)) return;
            modelsList.removeAllViews();
            if (!j.optBoolean("ok")) {
                modelsList.addView(text(j.optString("msg", "加载失败"), 13, RED, false));
                return;
            }
            JSONArray list = j.optJSONArray("list");
            if (list == null || list.length() == 0) {
                modelsList.addView(text("还没有配置模型", 13, MUT, false));
                return;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject m = list.optJSONObject(i);
                if (m != null) modelsList.addView(modelRow(m));
            }
        });
    }

    private View modelRow(JSONObject m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(bg(CARD, 16, 0x1affffff));
        row.setPadding(dp(16), dp(14), dp(12), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        String name = m.optString("name", m.optString("key"));
        boolean def = m.optBoolean("isDefault");

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(name, 15, def ? TEAL : FG, true);
        left.addView(title);
        String meta = m.optString("key") + (m.optBoolean("vision") ? " · 识图" : "") + (m.optBoolean("hasKey") ? "" : " · 未配密钥");
        TextView sub = text(meta, 12, MUT, false);
        sub.setPadding(0, dp(3), 0, 0);
        left.addView(sub);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(left);

        if (def) {
            TextView tag = text("默认", 11, TEAL, true);
            tag.setBackground(bg(0x1a4ecdc4, 8, 0));
            tag.setPadding(dp(8), dp(3), dp(8), dp(3));
            row.addView(tag);
        } else {
            TextView b = ghostBtn("设为默认");
            b.setTextSize(12);
            b.setPadding(dp(12), dp(8), dp(12), dp(8));
            b.setOnClickListener(v -> {
                b.setEnabled(false);
                try {
                    Api.req("POST", "api/ext/app-manager/default-model",
                            new JSONObject().put("key", m.optString("key")), r -> {
                                if (authFailed(r)) return;
                                if (r.optBoolean("ok")) { toast(r.optString("msg", "已切换")); loadModels(); if (cur == 0) loadHome(); }
                                else { b.setEnabled(true); toast(r.optString("msg", "切换失败")); }
                            });
                } catch (Exception e) { toast("构造请求失败"); }
            });
            row.addView(b);
        }
        return row;
    }

    /* ================= 设置 ================= */

    private ScrollView buildSettings() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(6), dp(16), dp(24));

        LinearLayout acct = card();
        acct.addView(cardTitle("账号"));
        LinearLayout r1 = row("身份", true);
        acct.addView(r1);
        kv(r1, Api.identity().isEmpty() ? "—" : Api.identity());
        acct.addView(divider());
        LinearLayout r2 = row("服务器", false);
        acct.addView(r2);
        kv(r2, Api.baseUrl());
        page.addView(acct);

        if (bound()) {
            LinearLayout act = card();
            TextView out = text("解除绑定", 14, RED, false);
            out.setBackground(bg(0x14ff6b6b, 12, 0x33ff6b6b));
            out.setGravity(Gravity.CENTER);
            out.setPadding(dp(16), dp(12), dp(16), dp(12));
            out.setOnClickListener(v -> {
                Api.clearAuth();
                toast("已解除绑定");
                show(0);
            });
            act.addView(out);
            page.addView(act);
        }

        LinearLayout about = card();
        about.addView(cardTitle("关于"));
        LinearLayout a1 = row("版本", true);
        about.addView(a1);
        kv(a1, "1.3.0");
        about.addView(divider());
        LinearLayout a2 = row("项目", false);
        about.addView(a2);
        kv(a2, "nidie2580/ai0-APP");
        page.addView(about);

        return scrollWrap(page);
    }

    /* ================= 绑定 ================= */

    private void handleBind(Intent i) {
        if (i == null || i.getData() == null) return;
        Uri d = i.getData();
        if (!"bind".equals(d.getHost())) return;
        String u = d.getQueryParameter("u");
        String c = d.getQueryParameter("c");
        if (u == null || c == null) return;
        if (bound()) { toast("已绑定；如需换绑请先在设置里解除绑定"); return; }
        showBindDialog(u, c);
    }

    private void onScanText(String text) {
        try {
            Uri u = Uri.parse(text);
            String url = u.getQueryParameter("u");
            String code = u.getQueryParameter("c");
            if (url == null || code == null) { toast("不是有效的绑定二维码"); return; }
            showBindDialog(url, code);
        } catch (Exception e) {
            toast("二维码解析失败");
        }
    }

    private void showBindDialog(String url, String code) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout outer = new FrameLayout(this);
        outer.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(bg(0xff0c0c0e, 20, 0x1affffff));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));

        box.addView(text("绑定服务器", 18, FG, true));
        TextView tip = text("在网页后台「扩展 → App 管理」生成绑定码，或用二维码扫码", 12, MUT, false);
        tip.setPadding(0, dp(4), 0, dp(14));
        box.addView(tip);

        box.addView(label("服务器地址"));
        final EditText eUrl = input(url == null ? Api.baseUrl() : url, InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(eUrl);

        box.addView(label("绑定码"));
        final EditText eCode = input(code == null ? "" : code, InputType.TYPE_CLASS_TEXT);
        eCode.setTextSize(18);
        box.addView(eCode);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(16), 0, 0);

        TextView scan = ghostBtn("扫码");
        scan.setOnClickListener(v -> startActivityForResult(new Intent(this, ScanActivity.class), REQ_SCAN));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.rightMargin = dp(8);

        TextView go = primaryBtn("绑定");
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);

        TextView cancel = ghostBtn("取消");
        cancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        btns.addView(cancel, clp);
        btns.addView(scan, slp);
        btns.addView(go, glp);
        box.addView(btns);

        TextView more = text("用 QQ 验证码登录", 12, TEAL, false);
        more.setPadding(0, dp(14), 0, 0);
        more.setOnClickListener(v -> { d.dismiss(); showCodeDialog(); });
        box.addView(more);

        go.setOnClickListener(v -> {
            String u2 = normUrl(eUrl.getText().toString());
            String c2 = eCode.getText().toString().trim().toUpperCase();
            if (u2.isEmpty() || c2.isEmpty()) { toast("请填写服务器地址和绑定码"); return; }
            d.dismiss();
            pair(u2, c2);
        });

        outer.addView(box);
        d.setContentView(outer);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();

        if (url != null && code != null) {
            eCode.setText(code);
        }
    }

    private void showCodeDialog() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout outer = new FrameLayout(this);
        outer.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(bg(0xff0c0c0e, 20, 0x1affffff));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));

        box.addView(text("验证码登录", 18, FG, true));
        box.addView(label("服务器地址"));
        final EditText eUrl = input(Api.baseUrl(), InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(eUrl);
        box.addView(label("身份（QQ 号或 stdin）"));
        final EditText eQq = input(Api.identity(), InputType.TYPE_CLASS_TEXT);
        box.addView(eQq);
        box.addView(label("登录验证码"));
        final EditText eCode = input("", InputType.TYPE_CLASS_TEXT);
        box.addView(eCode);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(16), 0, 0);
        TextView cancel = ghostBtn("取消");
        cancel.setOnClickListener(v -> d.dismiss());
        TextView go = primaryBtn("登录");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(8);
        btns.addView(cancel, lp1);
        btns.addView(go, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        box.addView(btns);

        go.setOnClickListener(v -> {
            String u = normUrl(eUrl.getText().toString());
            String id = eQq.getText().toString().trim();
            String cd = eCode.getText().toString().trim();
            if (u.isEmpty() || id.isEmpty() || cd.isEmpty()) { toast("请填写完整"); return; }
            Api.saveUrl(u);
            d.dismiss();
            Api.login(id, cd, r -> {
                if (r.optBoolean("ok")) { toast("登录成功"); show(0); }
                else toast(r.optString("msg", "登录失败"));
            });
        });

        outer.addView(box);
        d.setContentView(outer);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    private void pair(String url, String code) {
        Api.saveUrl(url);
        toast("绑定中…");
        Api.pair(url, code, j -> {
            if (j.optBoolean("ok")) {
                toast("绑定成功");
                show(0);
            } else {
                toast(j.optString("msg", "绑定失败"));
            }
        });
    }

    private String normUrl(String u) {
        if (u == null) return "";
        u = u.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        if (!u.endsWith("/")) u = u + "/";
        return u;
    }

    private TextView label(String s) {
        TextView t = text(s, 12, MUT, false);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private EditText input(String pre, int type) {
        EditText e = new EditText(this);
        e.setText(pre);
        e.setInputType(type);
        e.setTextColor(FG);
        e.setHintTextColor(0x55ffffff);
        e.setTextSize(14);
        e.setBackground(bg(0xff151518, 10, 0x1affffff));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }
}
