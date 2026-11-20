package org.cqutosp.hydros.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.cqutosp.hydros.MainActivity

/**
 * 语音助手中转页：把外部助手送来的口令转交给主界面执行。
 *
 * 支持的入口（任意手机厂商的"快捷指令/自定义语音"都能绑定其中一种）：
 *  - 深链：hydros://voice?q=打开宿舍热水
 *  - Intent extra：org.cqutosp.hydros.extra.VOICE_COMMAND
 *  - 系统助手通用 extra：Intent.EXTRA_TEXT 或 query
 */
class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = extractCommand(intent)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            if (!command.isNullOrBlank()) putExtra(VoiceCommands.EXTRA_COMMAND, command)
        }
        startActivity(launch)
        finish()
    }

    private fun extractCommand(intent: Intent?): String? {
        intent ?: return null
        intent.data?.getQueryParameter(VoiceCommands.QUERY_PARAM)?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra(VoiceCommands.EXTRA_COMMAND)?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra("query")?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }
}
