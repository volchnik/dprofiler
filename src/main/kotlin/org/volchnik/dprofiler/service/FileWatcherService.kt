package org.volchnik.dprofiler.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import mu.KotlinLogging
import org.volchnik.dprofiler.addRootNode
import org.volchnik.dprofiler.model.DirectoryNode
import org.volchnik.dprofiler.model.DiskNode
import org.volchnik.dprofiler.model.FileNode
import org.volchnik.dprofiler.model.NodeEvent
import org.volchnik.dprofiler.scanDirectory
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.fileSize
import kotlin.io.path.name

class FileWatcherService(val nodeMap: ConcurrentHashMap<Path, DiskNode>, val events: MutableSharedFlow<NodeEvent>) {

    private val logger = KotlinLogging.logger {}

    val watchService: WatchService = FileSystems.getDefault().newWatchService()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            while (isActive) {
                val monitorKey = watchService.take()

                val dirPath = monitorKey.watchable() as? Path ?: break
                run breaking@{
                    monitorKey.pollEvents().forEach {
                        val eventPath = dirPath.resolve(it.context() as Path)
                        val parent = nodeMap[eventPath.parent]
                        logger.debug { "Monitor event: ${it.kind()} $eventPath" }
                        when (it.kind()) {
                            ENTRY_CREATE -> {
                                if (parent is DirectoryNode) {
                                    parent.addDiskNode(eventPath)
                                }
                            }

                            ENTRY_DELETE -> {
                                val node = nodeMap[eventPath]
                                if (parent is DirectoryNode && node != null) {
                                    parent.removeDiskNode(path = eventPath, node = node)
                                }
                            }

                            ENTRY_MODIFY -> {
                                val node = nodeMap[eventPath]
                                if (parent is DirectoryNode && node is FileNode) {
                                    parent.modifyChild(path = eventPath, child = node, events = events)
                                }
                            }
                        }

                        if (!isActive) return@breaking
                    }
                }
                if (!monitorKey.reset()) {
                    monitorKey.cancel()
                    break
                }
            }
            watchService.close()
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    private fun DirectoryNode.addDiskNode(path: Path) {
        when (path.toFile().isDirectory) {
            true -> {
                val subRootNode = path.addRootNode(watchService = watchService, nodeMap = nodeMap)
                path.scanDirectory(
                    rootNode = subRootNode,
                    watchService = watchService,
                    events = events,
                    nodeMap = nodeMap
                )
                addChild(path = path, child = subRootNode, events = events, nodeMap = nodeMap)
            }
            else -> {
                val fileNode = FileNode(name = path.name, size = path.fileSize())
                addChild(path = path, child = fileNode, events = events, nodeMap = nodeMap)
            }
        }
    }

    private fun DirectoryNode.removeDiskNode(path: Path, node: DiskNode) {
        when (node) {
            is DirectoryNode -> removeChildDirectory(path = path, child = node, nodeMap = nodeMap, events = events)
            is FileNode -> removeChildFile(path = path, child = node, nodeMap = nodeMap, events = events)
        }
    }
}