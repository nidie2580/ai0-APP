# ai0-APP

ai0-plugin 的移动端管理扩展 + 配套 Android 客户端。

## 组成

```
extension/            ai0-plugin 扩展（放到 plugins/ai0-plugin/extensions/app-manager/）
├── manifest.json
├── index.js          管理 API：状态 / 模型列表 / 切换默认模型
└── web/index.html    移动端管理页（挂在 /ext/app-manager/）

android/              Android 客户端（WebView 壳，打包管理页）
├── AndroidManifest.xml
├── src/cn/ai0/app/MainActivity.java
├── res/…             图标与文案
└── build.sh          无 Gradle 构建脚本（aapt2 + javac + d8 + apksigner）
```

## 安装扩展

```bash
# 在服务器上
cd /root/XRK-Yunzai/plugins/ai0-plugin/extensions
git clone https://github.com/nidie2580/ai0-APP.git app-manager-tmp
mv app-manager-tmp/extension app-manager
rm -rf app-manager-tmp
systemctl restart xrk-yunzai   # 或 pkill -9 -x XRK-Yunzai，守护脚本会拉起
```

装好后打开网页后台 →「🧩 扩展」→ App 管理 →「打开」，即可看到移动端页面。

## 扩展 API（均需登录，挂在 `/api/ext/app-manager`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/status` | 插件状态、默认模型、provider 数、扩展数 |
| GET | `/models` | 模型列表（密钥只返回是否已配置） |
| POST | `/default-model` | `{ "key": "deepseek-flash" }` 切换默认模型 |

## 构建 APK

```bash
cd android
sh build.sh          # 产出 ai0-app.apk
```

依赖与 SBZYB 助手相同的无 Gradle 工具链（aapt2 / android.jar / d8 / apksigner）。
脚本里 `AAPT2` 用了 `qemu-x86_64 -L /opt/x86` 前缀（Alpine aarch64 环境）；普通 x86_64 机器删掉该前缀即可。

## 客户端说明

- 首次启动加载 `http://45.192.98.189:9178/`（网页后台）
- **长按页面**可修改服务器地址（会保存到本地）
- 登录一次后 Cookie 持久化，下次直接进后台
- 支持返回键后退、文件上传（系统选择器）

## 许可

MIT
