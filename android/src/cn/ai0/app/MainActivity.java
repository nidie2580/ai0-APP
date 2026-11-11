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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AI0 管理台 v1.4.0
 * 对齐网页端：首页 / 会话 / 模型 / 设置 + 聊天记录 / 诊断 / 关于
 * 首页下拉刷新；未绑定时右下角 + 号绑定。
 */
public class MainActivity extends Activity {

    private static final int BG = 0xff000000, CARD = 0xff0f0f12, HAIR = 0x14ffffff;
    private static final int FG = 0xffffffff, MUT = 0x8cffffff, DIM = 0x59ffffff;
    private static final int TEAL = 0xff4ecdc4, RED = 0xffff6b6b, GREEN = 0xff57d98a, YELLOW = 0xffffe66d;
    private static final int REQ_SCAN = 21;

    private FrameLayout contentWrap;
    private ImageView backBtn;
    private TextView titleText, statusText;
    private LinearLayout nav;
    private ImageView[] navIcons = new ImageView[4];
    private TextView[] navLabels = new TextView[4];
    private View fab;
    private int cur = -1;
    private boolean inSub = false;

    private PullRefreshLayout refreshLayout;
    private TextView vDefModel, vModelCount, vAgent, vGroups, vLoad, vUptime;

    private LinearLayout sessionsList, modelsList;
    private int pendingLoads = 0;

    private String pendingUrl, pendingCode;

    /* ================= 基础 ================= */

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

    private TextView primaryBtn(String s) {
        TextView t = text(s, 14, 0xff06201e, true);
        t.setBackground(bg(TEAL, 12, 0));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        return t;
    }

    private TextView ghostBtn(String s) {
        TextView t = text(s, 13, FG, false);
        t.setBackground(bg(0x14ffffff, 12, 0x1affffff));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(CARD, 18, 0x1affffff));
        c.setPadding(dp(18), dp(14), dp(18), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView cardTitle(String s) {
        TextView t = text(s, 11, DIM, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(HAIR);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** 键值行，value 在 tag */
    private LinearLayout row(String key) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        r.addView(text(key, 13, MUT, false));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        r.addView(spacer);
        TextView v = text("—", 14, FG, true);
        r.addView(v);
        r.setTag(v);
        return r;
    }

    private void kv(LinearLayout r, String val) { if (r != null && r.getTag() != null) ((TextView) r.getTag()).setText(val); }

    private void kv(TextView t, String val) { if (t != null) t.setText(val); }

    private ScrollView wrap(LinearLayout page) {
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(BG);
        s.setFillViewport(true);
        s.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private LinearLayout pageBox() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(4), dp(16), dp(28));
        return p;
    }

    private boolean bound() { return !Api.session().isEmpty(); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /* ================= 壳 ================= */

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
            String t = data.getStringExtra("text");
            if (t != null) onScanText(t);
        }
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(14), dp(18), dp(8));

