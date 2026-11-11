package cn.ai0.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** AI0 助手：原生管理端（登录 + 首页/模型/设置） */
public class MainActivity extends Activity {

    private static final int BG = 0xff0b0d10, CARD = 0xff141920, LINE = 0x22ffffff;
    private static final int FG = 0xffe9eef5, MUT = 0x99e9eef5, ACC = 0xff4c8dff;

    private FrameLayout content;
    private ScrollView homePage, modelsPage, settingsPage;
    private View[] tabs;
    private int cur = -1;

    // 首页控件引用
    private GaugeView gCpu, gMem, gDisk;
    private TextView vLoad, vUptime, vDefModel, vModelCount, vAgent, homeMsg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Api.init(this);
        if (Api.session().isEmpty()) buildLogin();
        else buildMain();
    }

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
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

    private TextView button(String s) {
        TextView t = text(s, 13, 0xffffffff, true);
        t.setBackground(round(ACC, 10));
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 14));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        return c;
    }

    private LinearLayout kv(String key) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView k = text(key, 14, MUT, false);
        TextView v = text("…", 14, FG, true);
        r.addView(k);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        r.addView(spacer);
        r.addView(v);
        r.setTag(v);
        return r;
    }

    private void setKv(LinearLayout row, String val) { ((TextView) row.getTag()).setText(val); }

    /* ---------------- 登录 ---------------- */

    private void buildLogin() {
        setContentView(loginRoot());
    }

    private View loginRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(48), dp(28), dp(24));
        scroll.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView logo = text("AI0 助手", 26, FG, true);
        logo.setGravity(Gravity.CENTER);
        box.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = text("ai0-plugin 原生管理端", 13, MUT, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(24));
        box.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.addView(fieldLabel("服务器地址"));
        final EditText url = input(Api.baseUrl(), InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url);
        box.addView(fieldLabel("身份（QQ 号或 stdin）"));
        final EditText qq = input(Api.identity(), InputType.TYPE_CLASS_NUMBER);
        box.addView(qq);
        box.addView(fieldLabel("登录验证码"));
        final EditText code = input("", InputType.TYPE_CLASS_NUMBER);
        box.addView(code);

        LinearLayout gap = new LinearLayout(this);
        gap.setPadding(0, dp(10), 0, dp(10));
        box.addView(gap);

        final TextView btn = button("登 录");
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        btn.setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            String id = qq.getText().toString().trim();
            String cd = code.getText().toString().trim();
            if (u.isEmpty() || id.isEmpty() || cd.isEmpty()) {
                toast("请填写完整"); return;
            }
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
            if (!u.endsWith("/")) u = u + "/";
            Api.saveUrl(u);
            btn.setEnabled(false);
            btn.setText("登录中…");
            Api.login(id, cd, j -> {
                btn.setEnabled(true);
                btn.setText("登 录");
                if (j.optBoolean("ok")) {
                    buildMain();
                } else {
                    toast(j.optString("msg", "登录失败"));
                }
            });
        });
        box.addView(btn);
        return scroll;
    }

    private TextView fieldLabel(String s) {
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
        e.setBackground(round(0xff1a2129, 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /* ---------------- 主界面 ---------------- */

    private void buildMain() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView title = text("AI0 助手", 17, FG, true);
        header.addView(title);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(spacer);
        TextView refresh = text("↻ 刷新", 13, ACC, false);
        refresh.setPadding(dp(10), dp(4), dp(10), dp(4));
        refresh.setOnClickListener(v -> reload());
        header.addView(refresh);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(round(0xff10151b, 0));
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        String[][] items = {{"🏠", "首页"}, {"🧠", "模型"}, {"⚙️", "设置"}};
        tabs = new View[items.length];
        for (int i = 0; i < items.length; i++) {
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER);
            TextView icon = text(items[i][0], 18, FG, false);
            icon.setGravity(Gravity.CENTER);
            TextView label = text(items[i][1], 11, MUT, false);
            label.setGravity(Gravity.CENTER);
            t.addView(icon);
            t.addView(label);
            t.setTag(label);
            t.setOnClickListener(v -> switchTab(t));
            tabs[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        root.addView(bar, blp);
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        switchTab(tabs[0]);
    }

    private void switchTab(View t) {
        int idx = -1;
        for (int i = 0; i < tabs.length; i++) if (tabs[i] == t) idx = i;
        if (idx == cur) return;
        cur = idx;
        for (View tab : tabs) ((TextView) tab.getTag()).setTextColor(MUT);
        ((TextView) t.getTag()).setTextColor(ACC);
        content.removeAllViews();
        if (idx == 0) {
            if (homePage == null) homePage = buildHome();
            content.addView(homePage);
            loadHome();
        } else if (idx == 1) {
            if (modelsPage == null) modelsPage = buildModels();
            content.addView(modelsPage);
            loadModels();
        } else {
            if (settingsPage == null) settingsPage = buildSettings();
            content.addView(settingsPage);
        }
    }

    private void reload() {
        if (cur == 0) loadHome();
        else if (cur == 1) loadModels();
    }

    private ScrollView scrollOf(LinearLayout inner) {
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(BG);
        s.setFillViewport(true);
        s.addView(inner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    /* ---------------- 首页 ---------------- */

    private ScrollView buildHome() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(6), dp(14), dp(20));

        homeMsg = text("", 13, 0xffff9a9a, false);
        homeMsg.setPadding(dp(4), dp(2), dp(4), dp(6));
        page.addView(homeMsg);

        LinearLayout serverCard = card();
        TextView t1 = text("服务器", 15, FG, true);
        serverCard.addView(t1);
        LinearLayout gauges = new LinearLayout(this);
        gauges.setOrientation(LinearLayout.HORIZONTAL);
        gauges.setPadding(0, dp(8), 0, dp(4));
        gCpu = new GaugeView(this);
        gMem = new GaugeView(this);
        gDisk = new GaugeView(this);
        GaugeView[] gs = {gCpu, gMem, gDisk};
        for (GaugeView g : gs) gauges.addView(g, new LinearLayout.LayoutParams(0, dp(104), 1f));
        serverCard.addView(gauges);
        LinearLayout loadRow = kv("负载");
        LinearLayout upRow = kv("运行时长");
        serverCard.addView(loadRow);
        serverCard.addView(upRow);
        page.addView(serverCard);

        LinearLayout pluginCard = card();
        pluginCard.addView(text("插件", 15, FG, true));
        pluginCard.setPadding(dp(14), dp(12), dp(14), dp(6));
        LinearLayout defRow = kv("默认模型");
        LinearLayout cntRow = kv("模型数量");
        LinearLayout agRow = kv("Agent");
        pluginCard.addView(defRow);
        pluginCard.addView(cntRow);
        pluginCard.addView(agRow);
        vDefModel = (TextView) defRow.getTag();
        vModelCount = (TextView) cntRow.getTag();
        vAgent = (TextView) agRow.getTag();
        page.addView(pluginCard);
        vLoad = (TextView) loadRow.getTag();
        vUptime = (TextView) upRow.getTag();
        return scrollOf(page);
    }

    private void loadHome() {
        homeMsg.setText("");
        Api.req("GET", "api/ext/app-manager/metrics", null, j -> {
            if (!j.optBoolean("ok")) { homeFail(j); return; }
            gCpu.set((float) j.optInt("cpu"), "CPU");
            JSONObject mem = j.optJSONObject("mem");
            long mu = mem == null ? 0 : mem.optLong("used");
            long mt = mem == null ? 0 : mem.optLong("total");
            gMem.set(mt > 0 ? mu * 100f / mt : 0, "内存");
            JSONObject disk = j.optJSONObject("disk");
            long dt = disk == null ? 0 : disk.optLong("total");
            long df = disk == null ? 0 : disk.optLong("free");
            gDisk.set(dt > 0 ? (dt - df) * 100f / dt : 0, "磁盘");
            vLoad.setText(String.valueOf(j.optDouble("load", 0)));
            vUptime.setText(fmtUptime(j.optLong("uptime", 0)));
        });
        Api.req("GET", "api/ext/app-manager/status", null, j -> {
            if (!j.optBoolean("ok")) { homeFail(j); return; }
            JSONObject p = j.optJSONObject("plugin");
            if (p == null) return;
            vDefModel.setText(p.optString("defaultModel", "—"));
            vModelCount.setText(String.valueOf(p.optInt("modelCount", 0)));
            vAgent.setText(p.optBoolean("agentEnabled") ? "已开启" : "已关闭");
        });
    }

    private void homeFail(JSONObject j) {
        String m = j.optString("msg", "加载失败");
        homeMsg.setText(m.contains("401") || m.contains("登录") ? "登录已失效，请到设置页退出后重新登录" : m);
    }

    private String fmtUptime(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + " 天 " + h + " 小时";
        if (h > 0) return h + " 小时 " + m + " 分";
        return m + " 分钟";
    }

    /* ---------------- 模型 ---------------- */

    private LinearLayout modelsList;

    private ScrollView buildModels() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(6), dp(14), dp(20));
        TextView hint = text("点击「设为默认」切换默认模型", 12, MUT, false);
        hint.setPadding(dp(4), dp(2), dp(4), dp(8));
        page.addView(hint);
        modelsList = new LinearLayout(this);
        modelsList.setOrientation(LinearLayout.VERTICAL);
        page.addView(modelsList);
        return scrollOf(page);
    }

    private void loadModels() {
        modelsList.removeAllViews();
        TextView loading = text("加载中…", 13, MUT, false);
        modelsList.addView(loading);
        Api.req("GET", "api/ext/app-manager/models", null, j -> {
            modelsList.removeAllViews();
            if (!j.optBoolean("ok")) {
                homeFail(j);
                modelsList.addView(text(j.optString("msg", "加载失败"), 13, 0xffff9a9a, false));
                return;
            }
            org.json.JSONArray list = j.optJSONArray("list");
            if (list == null || list.length() == 0) {
                modelsList.addView(text("还没有配置模型", 13, MUT, false));
                return;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject m = list.optJSONObject(i);
                if (m == null) continue;
                modelsList.addView(modelRow(m));
            }
        });
    }

    private View modelRow(JSONObject m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD, 12));
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        String name = m.optString("name", m.optString("key"));
        boolean def = m.optBoolean("isDefault");
        TextView title = text(name + (def ? "  ·默认" : ""), 14, def ? 0xff6fe3a1 : FG, true);
        String meta = m.optString("key") + (m.optBoolean("vision") ? " · 识图" : "") + (m.optBoolean("hasKey") ? "" : " · 未配密钥");
        TextView subT = text(meta, 11, MUT, false);
        subT.setPadding(0, dp(2), 0, 0);
        left.addView(title);
        left.addView(subT);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.setLayoutParams(llp);
        row.addView(left);

        if (!def) {
            TextView btn = button("设为默认");
            btn.setOnClickListener(v -> {
                btn.setEnabled(false);
                btn.setText("保存中");
                try {
                    Api.req("POST", "api/ext/app-manager/default-model",
                            new JSONObject().put("key", m.optString("key")),
                            r -> {
                                if (r.optBoolean("ok")) {
                                    toast(r.optString("msg", "已切换"));
                                    loadModels();
                                } else {
                                    btn.setEnabled(true);
                                    btn.setText("设为默认");
                                    toast(r.optString("msg", "切换失败"));
                                }
                            });
                } catch (Exception e) {
                    toast("构造请求失败");
                }
            });
            row.addView(btn);
        }
        return row;
    }

    /* ---------------- 设置 ---------------- */

    private ScrollView buildSettings() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(6), dp(14), dp(20));

        LinearLayout conn = card();
        conn.addView(text("连接", 15, FG, true));
        TextView l1 = fieldLabel("服务器地址");
        conn.addView(l1);
        final EditText url = input(Api.baseUrl(), InputType.TYPE_TEXT_VARIATION_URI);
        conn.addView(url);
        TextView save = button("保存地址");
        save.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        slp.topMargin = dp(10);
        save.setLayoutParams(slp);
        save.setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            if (u.isEmpty()) { toast("地址不能为空"); return; }
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
            if (!u.endsWith("/")) u = u + "/";
            Api.saveUrl(u);
            toast("已保存，登录状态可能失效，建议重新登录");
        });
        conn.addView(save);
        page.addView(conn);

        LinearLayout acct = card();
        acct.addView(text("账号", 15, FG, true));
        LinearLayout idRow = kv("当前身份");
        setKv(idRow, Api.identity().isEmpty() ? "—" : Api.identity());
        acct.addView(idRow);
        TextView out = button("退出登录");
        out.setBackground(round(0x33ef4444, 10));
        out.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        olp.topMargin = dp(10);
        out.setLayoutParams(olp);
        out.setOnClickListener(v -> {
            Api.clearAuth();
            buildLogin();
        });
        acct.addView(out);
        page.addView(acct);

        LinearLayout about = card();
        about.addView(text("关于", 15, FG, true));
        TextView a1 = text("AI0 助手 v1.1.0 · 原生客户端", 13, MUT, false);
        a1.setPadding(0, dp(6), 0, 0);
        about.addView(a1);
        TextView a2 = text("github.com/nidie2580/ai0-APP", 13, ACC, false);
        a2.setPadding(0, dp(2), 0, 0);
        about.addView(a2);
        page.addView(about);
        return scrollOf(page);
    }
}
