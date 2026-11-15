# ZPCL Launcher

面向 **Android 设备** 的 [Minecraft: Java Edition](https://www.minecraft.net/) 启动器。

启动核心沿用 [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) 与
[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)，界面按
[Plain Craft Launcher 2](https://github.com/Hex-Dragon/PCL2)（下称 PCL2）的版式与交互重做，
使用 **Jetpack Compose** 编写。

> 当前版本：**1.5.4（versionCode 200076）**
> 本项目是 Zalith Launcher 2 的**非官方修改版**，与上游官方无关联。
> 依 GPLv3 第 7 条附加条款，名称中不含上游原名 `ZalithLauncher` 及其缩写 `ZL`，
> 并在启动页与关于页标注「非官方修改版」。

修改声明、与上游的边界、名称与标识的处理、问题该提给谁，都写在 **[NOTICE.md](NOTICE.md)**。
本文件只讲这个启动器本身。

---

## 目录

- [功能](#功能)
- [两条产线](#两条产线)
- [构建](#构建)
- [插件开发](#插件开发)
- [目录结构](#目录结构)
- [版本与发布](#版本与发布)
- [已知限制](#已知限制)
- [许可与致谢](#许可与致谢)

---

## 功能

### 界面

- 首页 / 下载 / 版本设置 / 联机 / 设置，按 PCL2 的分栏结构重排（左栏列表 + 右侧内容区）
- 鼠标可用：列表项、按钮、下拉菜单都有指向高亮；低缩放或矮窗口下弹窗正文可滚动、按钮固定在底部
- 二级页是真正的页面栈：资源详情 → 前置资源 → 再返回，逐层退回而不是直接跳回首页

### 下载

- **原版游戏**：选版本 → 装游戏本体，可同时安装 Fabric / Forge / NeoForge / Quilt / OptiFine
- **社区资源**：Mod / 整合包 / 资源包 / 光影包 / 存档
  - 来源默认 **Modrinth**（免密钥）；CurseForge 需要 `x-api-key`，本构建不内置，选中会拿不到结果
  - 默认 **32 条并发连接**，可在「设置 → 下载 → 最大线程数」调整，下一次下载生效
  - 下载源可切「自动 / 官方 / 镜像」，失败会自动换源重试

### 实例

- 新建、复制、删除、重置；每个实例有独立的版本设置（渲染器、内存、Java 参数等）
- 整合包：本地导入与系统分享接入，安装前可自定义版本名，失败自动清理不完整版本
- 导出整合包：按 PCL 的匹配规则打包，规则里的特殊字符（如 `[宝]`）不会再把启动器打崩

### 运行

- 多渲染器可选，并为 Sodium / Iris 这类硬性要求桌面 GL 3.3+ 的模组做自动兼容
- 陶瓦（Terracotta）联机模块内置，未启用时联机页给出引导
- 更新检查：`latest_version_md.json` + 官网分流，更新弹窗里常驻「从官网下载（若下载缓慢）」


写插件请看 **[docs/PLUGIN_GUIDE.md](docs/PLUGIN_GUIDE.md)**（目录结构、`plugin.json`、
`pages.json`、积木与动作清单、数据通道、最小示例与排错表）。

---

## 两条产线

仓库里有两份同构工程，改动需要手动同步：

| | `Zalith-PlainCraftLauncher/` | `Zalith-PlainCraftLauncher-Release/` |
|---|---|---|
| 用途 | 日常开发、调试 | 对外发布 |
| 包名 | `com.zalith.pcl.debug` | `com.zalith.pcl` |
| 构建类型 | debug | release（正式签名） |
| 正版（微软）登录 | 无 | 有 |
| 隐藏主题 | 无 | 有 |

比对两条线时务必带 `--strip-trailing-cr`，否则 CRLF 差异会把结果淹掉：

```bash
diff -rq --strip-trailing-cr Zalith-PlainCraftLauncher/ZalithLauncher/src \
                       Zalith-PlainCraftLauncher-Release/ZalithLauncher/src
```

---

## 构建

### 环境

- **JDK 17**（`JAVA_HOME` 指向 JDK 17；JDK 8 会在 Gradle 阶段直接失败）
- Android SDK：`minSdk 26`、`targetSdk 34`，`compileSdk` 见
  [`ZalithLauncher/build.gradle.kts`](ZalithLauncher/build.gradle.kts)
- 首次构建需要联网拉依赖

### 命令

```bash
# 调试包（单架构，约 210 MB）
export JAVA_HOME=/path/to/jdk17
./gradlew :ZalithLauncher:assembleDebug -Darch=arm64

# 正式包（需要签名配置，见下）
./gradlew :ZalithLauncher:assembleRelease -Darch=arm64
```

`-Darch` 只认这四个值：`arm`、`arm64`、`x86`、`x86_64`；不传则打包含全部 ABI 的包（约 400 MB）。

| 传入 | 产物 |
|---|---|
| `arm64` | `ZalithLauncher-<版本>-arm64-v8a.apk` |
| `arm` | `ZalithLauncher-<版本>-armeabi-v7a.apk` |
| `x86` / `x86_64` | 对应架构 |
| 不传 | 全 ABI（无架构后缀） |

产物在 `ZalithLauncher/build/outputs/apk/{debug,release}/`。

### 签名

正式包读取 `ZalithLauncher/build.gradle.kts` 里 `signingConfigs` 配置的 keystore，
密码从 `.store_password.txt` / `.key_password.txt`（或环境变量）读取，两者都在 `.gitignore` 中。
**签名文件请自行备份**：换签名后已安装的用户必须卸载重装，且无法覆盖升级。

### 微软登录

源码不携带任何 OAuth `client_id`。需要正版登录（仅正式线）时，把 Azure 应用的客户端 ID 写进
`.oauth_client_id.txt`，或设置环境变量 `OAUTH_CLIENT_ID` 后再构建。未配置时离线登录不受影响。

---

## 插件开发

插件是一个普通 APK，只要包内带 `assets/zplugin/plugin.json`，启动器就把它当插件识别。
页面用 JSON 描述（`pages.json`），启动器只负责渲染，具体内容与数据都在插件里。

完整协议见 **[docs/PLUGIN_GUIDE.md](docs/PLUGIN_GUIDE.md)**，包括：

- `plugin.json` 全部字段（含 `entry` / `label` / `ui`）
- `pages` / `sections`（整块接管版块）/ `entries`（往左栏加一条）
- 积木（title、text、card、button、switch、status…）与动作（page、launch、open、copy、install、app…）
- ContentProvider 数据通道：`content://<包名>.pclplugin` 的 `call(method, arg)`
- 升级注意：**插件 APK 的 `versionCode` 必须递增**，否则启动器不会重新解包

---

## 目录结构

| 路径 | 内容 |
|---|---|
| `ZalithLauncher/` | 启动器主模块（Gradle 模块名沿用上游，**只是路径**；程序显示名与标识不含上游名） |
| `…/ui/pcl2/` | PCL2 界面：外壳、左栏、各版块页面 |
| `…/ui/pcl2/components/` | PCL 控件库（按钮、列表项、卡片、弹窗、下拉） |
| `…/game/` | 下载、启动、渲染器、插件管理 |
| `…/game/plugin/` | 插件识别与安装（以 `assets/zplugin/plugin.json` 为识别依据） |
| `docs/PLUGIN_GUIDE.md` | 插件开发指南 |
| `docs/THIRD_PARTY.md` | 第三方组件与许可清单 |
| `latest_version_md.json` | 应用内「检查更新」读取的版本清单 |
| `CardGrid/` `ColorPicker/` `InputMap/` `LayerController/` `LWJGL/` `Terracotta/` | 渲染、输入、联机等子模块 |
| `.github/workflows/` | CI：编译校验与推送检查（不含打包上传） |

---

## 版本与发布

本仓库**只承载源代码**，不存放安装包：仓库与 GitHub Releases 都不挂 APK，
分发包由官网（`url_home` 指向的站点）自行提供。

1. 改 `ZalithLauncher/gradle.properties` 的 `launcher_version_name` / `launcher_version_code`
   —— **versionCode 必须递增**，否则已安装的设备无法覆盖升级；两条产线都要改
2. 同步改 `latest_version_md.json` 的 `version` / `code`，并在 `default_body` 里写这一版的说明
   （应用内「检查更新」读的就是它）
3. 需要打标签时：`git tag -a v<版本> -m "ZPCL Launcher <版本>" && git push origin main --tags`
   —— 标签只标记源码节点，不触发任何上传安装包的流水线

`launcher_name` 与 `CLIENT_TOKEN_SEED` 属于登录与数据目录的标识，**不要修改**。

---

## 已知限制

- 仅支持 Android（`minSdk 26`）；iOS / 桌面端不在范围内
- CurseForge 官方接口需要 `x-api-key`，未内置时只能使用 Modrinth 或镜像
- 正版登录只在正式线可用，且必须自备 `client_id`
- 皮肤插件同一时间只生效一个；多个一起启用会互相覆盖外观
- 插件升级后必须让插件 APK 的 `versionCode` 递增，否则启动器不会重新解包（界面会停留在旧配置）

---

## 许可与致谢

本项目遵循 **[GPL-3.0](LICENSE)**。上游在 GPLv3 第 7 条下附有名称与标注要求，本仓库已照办：
名称不使用上游原名及缩写，启动页与关于页标注「非官方修改版」。

修改声明、名称处理、源码获取与署名见 **[NOTICE.md](NOTICE.md)**；第三方组件与许可见
**[docs/THIRD_PARTY.md](docs/THIRD_PARTY.md)**。

`Zalith Launcher` / `PCL2` 的名称与标识归各自作者所有；本项目只在「基于谁」的意义上提及它们，
不用作自身名称，也不声称与其有关联。

感谢 [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2)、
[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)、
[PCL2](https://github.com/Hex-Dragon/PCL2) 以及所有上游依赖的作者。