        backBtn = new ImageView(this);
        backBtn.setImageResource(R.drawable.ic_back);
        backBtn.setColorFilter(FG);
        backBtn.setPadding(dp(6), dp(6), dp(10), dp(6));
        backBtn.setVisibility(View.GONE);
        backBtn.setOnClickListener(v -> backToTab());
        header.addView(backBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        titleText = text("AI0", 22, FG, true);
        header.addView(titleText);

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
        nav.setPadding(dp(6), dp(5), dp(6), dp(5));
        int[] icons = {R.drawable.ic_home, R.drawable.ic_chat, R.drawable.ic_model, R.drawable.ic_settings};
        String[] labels = {"首页", "会话", "模型", "设置"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageResource(icons[i]);
            ic.setColorFilter(0x66ffffff);
            item.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView lb = text(labels[i], 10, 0x66ffffff, false);
            lb.setPadding(0, dp(3), 0, 0);
            item.addView(lb);
            item.setOnClickListener(v -> show(idx));
            navIcons[i] = ic;
            navLabels[i] = lb;
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        col.addView(nav);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout fabWrap = new FrameLayout(this);
        fabWrap.setBackground(bg(TEAL, 28, 0));
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(0xff06201e);
        fabWrap.addView(plus, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        fabWrap.setOnClickListener(v -> showBindDialog(null, null));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
        flp.rightMargin = dp(20);
        flp.bottomMargin = dp(82);
        fab = fabWrap;
        root.addView(fab, flp);

        setContentView(root);
    }

    private void refreshHeader() {
        statusText.setText(bound() ? "已连接" : "未绑定");
        statusText.setTextColor(bound() ? TEAL : MUT);
        backBtn.setVisibility(inSub ? View.VISIBLE : View.GONE);
        titleText.setTextSize(inSub ? 17 : 22);
        if (fab != null) fab.setVisibility(!inSub && cur == 0 && !bound() ? View.VISIBLE : View.GONE);
    }

    private void updateNav() {
        for (int i = 0; i < 4; i++) {
            boolean on = i == cur;
            navIcons[i].setColorFilter(on ? TEAL : 0x66ffffff);
            navLabels[i].setTextColor(on ? TEAL : 0x66ffffff);
        }
    }

    private void show(int idx) {
        cur = idx;
        inSub = false;
        titleText.setText("AI0");
        updateNav();
        refreshHeader();
        render();
    }

    private void backToTab() { show(cur); }

    private void render() {
        contentWrap.removeAllViews();
        if (cur == 0) contentWrap.addView(buildHome());
        else if (cur == 1) contentWrap.addView(buildSessions());
        else if (cur == 2) contentWrap.addView(buildModels());
        else contentWrap.addView(buildSettings());
    }

    private void openSub(String title, View v) {
        inSub = true;
        titleText.setText(title);
        refreshHeader();
        contentWrap.removeAllViews();
        contentWrap.addView(v);
    }

    private void needAuth() {
        if (!bound()) {
            toast("请先绑定服务器");
            openSub("绑定", buildBindPrompt());
        }
    }

    private View buildBindPrompt() {
        LinearLayout p = pageBox();
        LinearLayout c = card();
        c.addView(text("还没有绑定服务器", 16, FG, true));
        TextView d = text("在网页后台「扩展 → App 管理」生成绑定码或二维码，再点右下角 + 号绑定。", 13, MUT, false);
        d.setPadding(0, dp(6), 0, dp(14));
        c.addView(d);
        TextView b = primaryBtn("立即绑定");
        b.setOnClickListener(v -> showBindDialog(null, null));
        c.addView(b);
        p.addView(c);
        return wrap(p);
    }

    /* ================= 首页 ================= */

    private View buildHome() {
        LinearLayout page = pageBox();

        if (!bound()) {
            page.addView(buildBindPromptInner());
            return wrap(page);
        }

        LinearLayout status = card();
        status.addView(cardTitle("运行状态"));
        LinearLayout r1 = row("默认模型");
        LinearLayout r2 = row("模型数量");
        LinearLayout r3 = row("Agent");
        LinearLayout r4 = row("AI 群");
        status.addView(r1); status.addView(divider());
        status.addView(r2); status.addView(divider());
        status.addView(r3); status.addView(divider());
        status.addView(r4);
        vDefModel = (TextView) r1.getTag();
        vModelCount = (TextView) r2.getTag();
        vAgent = (TextView) r3.getTag();
        vGroups = (TextView) r4.getTag();
        page.addView(status);

        LinearLayout srv = card();
        srv.addView(cardTitle("服务器"));
        LinearLayout s1 = row("负载");
        LinearLayout s2 = row("运行时长");
        srv.addView(s1); srv.addView(divider()); srv.addView(s2);
        vLoad = (TextView) s1.getTag();
        vUptime = (TextView) s2.getTag();
        page.addView(srv);

        LinearLayout tools = card();
        tools.addView(cardTitle("工具"));
        tools.addView(navRow(R.drawable.ic_diag, "诊断", "运行状态与配置检查", () -> openDiag()));
        tools.addView(divider());
        tools.addView(navRow(R.drawable.ic_chat, "聊天记录", "最近的问答与回复", () -> openChatLog()));
        tools.addView(divider());
        tools.addView(navRow(R.drawable.ic_info, "关于", "版本与项目信息", () -> openAbout()));
        page.addView(tools);

        ScrollView sv = wrap(page);
        refreshLayout = new PullRefreshLayout(this);
        refreshLayout.setContent(sv, this::loadHome);
        loadHome();
        return refreshLayout;
    }

    private View buildBindPromptInner() {
        LinearLayout c = card();
        c.addView(text("还没有绑定服务器", 17, FG, true));
        TextView d = text("在网页后台「扩展 → App 管理」生成绑定码或二维码，然后点右下角 + 号绑定。", 13, MUT, false);
        d.setPadding(0, dp(6), 0, dp(16));
        c.addView(d);
        TextView b = primaryBtn("立即绑定");
        b.setOnClickListener(v -> showBindDialog(null, null));
        c.addView(b);
        return c;
    }

    private View navRow(int iconRes, String title, String desc, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setColorFilter(MUT);
        r.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(14), 0, dp(8), 0);
        mid.addView(text(title, 14, FG, false));
        TextView dd = text(desc, 11, DIM, false);
        dd.setPadding(0, dp(2), 0, 0);
        mid.addView(dd);
        r.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setColorFilter(0x40ffffff);
        r.addView(chev, new LinearLayout.LayoutParams(dp(16), dp(16)));
        r.setOnClickListener(v -> onClick.run());
        return r;
    }

    private void loadHome() {
        final int total = 2;
        pendingLoads = total;
        Api.req("GET", "api/ext/app-manager/status", null, j -> {
            if (authFailed(j)) { doneHome(); return; }
            if (j.optBoolean("ok")) {
                JSONObject p = j.optJSONObject("plugin");
                if (p != null) {
                    kv(vDefModel, p.optString("defaultModel", "—"));
                    kv(vModelCount, String.valueOf(p.optInt("modelCount", 0)));
                    kv(vAgent, p.optBoolean("agentEnabled") ? "已开启" : "已关闭");
                    kv(vGroups, p.optInt("groupCount", 0) + " 个");
                }
            }
            doneHome();
        });
        Api.req("GET", "api/ext/app-manager/metrics", null, j -> {
            if (j.optBoolean("ok")) {
                kv(vLoad, String.valueOf(j.optDouble("load", 0)));
                kv(vUptime, fmtUptime(j.optLong("uptime", 0)));
            }
            doneHome();
        });
    }

    private void doneHome() {
        pendingLoads--;
        if (pendingLoads <= 0 && refreshLayout != null) refreshLayout.finishRefresh();
    }

    private String fmtUptime(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + " 天 " + h + " 小时";
        if (h > 0) return h + " 小时 " + m + " 分";
        return m + " 分钟";
    }

    private boolean authFailed(JSONObject j) {
        String m = j.optString("msg", "");
        if (!j.optBoolean("ok") && (m.contains("未登录") || m.contains("登录已过期"))) {
            Api.clearAuth();
            toast("登录已过期，请重新绑定");
            show(0);
            return true;
        }
        return false;
    }

    /* ================= 会话 ================= */

    private View buildSessions() {
        LinearLayout page = pageBox();
        if (!bound()) {
            page.addView(buildBindPromptInner());
            return wrap(page);
        }
        sessionsList = new LinearLayout(this);
        sessionsList.setOrientation(LinearLayout.VERTICAL);
        page.addView(sessionsList);
        loadSessions();
        return wrap(page);
    }

    private void loadSessions() {
        sessionsList.removeAllViews();
        sessionsList.addView(text("加载中…", 13, DIM, false));
        Api.req("GET", "api/sessions", null, j -> {
            if (authFailed(j)) return;
            sessionsList.removeAllViews();
            if (!j.optBoolean("ok")) {
                sessionsList.addView(text(j.optString("msg", "加载失败"), 13, RED, false));
                return;
            }
            JSONArray users = j.optJSONArray("data");
            if (users == null || users.length() == 0) {
                sessionsList.addView(text("还没有会话", 13, DIM, false));
                return;
            }
            for (int i = 0; i < users.length(); i++) {
                JSONObject u = users.optJSONObject(i);
                if (u == null) continue;
                String uid = u.optString("userId");
                TextView h = text(uid, 11, DIM, true);
                h.setLetterSpacing(0.05f);
                h.setPadding(dp(4), dp(10), 0, dp(6));
                sessionsList.addView(h);
                JSONArray ss = u.optJSONArray("sessions");
                if (ss == null) continue;
                for (int k = 0; k < ss.length(); k++) {
                    JSONObject s = ss.optJSONObject(k);
                    if (s != null) sessionsList.addView(sessionCard(uid, s));
                }
            }
        });
    }

    private View sessionCard(String uid, JSONObject s) {
        LinearLayout c = card();
        String preview = s.optString("preview", "").replace("\n", " ");
        TextView p = text(preview.isEmpty() ? "（空会话）" : preview, 13, FG, false);
        p.setMaxLines(2);
        p.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(p);
        String meta = s.optInt("msgCount", 0) + " 条 · " + fmtTime(s.optLong("mtime", 0));
        if (s.optBoolean("agentUsed")) meta += " · Agent";
        TextView m = text(meta, 11, DIM, false);
        m.setPadding(0, dp(6), 0, 0);
        c.addView(m);
        c.setOnClickListener(v -> openSession(uid, s.optString("id")));
        return c;
    }

    private void openSession(String uid, String sid) {
        LinearLayout page = pageBox();
        page.addView(text("加载中…", 13, DIM, false));
        openSub("会话详情", wrap(page));
        Api.req("GET", "api/sessions/" + uid + "/" + sid, null, j -> {
            page.removeAllViews();
            if (!j.optBoolean("ok")) {
                page.addView(text(j.optString("msg", "加载失败"), 13, RED, false));
                return;
            }
            JSONArray msgs = j.optJSONArray("data");
            if (msgs == null || msgs.length() == 0) {
                page.addView(text("没有消息", 13, DIM, false));
                return;
            }
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role", "");
                if ("system".equals(role)) continue;
                String content = m.optString("content", "");
                if (content.isEmpty()) continue;
                LinearLayout c = card();
                TextView who = text("user".equals(role) ? "我" : "AI", 11,
                        "user".equals(role) ? TEAL : MUT, true);
                c.addView(who);
                TextView body = text(content, 13, FG, false);
                body.setLineSpacing(dp(3), 1f);
                body.setPadding(0, dp(6), 0, 0);
                c.addView(body);
                page.addView(c);
            }
        });
    }

