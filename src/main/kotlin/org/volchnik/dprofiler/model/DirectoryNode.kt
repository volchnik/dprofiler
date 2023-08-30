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


class DirectoryNode(name: String, val watchKey: WatchKey) : DiskNode(name) {

    private val logger = KotlinLogging.logger {}

    private val childNodes: ConcurrentHashMap<String, DiskNode> = ConcurrentHashMap()

    private val sizeAccumulator = LongAdder()

    override val size: Long
        get() = sizeAccumulator.sum()

    val children: List<DiskNode>
        get() = childNodes.values.toList()

    fun addChild(path: Path, child: DiskNode, events: MutableSharedFlow<NodeEvent>, nodeMap: ConcurrentHashMap<Path, DiskNode>) {
        addChild(child = child, events = events)
        nodeMap[path] = child
    }

    private fun addChild(child: DiskNode, events: MutableSharedFlow<NodeEvent>) {
        child.parent = this
        childNodes[child.name] = child
        updateSize(diff = child.size, events = events)

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
        child.watchKey.cancel()
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
        updateSize(diff = -child.size, events = events)

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
        updateSize(diff = newSize - oldSize, events = events, child = child)
    }

    private fun updateSize(diff: Long, events: MutableSharedFlow<NodeEvent>, child: DiskNode? = null) {
        if (diff == 0L) return

        sizeAccumulator.add(diff)

        if (subscribers.isNotEmpty()) {
            val event = NodeEvent(node = this, child = child ?: this, type = EventType.REFRESH)
            val result = events.tryEmit(event)
            if (!result) logger.warn { "Dropped event: $event due to capacity limits" }
        }

        parent?.updateSize(diff = diff, events = events)
    }
}