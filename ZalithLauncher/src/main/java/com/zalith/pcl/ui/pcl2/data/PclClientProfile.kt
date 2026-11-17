/*
 * ZPCL Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.ui.pcl2.data

/**
 * 这个构建「是什么形态」的集中开关。
 *
 * 整合包专属客户端只需要一份装好的游戏，不需要装游戏、找资源、下整合包，
 * 所以下载相关的入口整体关掉；把开关收在这里，比散在十几个页面里判断要好找。
 */
object PclClientProfile {
    /**
     * 是否提供在线下载（原版安装、资源搜索、整合包下载、下载管理……）。
     *
     * 关掉之后：导航栏不再出现「下载」；任何跳往该版块的调用都会被
     * [com.zalith.pcl.ui.pcl2.PclNavController.go] 挡回去；
     * 实例页里原本指向「下载新 Mod」的按钮改成「从文件安装」或直接去掉。
     */
    const val DOWNLOAD_ENABLED = false
}
