# ai0-APP

ai0-plugin 的移动端管理：**原生 Android 客户端**（非 WebView）+ 配套「App 管理」扩展。

## 组成

```
extension/            ai0-plugin 扩展（放到 plugins/ai0-plugin/extensions/app-manager/）
├── manifest.json
├── index.js          管理 API：状态 / 指标 / 模型列表 / 切换默认模型
└── web/index.html    备用移动端网页（挂在 /ext/app-manager/）

android/              原生 Android 客户端（无 Gradle：aapt2 + javac + d8 + apksigner）
├── AndroidManifest.xml
├── src/cn/ai0/app/
│   ├── MainActivity.java   登录页 + 首页/模型/设置 三 Tab
│   ├── Api.java            会话/CSRF/登录（含异身份审批轮询）
│   ├── GaugeView.java      圆弧仪表盘（CPU/内存/磁盘）
│   └── …
└── build.sh
```

## 客户端功能（v1.1.0）

- **绑定**（推荐）：后台「App 管理」页生成绑定码/二维码 → App「绑定码绑定」输入，或系统相机扫 `ai0://bind?u=…&c=…` 自动拉起绑定
- **验证码登录**：服务器地址 + 身份(QQ/stdin) + 验证码（网页后台同一套 `/api/login/code`，异身份自动轮询审批）
- **首页**：服务器 CPU / 内存 / 磁盘 圆弧仪表 + 负载 + 运行时长；插件默认模型 / 模型数 / Agent 开关
- **模型**：列表 + 一键「设为默认」
- **设置**：改服务器地址、退出登录、关于
- 会话存本地，请求带 `x-ai0-session` 头，写操作带 `x-csrf-token`

## 安装扩展

```bash
cd /root/XRK-Yunzai/plugins/ai0-plugin/extensions
git clone https://github.com/nidie2580/ai0-APP.git app-manager-tmp
mv app-manager-tmp/extension app-manager && rm -rf app-manager-tmp
# 重启机器人（pkill -9 -x XRK-Yunzai，守护脚本会拉起）
```

## 扩展 API（均需登录，挂在 `/api/ext/app-manager`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/status` | 默认模型 / 模型数 / Agent 开关 / AI 群数 |
| POST | `/pair-code` | 生成绑定码（需登录+CSRF，10 分钟一次性） |
| POST | `/pair` | 免登录绑定：验码 → 签发会话（每 IP 6 次/分钟限速） |
| GET | `/metrics` | CPU% / 内存 / 磁盘 / 负载 / 运行时长（250ms 采样） |
| GET | `/models` | 模型列表（密钥只返回是否已配置） |
| POST | `/default-model` | `{ "key": "deepseek-flash" }` 切换默认模型（CSRF） |

## 构建 APK

```bash
cd android
AI0APP_KS=/path/ai0app.keystore AI0APP_KS_PASS=xxx sh build.sh
```

依赖 aapt2 / android.jar / d8 / apksigner（脚本里 aapt2 默认带 `qemu-x86_64` 前缀，x86_64 机器删掉即可）。
keystore 不入库（CN=AI0）。

## 许可

MIT
