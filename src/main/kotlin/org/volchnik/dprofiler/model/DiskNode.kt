package org.volchnik.dprofiler.model

import java.util.concurrent.ConcurrentHashMap

sealed class DiskNode(val name: String, var parent: DirectoryNode? = null) {
    protected val subscribers: MutableSet<Any> = ConcurrentHashMap.newKeySet()

    abstract val size: Long

    fun subscribe(subscriber: Any): Boolean = subscribers.add(subscriber)

    fun unsubscribe(subscriber: Any): Boolean = subscribers.remove(subscriber)

    override fun toString() = "$name    ${size.formatToReadableSize()}"

    private fun Long.formatToReadableSize(): String = when(this) {
        in 1_000 .. 999_999 -> (this / 1_000.0).formatSize() + " KB"
        in 1_000_000 .. 999_999_999 -> (this / 1_000_000.0).formatSize() + " MB"
        in 1_000_000_000 .. Long.MAX_VALUE -> (this / 1_000_000_000.0).formatSize() + " GB"
        else -> toString() + " B"
    }

    private fun Double.formatSize() = "%.${2}f".format(this)
}