package org.volchnik.dprofiler

import kotlinx.coroutines.flow.MutableSharedFlow
import org.volchnik.dprofiler.model.DirectoryNode
import org.volchnik.dprofiler.model.DiskNode
import org.volchnik.dprofiler.model.FileNode
import org.volchnik.dprofiler.model.NodeEvent
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.TERMINATE
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
fun Path.scanDirectory(
    rootNode: DirectoryNode,
    watchService: WatchService,
    events: MutableSharedFlow<NodeEvent>,
    nodeMap: ConcurrentHashMap<Path, DiskNode>
) {
    val pathStack: ArrayDeque<DirectoryNode> = ArrayDeque()
    visitFileTree {
        onPreVisitDirectory { path, _ ->
            try {
                val newDir = when (pathStack.isEmpty()) {
                    true -> rootNode
                    else -> {
                        val watchKey = path.registerWatchKey(watchService)
                        DirectoryNode(name = path.name, watchKey = watchKey)
                    }
                }
                pathStack.add(newDir)
                CONTINUE
            } catch (e: Exception) {
                TERMINATE
            }
        }

        onPostVisitDirectory { path, _ ->
            try {
                val lastDir = pathStack.removeLast()
                pathStack.lastOrNull()?.addChild(path = path, child = lastDir, events = events, nodeMap = nodeMap)
                CONTINUE
            } catch (e: Exception) {
                TERMINATE
            }
        }

        onVisitFileFailed { _, _ -> CONTINUE }

        onVisitFile { file, _ ->
            try {
                if (file.exists()) {
                    val newFile = FileNode(name = file.name, size = file.fileSize())
                    pathStack.lastOrNull()?.addChild(path = file, child = newFile, events = events, nodeMap = nodeMap)
                }
                CONTINUE
            } catch (e: Exception) {
                TERMINATE
            }
        }
    }
}

fun Path.addRootNode(watchService: WatchService, nodeMap: ConcurrentHashMap<Path, DiskNode>): DirectoryNode {
    val rootNode = DirectoryNode(name = name, watchKey = registerWatchKey(watchService))
    nodeMap[this] = rootNode
    return rootNode
}

fun Path.registerWatchKey(watchService: WatchService): WatchKey =
    register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)