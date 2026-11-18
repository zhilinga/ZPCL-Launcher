/*
 * ZPCL Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.ui.pcl2.pages

import android.app.Activity
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.utils.network.openLinkInternal

/** 在浏览器里打开一个真实链接。对同包的更新宿主（PclUpgradeHost）开放。 */
internal fun openLink(context: android.content.Context?, url: String) {
    val activity = context as? Activity ?: return
    runCatching {
        activity.openLinkInternal(url)
    }.onFailure {
        PclHintHost.push("无法打开链接：" + url, PclHintTheme.Red)
    }
}
