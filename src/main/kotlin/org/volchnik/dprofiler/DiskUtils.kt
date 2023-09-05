package org.volchnik.dprofiler

import kotlinx.coroutines.flow.MutableSharedFlow
import org.volchnik.dprofiler.model.DirectoryNode
import org.volchnik.dprofiler.model.DiskNode
import org.volchnik.dprofiler.model.FileNode
import org.volchnik.dprofiler.model.NodeEvent
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.SKIP_SUBTREE
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.visitFileTree


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
                    else -> DirectoryNode(name = path.name)
                }
                pathStack.add(newDir)
                CONTINUE
            } catch (e: Exception) {
                SKIP_SUBTREE
            }
        }

        onPostVisitDirectory { path, _ ->
            try {
                val lastDir = pathStack.removeLast()
                pathStack.lastOrNull()?.addChild(path = path, child = lastDir, events = events, nodeMap = nodeMap)
                val watchKey = path.registerWatchKey(watchService)
                lastDir.watchKey = watchKey
                CONTINUE
            } catch (e: Exception) {
                SKIP_SUBTREE
            }
        }

        onVisitFileFailed { _, _ -> CONTINUE }

        onVisitFile { file, attributes ->
            try {
                val newFile = FileNode(name = file.name, size = attributes.size())
                pathStack.lastOrNull()?.addChild(path = file, child = newFile, events = events, nodeMap = nodeMap)
                CONTINUE
            } catch (e: Exception) {
                SKIP_SUBTREE
            }
        }
    }
}

fun Path.addRootNode(watchService: WatchService, nodeMap: ConcurrentHashMap<Path, DiskNode>): DirectoryNode {
    val rootNode = DirectoryNode(name)
    rootNode.watchKey = registerWatchKey(watchService)
    nodeMap[this] = rootNode
    return rootNode
}

fun Path.registerWatchKey(watchService: WatchService): WatchKey =
    register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)