    private String fmtTime(long ms) {
        if (ms <= 0) return "—";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    /* ================= 模型 ================= */

    private View buildModels() {
        LinearLayout page = pageBox();
        if (!bound()) {
            page.addView(buildBindPromptInner());
            return wrap(page);
        }
        modelsList = new LinearLayout(this);
        modelsList.setOrientation(LinearLayout.VERTICAL);
        page.addView(modelsList);
        loadModels();
        return wrap(page);
    }

    private void loadModels() {
        modelsList.removeAllViews();
        modelsList.addView(text("加载中…", 13, DIM, false));
        Api.req("GET", "api/ext/app-manager/models", null, j -> {
            if (authFailed(j)) return;
            modelsList.removeAllViews();
            if (!j.optBoolean("ok")) {
                modelsList.addView(text(j.optString("msg", "加载失败"), 13, RED, false));
                return;
            }
            final JSONArray list = j.optJSONArray("list");
            Api.req("GET", "api/models/status", null, st -> {
                JSONObject statusByKey = new JSONObject();
                if (st.optBoolean("ok")) {
                    JSONArray arr = st.optJSONArray("models");
                    if (arr != null) for (int i = 0; i < arr.length(); i++) {
                        JSONObject x = arr.optJSONObject(i);
                        if (x == null) continue;
                        try { statusByKey.put(x.optString("key"), x); } catch (Exception ignored) { }
                    }
                }
                if (list == null || list.length() == 0) {
                    modelsList.addView(text("还没有配置模型", 13, DIM, false));
                    return;
                }
                for (int i = 0; i < list.length(); i++) {
                    JSONObject m = list.optJSONObject(i);
                    if (m == null) continue;
                    modelsList.addView(modelRow(m, statusByKey.optJSONObject(m.optString("key"))));
                }
            });
        });
    }

    private View modelRow(JSONObject m, JSONObject st) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(bg(CARD, 16, 0x1affffff));
        row.setPadding(dp(16), dp(14), dp(12), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        boolean def = m.optBoolean("isDefault");
        String meta = m.optString("key") + (m.optBoolean("vision") ? " · 识图" : "") + (m.optBoolean("hasKey") ? "" : " · 未配密钥");
        int dotColor = DIM;
        if (st != null) {
            String status = st.optString("status", "");
            meta += " · " + ("ok".equals(status) ? st.optInt("latencyMs", 0) + "ms" : status);
            dotColor = "ok".equals(status) ? GREEN : ("error".equals(status) ? RED : YELLOW);
        }

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(bg(dotColor, 4, 0));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dlp.rightMargin = dp(8);
        titleRow.addView(dot, dlp);
        titleRow.addView(text(m.optString("name", m.optString("key")), 15, def ? TEAL : FG, true));
        left.addView(titleRow);
        TextView sub = text(meta, 11, DIM, false);
        sub.setPadding(0, dp(4), 0, 0);
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
                                if (r.optBoolean("ok")) { toast(r.optString("msg", "已切换")); loadModels(); }
                                else { b.setEnabled(true); toast(r.optString("msg", "切换失败")); }
                            });
                } catch (Exception e) { toast("构造请求失败"); }
            });
            row.addView(b);
        }
        return row;
    }

    /* ================= 设置 ================= */

    private View buildSettings() {
        LinearLayout page = pageBox();

        LinearLayout acct = card();
        acct.addView(cardTitle("账号"));
        LinearLayout r1 = row("身份");
        LinearLayout r2 = row("服务器");
        acct.addView(r1); acct.addView(divider()); acct.addView(r2);
        kv(r1, Api.identity().isEmpty() ? "—" : Api.identity());
        kv(r2, Api.baseUrl());
        page.addView(acct);

        LinearLayout tools = card();
        tools.addView(cardTitle("更多"));
        tools.addView(navRow(R.drawable.ic_diag, "诊断", "运行状态与配置检查", () -> openDiag()));
        tools.addView(divider());
        tools.addView(navRow(R.drawable.ic_info, "关于", "版本与项目信息", () -> openAbout()));
        page.addView(tools);

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
        return wrap(page);
    }

    /* ================= 子页 ================= */

    private void openAbout() {
        LinearLayout page = pageBox();
        LinearLayout c = card();
        c.addView(cardTitle("AI0 助手"));
        LinearLayout r1 = row("版本");
        LinearLayout r2 = row("客户端");
        LinearLayout r3 = row("项目");
        c.addView(r1); c.addView(divider());
        c.addView(r2); c.addView(divider());
        c.addView(r3);
        kv(r1, "1.4.0");
        kv(r2, "原生 Android · ZXing 扫码");
        kv(r3, "nidie2580/ai0-APP");
        page.addView(c);
        openSub("关于", wrap(page));
    }

    private void openDiag() {
        LinearLayout page = pageBox();
        page.addView(text("加载中…", 13, DIM, false));
        openSub("诊断", wrap(page));
        Api.req("GET", "api/diag", null, j -> {
            page.removeAllViews();
            if (!j.optBoolean("ok")) {
                page.addView(text(j.optString("msg", "加载失败"), 13, RED, false));
                return;
            }
            JSONObject server = j.optJSONObject("server");
            JSONObject web = j.optJSONObject("web");
            JSONObject model = j.optJSONObject("model");
            JSONObject master = j.optJSONObject("master");

            LinearLayout c1 = card();
            c1.addView(cardTitle("运行"));
            LinearLayout a = row("服务状态");
            LinearLayout b = row("监听地址");
            c1.addView(a); c1.addView(divider()); c1.addView(b);
            kv(a, server != null && server.optBoolean("running") ? "运行中" : "未运行");
            kv(b, web == null ? "—" : web.optString("host") + ":" + web.optInt("port"));
            page.addView(c1);

            LinearLayout c2 = card();
            c2.addView(cardTitle("模型"));
            LinearLayout d = row("默认模型");
            LinearLayout e = row("API Base");
            LinearLayout f = row("API Key");
            c2.addView(d); c2.addView(divider()); c2.addView(e); c2.addView(divider()); c2.addView(f);
            kv(d, model == null ? "—" : model.optString("defaultKey") + " / " + model.optString("modelName"));
            kv(e, model != null && model.optBoolean("apiBaseSet") ? "已配置" : "未配置");
            kv(f, model != null && model.optBoolean("apiKeySet") ? "已配置" : "未配置");
            page.addView(c2);

            LinearLayout c3 = card();
            c3.addView(cardTitle("主人"));
            LinearLayout g = row("框架主人");
            LinearLayout h = row("插件主人");
            c3.addView(g); c3.addView(divider()); c3.addView(h);
            kv(g, master == null ? "—" : (master.optBoolean("frameworkHasAny") ? master.optInt("frameworkCount") + " 个" : "未配置"));
            kv(h, master == null ? "—" : (master.optBoolean("pluginHasAny") ? master.optInt("pluginCount") + " 个" : "未配置"));
            page.addView(c3);
        });
    }

    private void openChatLog() {
        LinearLayout page = pageBox();
        page.addView(text("加载中…", 13, DIM, false));
        openSub("聊天记录", wrap(page));
        Api.req("GET", "api/chat-log?limit=30", null, j -> {
            page.removeAllViews();
            if (!j.optBoolean("ok")) {
                page.addView(text(j.optString("msg", "加载失败"), 13, RED, false));
                return;
            }
            JSONObject data = j.optJSONObject("data");
            JSONArray items = data == null ? null : data.optJSONArray("items");
            if (items == null || items.length() == 0) {
                page.addView(text("暂无聊天记录", 13, DIM, false));
                return;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                LinearLayout c = card();
                String uid = it.optString("userId");
                TextView meta = text(uid + " · " + fmtTime(it.optLong("ts", 0)), 11, DIM, false);
                c.addView(meta);
                String q = it.optString("question", "").replace("\n", " ");
                if (q.length() > 160) q = q.substring(0, 160) + "…";
                TextView qv = text(q, 13, FG, false);
                qv.setPadding(0, dp(6), 0, 0);
                c.addView(qv);
                JSONArray reps = it.optJSONArray("replies");
                if (reps != null) for (int k = 0; k < reps.length(); k++) {
                    JSONObject r = reps.optJSONObject(k);
                    if (r == null) continue;
                    String txt = r.optString("text", "").replace("\n", " ");
                    if (txt.length() > 120) txt = txt.substring(0, 120) + "…";
                    TextView rt = text(txt, 12, MUT, false);
                    rt.setPadding(0, dp(8), 0, 0);
                    c.addView(rt);
                }
                page.addView(c);
            }
        });
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
        TextView tip = text("在网页后台「扩展 → App 管理」生成绑定码，或扫码", 12, MUT, false);
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

        TextView cancel = ghostBtn("取消");
        cancel.setOnClickListener(v -> d.dismiss());
        TextView scan = ghostBtn("扫码");
        scan.setOnClickListener(v -> startActivityForResult(new Intent(this, ScanActivity.class), REQ_SCAN));
        TextView go = primaryBtn("绑定");

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = dp(8);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.rightMargin = dp(8);
        btns.addView(cancel, p1);
        btns.addView(scan, p2);
        btns.addView(go, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
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
            if (j.optBoolean("ok")) { toast("绑定成功"); show(0); }
            else toast(j.optString("msg", "绑定失败"));
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
