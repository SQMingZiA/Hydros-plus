package org.cqutosp.hydros.voice

import java.net.URLEncoder

/** 语音助手支持的动作。解析结果只描述意图，真正执行由界面转交给 ViewModel。 */
sealed interface VoiceCommand {
    /** 开始使用某台设备；deviceQuery 为空表示"任意可用设备"。 */
    data class Start(val deviceQuery: String?) : VoiceCommand
    /** 结束当前用水。 */
    data class Stop(val deviceQuery: String?) : VoiceCommand
    /** 查询钱包余额。 */
    data object Balance : VoiceCommand
    /** 查询本月用水消费。 */
    data object Consumption : VoiceCommand
    /** 打开充值页。 */
    data object Recharge : VoiceCommand
    /** 播报可用指令。 */
    data object Help : VoiceCommand
    data class Unknown(val raw: String) : VoiceCommand
}

object VoiceCommands {
    const val EXTRA_COMMAND = "org.cqutosp.hydros.extra.VOICE_COMMAND"
    const val SCHEME = "hydros"
    const val HOST = "voice"
    const val QUERY_PARAM = "q"

    const val HELP_TEXT =
        "你可以说：打开宿舍热水、结束用水、查余额、查本月消费、我要充值。"

    private val startVerbs = listOf("帮我打开", "帮我开", "打开", "开启", "开始使用", "开始", "启动", "开一下", "开水")
    private val stopVerbs = listOf("结束用水", "结束使用", "结束", "停止使用", "停止", "关闭", "关掉", "停水", "关水")
    private val noise = listOf("设备", "水表", "一下", "吧", "的", "请", "帮我", "谢谢", "现在", "我", "想", "要")
    private val punctuation = charArrayOf('，', '。', '！', '？', ',', '.', '!', '?', '、', ' ', '\n', '\t')

    fun parse(raw: String?): VoiceCommand {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return VoiceCommand.Help
        val normalized = text.filterNot { it in punctuation }
        return when {
            containsAny(normalized, "帮助", "怎么用", "能做什么", "有什么功能", "指令") -> VoiceCommand.Help
            containsAny(normalized, "余额", "还有多少钱", "剩多少钱", "钱包") -> VoiceCommand.Balance
            containsAny(normalized, "充值", "充钱", "缴费") -> VoiceCommand.Recharge
            containsAny(normalized, "消费", "花了多少", "用水报告", "账单", "用掉多少", "用水量") -> VoiceCommand.Consumption
            containsAny(normalized, *stopVerbs.toTypedArray()) -> VoiceCommand.Stop(extractDevice(text, stopVerbs))
            containsAny(normalized, *startVerbs.toTypedArray()) -> VoiceCommand.Start(extractDevice(text, startVerbs))
            else -> VoiceCommand.Unknown(text)
        }
    }

    /** 用一句人话说明解析出的动作，便于确认后再执行。 */
    fun describe(command: VoiceCommand): String = when (command) {
        is VoiceCommand.Start -> command.deviceQuery?.let { "开始使用：" + it } ?: "开始使用设备"
        is VoiceCommand.Stop -> "结束当前用水"
        VoiceCommand.Balance -> "查询钱包余额"
        VoiceCommand.Consumption -> "查询本月用水消费"
        VoiceCommand.Recharge -> "打开充值页面"
        VoiceCommand.Help -> "查看可用指令"
        is VoiceCommand.Unknown -> "无法识别的指令"
    }

    /** 把一条口令拼成可被外部助手调用的深链。 */
    fun deepLink(command: String): String =
        "$SCHEME://$HOST?$QUERY_PARAM=" + URLEncoder.encode(command, "UTF-8")

    private fun containsAny(text: String, vararg keywords: String) = keywords.any { it.isNotEmpty() && text.contains(it) }

    private fun extractDevice(raw: String, verbs: List<String>): String? {
        var text = raw
        verbs.sortedByDescending { it.length }.forEach { verb -> text = text.replace(verb, "") }
        noise.forEach { word -> text = text.replace(word, "") }
        text = text.filterNot { it in punctuation }.trim()
        return text.takeIf { it.length >= 2 }
    }

    /** 宽松匹配设备：口令里的关键词出现在设备名称或安装位置上即可。 */
    fun matchesDevice(name: String, location: String, query: String?): Boolean {
        val target = query?.trim().orEmpty()
        if (target.isEmpty()) return false
        return name.contains(target, ignoreCase = true) || location.contains(target, ignoreCase = true)
    }
}
