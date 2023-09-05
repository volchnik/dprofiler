package org.volchnik.dprofiler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import org.volchnik.dprofiler.model.*
import java.nio.file.Path
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.fileSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryNodeTest {

    @Test
    fun `add child`() {
        val watchKey = mockk<WatchKey>()
        val nodeMap: ConcurrentHashMap<Path, DiskNode> = ConcurrentHashMap()
        val events = mockk<MutableSharedFlow<NodeEvent>> { every { tryEmit(any()) } returns true }
        val dir = DirectoryNode(name = "dir")
        dir.watchKey = watchKey
        val child = FileNode(name = "file", size = 10L)

        // without subscribers
        dir.addChild(Path.of("child/path"), child = child, events = events, nodeMap = nodeMap)
        assertEquals(expected = 1, actual = dir.children.size)
        assertEquals(expected = child, actual = dir.children[0])
        assertEquals(expected = dir, actual = child.parent)
        assertEquals(expected = 10L, actual = dir.size)
        assertEquals(expected = 1, actual = nodeMap.size)
        verify(exactly = 0) { events.tryEmit(any()) }

        // with subscribers
        val child2 = FileNode(name = "file2", size = 20L)
        dir.subscribe(dir)
        dir.addChild(Path.of("child/path/2"), child = child2, events = events, nodeMap = nodeMap)
        assertEquals(expected = 2, actual = dir.children.size)
        assertEquals(expected = child2, actual = dir.children[1])
        assertEquals(expected = dir, actual = child2.parent)
        assertEquals(expected = 30L, actual = dir.size)
        assertEquals(expected = 2, actual = nodeMap.size)

        val expectedEvent = NodeEvent(node = dir, child = child2, type = EventType.ADD)
        verify(exactly = 1) { events.tryEmit(expectedEvent) }
    }

    @Test
    fun `remove child dir`() {
        val watchKey = mockk<WatchKey> { every { cancel() } returns Unit }
        val nodeMap: ConcurrentHashMap<Path, DiskNode> = ConcurrentHashMap()
        val events = mockk<MutableSharedFlow<NodeEvent>> { every { tryEmit(any()) } returns true }
        val dir = DirectoryNode(name = "dir")
        dir.watchKey = watchKey
        val dir2 = DirectoryNode(name = "dir2")
        dir2.watchKey = watchKey
        val child = FileNode(name = "file", size = 10L)

        // without subscribers
        dir2.addChild(Path.of("dir/dir2/child"), child = child, events = events, nodeMap = nodeMap)
        dir.addChild(Path.of("dir/dir2"), child = dir2, events = events, nodeMap = nodeMap)

        dir.removeChildDirectory(path = Path.of("dir/dir2"), child = dir2, nodeMap = nodeMap, events = events)
        assertEquals(expected = 0, actual = dir.children.size)
        assertEquals(expected = 0L, actual = dir.size)
        assertEquals(expected = 1, actual = nodeMap.size)
        verify(exactly = 1) { dir2.watchKey?.cancel() }
        verify(exactly = 0) { events.tryEmit(any()) }

        // with subscribers
        dir.subscribe(dir)
        dir2.addChild(Path.of("dir/dir2/child"), child = child, events = events, nodeMap = nodeMap)
        dir.addChild(Path.of("dir/dir2"), child = dir2, events = events, nodeMap = nodeMap)

        dir.removeChildDirectory(path = Path.of("dir/dir2"), child = dir2, nodeMap = nodeMap, events = events)
        val expectedEventDir = NodeEvent(node = dir, child = dir2, type = EventType.REMOVE)
        verify(exactly = 1) { events.tryEmit(expectedEventDir) }
    }

    @Test
    fun `remove child file`() {
        val watchKey = mockk<WatchKey> { every { cancel() } returns Unit }
        val nodeMap: ConcurrentHashMap<Path, DiskNode> = ConcurrentHashMap()
        val events = mockk<MutableSharedFlow<NodeEvent>> { every { tryEmit(any()) } returns true }
        val dir = DirectoryNode(name = "dir")
        dir.watchKey = watchKey
        val child = FileNode(name = "file", size = 10L)

        // without subscribers
        dir.addChild(Path.of("dir/child"), child = child, events = events, nodeMap = nodeMap)

        dir.removeChildFile(path = Path.of("dir/child"), child = child, nodeMap = nodeMap, events = events)
        assertEquals(expected = 0, actual = dir.children.size)
        assertEquals(expected = 0L, actual = dir.size)
        assertEquals(expected = 0, actual = nodeMap.size)
        verify(exactly = 0) { events.tryEmit(any()) }

        // with subscribers
        dir.subscribe(dir)
        dir.addChild(Path.of("dir/child"), child = child, events = events, nodeMap = nodeMap)

        dir.removeChildFile(path = Path.of("dir/child"), child = child, nodeMap = nodeMap, events = events)
        val expectedEventFile = NodeEvent(node = dir, child = child, type = EventType.REMOVE)
        verify(exactly = 1) { events.tryEmit(expectedEventFile) }
    }

    @Test
    fun `update child`() {
        val watchKey = mockk<WatchKey> { every { cancel() } returns Unit }
        val nodeMap: ConcurrentHashMap<Path, DiskNode> = ConcurrentHashMap()
        val events = mockk<MutableSharedFlow<NodeEvent>> { every { tryEmit(any()) } returns true }
        val path = mockk<Path> { every { fileSize() } returns 100L }
        val dir = DirectoryNode(name = "dir")
        dir.watchKey = watchKey
        val child = FileNode(name = "file", size = 10L)

        // without subscribers
        dir.addChild(Path.of("dir/child"), child = child, events = events, nodeMap = nodeMap)

        assertEquals(expected = 10L, actual = dir.size)
        dir.modifyChild(path = path, child = child, events = events)
        assertEquals(expected = 100L, actual = dir.size)
        verify(exactly = 0) { events.tryEmit(any()) }

        // with subscribers
        dir.subscribe(dir)

        dir.modifyChild(path = path, child = child, events = events)
        verify(exactly = 0) { events.tryEmit(any()) }

        every { path.fileSize() } returns 1000L
        dir.modifyChild(path = path, child = child, events = events)
        assertEquals(expected = 1000L, actual = dir.size)
        val expectedModifyFile = NodeEvent(node = dir, child = child, type = EventType.REFRESH)
        verify(exactly = 1) { events.tryEmit(expectedModifyFile) }
    }
}