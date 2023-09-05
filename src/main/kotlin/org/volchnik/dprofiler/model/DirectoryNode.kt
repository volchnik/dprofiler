package org.volchnik.dprofiler.model

import kotlinx.coroutines.flow.MutableSharedFlow
import mu.KotlinLogging
import org.volchnik.dprofiler.model.EventType.ADD
import org.volchnik.dprofiler.model.EventType.REMOVE
import java.nio.file.Path
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import kotlin.io.path.fileSize


class DirectoryNode(name: String) : DiskNode(name) {

    private val logger = KotlinLogging.logger {}

    private val childNodes: ConcurrentHashMap<String, DiskNode> = ConcurrentHashMap()

    private val sizeAccumulator = LongAdder()

    private val countAccumulator = LongAdder()

    var watchKey: WatchKey? = null

    override val size: Long
        get() = sizeAccumulator.sum()

    val count: Long
        get() = countAccumulator.sum()

    val children: List<DiskNode>
        get() = childNodes.values.toList()

    fun addChild(path: Path, child: DiskNode, events: MutableSharedFlow<NodeEvent>, nodeMap: ConcurrentHashMap<Path, DiskNode>) {
        addChild(child = child, events = events)
        nodeMap[path] = child
    }

    private fun addChild(child: DiskNode, events: MutableSharedFlow<NodeEvent>) {
        child.parent = this
        childNodes[child.name] = child

        val diffCount = when (child) {
            is FileNode -> 1L
            is DirectoryNode -> child.count
        }
        update(diffSize = child.size, diffCount = diffCount, events = events)

        if (subscribers.isNotEmpty()) {
            val event = NodeEvent(node = this, child = child, type = ADD)
            val result = events.tryEmit(event)
            if (!result) logger.warn { "Dropped event: $event due to capacity limits" }
        }
    }

    fun removeChildDirectory(
        path: Path,
        child: DirectoryNode,
        nodeMap: ConcurrentHashMap<Path, DiskNode>,
        events: MutableSharedFlow<NodeEvent>,
        nested: Boolean = false
    ) {
        child.childNodes.values.forEach {
            when (it) {
                is DirectoryNode -> removeChildDirectory(
                    path = path.resolve(it.name),
                    child = it,
                    nodeMap = nodeMap,
                    events = events,
                    nested = true
                )
                is FileNode -> removeChildFile(
                    path = path,
                    child = it,
                    nodeMap = nodeMap,
                    events = events,
                    nested = true
                )
            }
        }
        nodeMap.remove(path)
        child.watchKey?.cancel()
        childNodes.remove(child.name)
        if (!nested) {
            postDelete(child = child, events = events)
        }
    }

    fun removeChildFile(
        path: Path,
        child: FileNode,
        nodeMap: ConcurrentHashMap<Path, DiskNode>,
        events: MutableSharedFlow<NodeEvent>,
        nested: Boolean = false
    ) {
        nodeMap.remove(path)
        childNodes.remove(child.name)
        if (!nested) {
            postDelete(child = child, events = events)
        }
    }

    private fun postDelete(child: DiskNode, events: MutableSharedFlow<NodeEvent>) {
        val diffCount = when (child) {
            is FileNode -> 1L
            is DirectoryNode -> child.count
        }
        update(diffSize = -child.size, diffCount = -diffCount, events = events)

        if (subscribers.isNotEmpty()) {
            val event = NodeEvent(node = this, child = child, type = REMOVE)
            val result = events.tryEmit(event)
            if (!result) logger.warn { "Dropped event: $event due to capacity limits" }
        }
    }

    fun modifyChild(path: Path, child: FileNode, events: MutableSharedFlow<NodeEvent>) {
        val newSize = path.fileSize()
        val oldSize = child.size
        child.size = newSize
        update(diffSize = newSize - oldSize, diffCount = 0L, events = events, child = child)
    }

    private fun update(diffSize: Long, diffCount: Long, events: MutableSharedFlow<NodeEvent>, child: DiskNode? = null) {
        if (diffSize == 0L && diffCount == 0L) return

        sizeAccumulator.add(diffSize)
        countAccumulator.add(diffCount)

        if (subscribers.isNotEmpty()) {
            val event = NodeEvent(node = this, child = child ?: this, type = EventType.REFRESH)
            val result = events.tryEmit(event)
            if (!result) logger.warn { "Dropped event: $event due to capacity limits" }
        }

        parent?.update(diffSize = diffSize, diffCount = diffCount, events = events)
    }
}