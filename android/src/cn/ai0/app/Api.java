package cn.ai0.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 轻量 API 客户端：会话走 x-ai0-session 头，写操作带 x-csrf-token。 */
public class Api {
    private static SharedPreferences sp;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Cb { void done(JSONObject j); }

    public static void init(Context c) {
        sp = c.getSharedPreferences("ai0", Context.MODE_PRIVATE);
    }

    public static String baseUrl() { return sp.getString("url", "http://45.192.98.189:9178/"); }
    public static String session() { return sp.getString("session", ""); }
    public static String csrf() { return sp.getString("csrf", ""); }
    public static String identity() { return sp.getString("identity", ""); }

    public static void saveAuth(String session, String csrf, String identity) {
        sp.edit().putString("session", session).putString("csrf", csrf).putString("identity", identity).apply();
    }

    public static void saveUrl(String url) { sp.edit().putString("url", url).apply(); }

    public static void clearAuth() {
        sp.edit().remove("session").remove("csrf").remove("identity").apply();
    }

    private static void finish(Cb cb, final JSONObject j) { MAIN.post(() -> cb.done(j)); }

    private static JSONObject err(String m) {
        try { return new JSONObject().put("ok", false).put("msg", m); }
        catch (Exception e) { return new JSONObject(); }
    }

    /** 通用请求（异步，回调在主线程） */
    public static void req(String method, String path, JSONObject body, Cb cb) {
        new Thread(() -> {
            JSONObject out;
            try {
                HttpURLConnection c = open(method, path, body);
                int code = c.getResponseCode();
                String txt = read(c);
                out = txt.startsWith("{") ? new JSONObject(txt) : err("响应异常(" + code + ")");
            } catch (Exception e) {
                out = err("网络错误: " + e.getMessage());
            }
            final JSONObject f = out;
            finish(cb, f);
        }).start();
    }

    private static HttpURLConnection open(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        if (!session().isEmpty()) c.setRequestProperty("x-ai0-session", session());
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            if (!csrf().isEmpty()) c.setRequestProperty("x-csrf-token", csrf());
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        return c;
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while (is != null && (n = is.read(b)) > 0) bo.write(b, 0, n);
        if (is != null) is.close();
        return bo.toString("UTF-8");
    }

    private static String[] cookies(HttpURLConnection c) {
        String sess = "", csrf = "";
        Map<String, List<String>> h = c.getHeaderFields();
        List<String> sc = h == null ? null : h.get("Set-Cookie");
        if (sc != null) {
            for (String v : sc) {
                if (v.startsWith("ai0_session=")) sess = v.split(";")[0].substring(12);
                if (v.startsWith("ai0_csrf=")) csrf = v.split(";")[0].substring(9);
            }
        }
        return new String[]{sess, csrf};
    }

    private static class R { JSONObject json; String sess = "", csrf = ""; }

    private static R postSync(String path, JSONObject body) throws Exception {
        R r = new R();
        HttpURLConnection c = open("POST", path, body);
        String[] ck = cookies(c);
        r.sess = ck[0];
        r.csrf = ck[1];
        r.json = new JSONObject(read(c));
        return r;
    }

    /** 登录：验证码直通；异身份自动轮询审批并在放行后签收 */
    public static void login(String codeId, String code, Cb cb) {
        new Thread(() -> {
            JSONObject out;
            try {
                JSONObject body = new JSONObject().put("codeId", codeId).put("code", code);
                R r = postSync("api/login/code", body);
                out = r.json;
                if (out.optBoolean("ok") && !out.optBoolean("needVerify")) {
                    saveAuth(r.sess, r.csrf, codeId);
                } else if (out.optBoolean("needVerify")) {
                    String pid = out.optString("pendingId");
                    boolean approved = false;
                    for (int i = 0; i < 30 && !approved; i++) {
                        Thread.sleep(3000);
                        approved = postSync("api/login/code/need-verify",
                                new JSONObject().put("pendingId", pid)).json.optBoolean("approved");
                    }
                    if (approved) {
                        R cl = postSync("api/login/code/claim", new JSONObject().put("pendingId", pid));
                        saveAuth(cl.sess, cl.csrf, codeId);
                        out = new JSONObject().put("ok", true);
                    } else {
                        out = err("等待审批超时，请在服务器终端输入：继续操作 " + pid);
                    }
                }
            } catch (Exception e) {
                out = err("登录失败: " + e.getMessage());
            }
            final JSONObject f = out;
            finish(cb, f);
        }).start();
    }

    /** 绑定码绑定：POST /api/ext/app-manager/pair（免登录，绑定码一次性） */
    public static void pair(String url, String code, Cb cb) {
        new Thread(() -> {
            JSONObject out;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url + "api/ext/app-manager/pair").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(new JSONObject().put("code", code).toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                out = new JSONObject(read(c));
                if (out.optBoolean("ok")) {
                    saveUrl(url);
                    saveAuth(out.optString("session"), out.optString("csrf"),
                            out.optString("identity", "app-binding"));
                }
            } catch (Exception e) {
                out = err("绑定失败: " + e.getMessage());
            }
            final JSONObject f = out;
            finish(cb, f);
        }).start();
    }
}
