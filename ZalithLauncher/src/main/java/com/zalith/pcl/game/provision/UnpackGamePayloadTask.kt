/*
 * ZPCL Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.game.provision

import android.content.Context
import com.zalith.pcl.components.AbstractUnpackTask
import com.zalith.pcl.components.InstallableItem
import com.zalith.pcl.game.path.GamePathManager
import com.zalith.pcl.game.version.installed.VersionsManager
import com.zalith.pcl.utils.file.readString
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

private const val TAG = "GamePayload"

/** 内置整合包在 APK 里的位置，与 .github/workflows/modpack-apk.yml 的注入路径一致。 */
private const val BUNDLE_DIR = "zpcl_bundle"
private const val PAYLOAD_ZIP = "payload.zip"
private const val PAYLOAD_JSON = "payload.json"

/** 解压完成标记，落在游戏目录根下；内容就是这份 payload 的 sha256。 */
private const val MARKER_NAME = ".zpcl_payload.sha256"

private const val BUFFER_SIZE = 1 shl 16
private const val SPACE_FACTOR = 26L   // 解压倍率按 2.6 估：jar/ogg 本身已压缩

/** 随安装包内置的整合包元信息，由打包流水线生成。 */
data class PayloadMeta(
    val name: String,
    val size: Long,
    val sha256: String,
) {
    companion object {
        fun parse(json: String): PayloadMeta {
            val obj = JSONObject(json)
            return PayloadMeta(
                name = obj.optString("name").ifBlank { "内置整合包" },
                size = obj.optLong("size", 0L),
                sha256 = obj.optString("sha256")
            )
        }
    }
}

/**
 * 把随 APK 内置的整合包（一整份已装好的游戏目录）解压到游戏目录。
 *
 * 它是 [AbstractUnpackTask] 的一个实现，所以直接挂在
 * `SplashActivity.initUnpackItems()` 里，和内置 JRE / 组件走同一套
 * 「需要安装的内容」列表、同一套进度与失败提示。
 *
 * 判定是否已解压靠游戏目录根下的 [MARKER_NAME]：内容与 payload.json 的
 * sha256 一致就算完成；不一致（换了新整合包）则重新解压。
 */
class UnpackGamePayloadTask(private val context: Context) : AbstractUnpackTask() {
    private val assetManager = context.applicationContext.assets
    private var initFailed = false
    private val meta: PayloadMeta?

    init {
        var parsed: PayloadMeta? = null
        runCatching {
            val entries = assetManager.list(BUNDLE_DIR)?.toList().orEmpty()
            if (PAYLOAD_ZIP !in entries || PAYLOAD_JSON !in entries) {
                //没打包整合包，这一项根本不该出现在列表里
                initFailed = true
                return@runCatching
            }
            parsed = PayloadMeta.parse(assetManager.open("$BUNDLE_DIR/$PAYLOAD_JSON").readString())
        }.onFailure { e ->
            Logger.error(TAG, "读取内置整合包信息失败", e)
            initFailed = true
        }
        meta = parsed
        if (parsed == null) initFailed = true
    }

    fun isCheckFailed(): Boolean = initFailed

    fun displayName(): String = meta?.name ?: "内置整合包"

    private fun gameHome(): File = File(GamePathManager.getDefaultPath())

    private fun markerFile(): File = File(gameHome(), MARKER_NAME)

    override fun checkState(): InstallableItem.State {
        val m = meta ?: return InstallableItem.State.NOT_EXISTS
        if (initFailed) return InstallableItem.State.NOT_EXISTS

        val marker = markerFile()
        if (!marker.isFile) return InstallableItem.State.NOT_STARTED

        val done = runCatching { marker.readText().trim() }.getOrNull()
        return when {
            done.isNullOrEmpty() -> InstallableItem.State.NOT_STARTED
            done.equals(m.sha256, ignoreCase = true) -> InstallableItem.State.FINISHED
            else -> InstallableItem.State.PENDING
        }
    }

    override suspend fun run() {
        val m = meta ?: throw IllegalStateException("内置整合包信息缺失")
        val home = gameHome()
        if (!home.isDirectory && !home.mkdirs()) {
            throw IllegalStateException("无法创建游戏目录：${home.absolutePath}")
        }
        ensureEnoughSpace(home, m)

        updateMessage("正在解压游戏文件…")
        var entries = 0
        var written = 0L
        var announceAt = 64L * 1024 * 1024

        assetManager.open("$BUNDLE_DIR/$PAYLOAD_ZIP").use { raw ->
            ZipInputStream(BufferedInputStream(raw, BUFFER_SIZE)).use { zip ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break

                    val relative = normalizeEntryName(entry.name)
                    val target = if (relative.isEmpty()) null else File(home, relative)
                    if (target == null || !isInside(home, target)) {
                        if (relative.isNotEmpty()) {
                            Logger.warning(TAG, "跳过越界条目：${entry.name}")
                        }
                        zip.closeEntry()
                        continue
                    }

                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                written += read
                                if (written >= announceAt) {
                                    announceAt += 64L * 1024 * 1024
                                    updateMessage("正在解压游戏文件… ${written / 1024 / 1024} MB")
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entries++
                }
            }
        }

        markerFile().writeText(m.sha256)
        Logger.info(TAG, "内置整合包解压完成：$entries 项，${written / 1024 / 1024} MB")

        // 解压完立刻补上游戏侧默认配置（关云、启用基础材质包）
        ClientPatch.apply(context)

        //解压完让版本列表认识这份游戏，并把它设为当前版本 —— 用户点开就是「启动游戏」
        updateMessage("正在识别游戏版本…")
        VersionsManager.refresh(TAG)
        VersionsManager.waitForRefresh()
        VersionsManager.versions.value.firstOrNull()?.let { version ->
            VersionsManager.saveCurrentVersion(version.getVersionName())
            Logger.info(TAG, "已把 ${version.getVersionName()} 设为当前版本")
        }
    }

    private fun ensureEnoughSpace(home: File, meta: PayloadMeta) {
        if (meta.size <= 0L) return
        val usable = home.usableSpace
        val needed = (meta.size * SPACE_FACTOR / 10).coerceAtLeast(meta.size)
        if (usable in 1 until needed) {
            throw IllegalStateException(
                "存储空间不足：解压内置游戏需要约 ${needed / 1024 / 1024} MB，" +
                        "当前可用 ${usable / 1024 / 1024} MB。请清理空间后重新打开应用。"
            )
        }
    }

    /**
     * 规范化 zip 条目名：反斜杠转正斜杠、去掉 `./`、`__MACOSX/`，
     * 以及打包时连 `.minecraft` 目录一起压进去留下的那一层。
     */
    private fun normalizeEntryName(raw: String): String {
        var name = raw.replace('\\', '/')
        while (name.startsWith("./")) name = name.removePrefix("./")
        name = name.removePrefix("/")
        if (name.startsWith("__MACOSX/")) return ""
        name = name.removePrefix(".minecraft/")
        if (name == ".minecraft") return ""
        return name.trimEnd('/')
    }

    /** 防止 zip slip：条目必须落在游戏目录内部。 */
    private fun isInside(root: File, child: File): Boolean {
        return runCatching {
            val rootPath = root.canonicalPath + File.separator
            child.canonicalPath.startsWith(rootPath)
        }.getOrDefault(false)
    }
}
