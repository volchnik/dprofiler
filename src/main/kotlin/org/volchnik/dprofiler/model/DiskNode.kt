package org.volchnik.dprofiler.model

import java.util.concurrent.ConcurrentHashMap

sealed class DiskNode(val name: String, var parent: DirectoryNode? = null) {
    protected val subscribers: MutableSet<Any> = ConcurrentHashMap.newKeySet()

    abstract val size: Long

    fun subscribe(subscriber: Any): Boolean = subscribers.add(subscriber)

    fun unsubscribe(subscriber: Any): Boolean = subscribers.remove(subscriber)

    fun root(): DiskNode = when (val p = parent) {
        null -> this
        else -> p.root()
    }
}