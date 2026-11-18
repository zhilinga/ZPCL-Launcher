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
import com.zalith.pcl.game.path.GamePathManager
import com.zalith.pcl.utils.logging.Logger
import java.io.File

private const val TAG = "ClientPatch"

/**
 * 随客户端一起下发的「游戏侧默认配置」。
 *
 * 内置整合包是别人装好的目录，options.txt 里少了客户端要的两行：
 * 基础材质包没启用（方块贴图全丢）、云层在 GL4ES 上渲染错乱。
 * 这些没法靠启动器设置解决，只能在游戏目录里改。
 *
 * 做法是就地补行，不覆盖整个文件：用户自己改过的按键、视距、画质都保留，
 * 只在缺这行的时候才补；改完在文件末尾留一个标记，同一个版本不重复应用。
 */
object ClientPatch {
    /** 补丁版本：内容改了就把这个数字 +1，下次启动会重新应用。 */
    const val VERSION = "2"

    private const val MARKER_FILE = ".zpcl_client_patch"

    /** 内置的基础材质包（注意文件名里 .zip 前有一个空格）。 */
    private const val BASE_PACK = "MITE Resource Pack 1.6.4 .zip"

    fun apply(context: Context) = runCatching {
        val gameHome = File(GamePathManager.getDefaultPath())
        if (!gameHome.isDirectory) return@runCatching

        val marker = File(gameHome, MARKER_FILE)
        if (marker.isFile && marker.readText().trim() == VERSION) return@runCatching

        // 版本隔离时游戏读的是 versions/<实例>/options.txt，根目录那份只是打包时留下的。
        // 两处都补，隔离开或关都能对上。
        val targets = mutableListOf(File(gameHome, "options.txt"))
        File(gameHome, "versions").listFiles()?.filter { it.isDirectory }?.forEach {
            targets.add(File(it, "options.txt"))
        }

        val root = File(gameHome, "options.txt")
        targets.forEach { file ->
            if (!file.isFile && file != root && root.isFile) {
                runCatching { root.copyTo(file, overwrite = false) }
            }
            patchOptions(file)
        }
        val cleaned = cleanJunk(gameHome)
        marker.writeText(VERSION)
        Logger.info(TAG, "客户端默认配置已应用，共 " + targets.size + " 份 options.txt")
    }.onFailure {
        Logger.warning(TAG, "客户端默认配置应用失败", it)
    }

    /** 删掉游戏目录里的崩溃转储、旧日志、写锁。返回 (数量, 字节)。 */
    private fun cleanJunk(root: File): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        fun junk(file: File): Boolean {
            val name = file.name.lowercase()
            return name.endsWith(".lck") ||
                    name.startsWith("hs_err_pid") ||
                    name.startsWith("output-client") ||
                    name.startsWith("output-server") ||
                    name.startsWith("suspicious.log") ||
                    file.parentFile?.name?.lowercase() in setOf("logs", "crash-reports")
        }
        runCatching {
            root.walkTopDown().filter { it.isFile && junk(it) }.toList().forEach { file ->
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    count++
                    bytes += size
                }
            }
        }
        if (count > 0) Logger.info(TAG, "已清理无用文件 " + count + " 个，共 " + (bytes / 1024) + " KB")
        return count to bytes
    }

    private fun patchOptions(file: File) {
        val lines = if (file.isFile) file.readLines().toMutableList() else mutableListOf()

        // 云：关（1.6.4 在 GL4ES 下云层会渲染成巨型四边形）
        putOrReplace(lines, "clouds", "false")
        // 基础材质包：只有没有这一行时才补，用户自己选过就不动
        if (lines.none { it.startsWith("resourcePacks:") }) {
            lines.add("resourcePacks:[\"" + BASE_PACK + "\"]")
        }
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n") + "\n")
    }

    private fun putOrReplace(lines: MutableList<String>, key: String, value: String) {
        val index = lines.indexOfFirst { it.startsWith(key + ":") }
        if (index >= 0) lines[index] = key + ":" + value else lines.add(key + ":" + value)
    }
}
