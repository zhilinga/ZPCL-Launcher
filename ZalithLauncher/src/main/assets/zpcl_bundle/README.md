# zpcl_bundle —— 内置整合包

这个目录由 `.github/workflows/modpack-apk.yml` 在打包时填充，**不要把手上的整合包直接提交进 git**。

| 文件 | 由谁生成 | 说明 |
|---|---|---|
| `payload.zip` | 流水线从 `payload_url` 下载 | 一整份装好的游戏目录，根下直接是 `versions/`、`libraries/`、`assets/`、`mods/`；连 `.minecraft` 一起压缩也能识别 |
| `payload.json` | 流水线生成 | `{ "name", "size", "sha256", "arch" }`，App 首启据此校验并决定是否需要重新解压 |

App 侧的解压逻辑见 `game/provision/UnpackGamePayloadTask.kt`：解压到默认游戏目录，
在目录根写一个 `.zpcl_payload.sha256` 作为完成标记；sha256 变了就重新解压。

目录里没有 `payload.zip` 时，启动页不会出现这一项，等同于普通启动器。
