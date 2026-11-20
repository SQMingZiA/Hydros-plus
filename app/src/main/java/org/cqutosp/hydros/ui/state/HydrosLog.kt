package org.cqutosp.hydros.ui.state

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 轻量应用日志：开发者页用来观察最近的调用与异常。
 * 只在内存里保存最近 200 条，退出即清空。
 */
object HydrosLog {
    private const val CAPACITY = 200
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val entries = ArrayDeque<String>(CAPACITY)

    @Synchronized
    fun log(tag: String, message: String) {
        entries.addLast(LocalTime.now().format(timeFormat) + "  " + tag + "  " + message)
        while (entries.size > CAPACITY) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}
