package org.volchnik.dprofiler.view

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import mu.KotlinLogging
import org.volchnik.dprofiler.addRootNode
import org.volchnik.dprofiler.calcInsertPosition
import org.volchnik.dprofiler.model.DirectoryNode
import org.volchnik.dprofiler.model.DiskNode
import org.volchnik.dprofiler.model.EventType
import org.volchnik.dprofiler.model.NodeEvent
import org.volchnik.dprofiler.scanDirectory
import org.volchnik.dprofiler.service.FileWatcherService
import java.awt.GridLayout
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

const val EVENT_BUFFER_CAPACITY = 10_000
const val REPLAY_BUFFER_CAPACITY = 1024

class FileTreeComponent(basePath: Path) : JPanel(GridLayout(1, 0)),
    TreeWillExpandListener {

    private val logger = KotlinLogging.logger {}

    private val events =
        MutableSharedFlow<NodeEvent>(replay = REPLAY_BUFFER_CAPACITY, extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private val nodeMap: ConcurrentHashMap<Path, DiskNode> = ConcurrentHashMap()
    private val nodeTreeMap: ConcurrentHashMap<DiskNode, DefaultMutableTreeNode> = ConcurrentHashMap()
    private val fileWatch = FileWatcherService(nodeMap = nodeMap, events = events)

    private val rootNode = basePath.addRootNode(watchService = fileWatch.watchService, nodeMap = nodeMap)
    private val root = DefaultMutableTreeNode(rootNode)
    private val tree = JTree(root)

    private val defaultScope = CoroutineScope(Dispatchers.Default)
    private val uiScope = CoroutineScope(Dispatchers.Swing)

    init {
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.addTreeWillExpandListener(this)
        (tree.model as DefaultTreeModel).setAsksAllowsChildren(true)

        add(JScrollPane(tree))

        nodeTreeMap[rootNode] = root
        rootNode.subscribe(this)
        root.expand()

        defaultScope.launch {
            basePath.scanDirectory(
                rootNode = rootNode,
                watchService = fileWatch.watchService,
                events = events,
                nodeMap = nodeMap
            )
        }

        uiScope.launch {
            val treeModel = tree.model as DefaultTreeModel
            events.onEach {
                logger.debug { "Got event: $it" }
                when (it.type) {
                    EventType.ADD -> nodeTreeMap[it.node]?.addTreeNode(model = treeModel, node = it.child)
                    EventType.REMOVE -> nodeTreeMap[it.node]?.removeTreeNode(model = treeModel, node = it.child)
                    EventType.REFRESH -> nodeTreeMap[it.node]
                        ?.refreshTreeNode(model = treeModel, parentNode = it.node, node = it.child)
                }
            }.launchIn(this)
        }
    }

    fun shutdown() {
        defaultScope.cancel()
        fileWatch.shutdown()
        uiScope.cancel()
    }

    private fun DefaultMutableTreeNode.addTreeNode(model: DefaultTreeModel, node: DiskNode) {
        val treeChild = DefaultMutableTreeNode(node, node is DirectoryNode)
        model.insertNodeInto(
            treeChild,
            this,
            calcInsertPosition(size = node.size, from = 0, to = this.childCount, getSize = getChildSize, container = this)
        )
        node.subscribe(treeChild)
        nodeTreeMap[node] = treeChild
    }

    private fun DefaultMutableTreeNode.removeTreeNode(model: DefaultTreeModel, node: DiskNode) {
        val treeNode = nodeTreeMap[node]
        if (treeNode != null) {
            node.unsubscribe(treeNode)
            model.removeNodeFromParent(treeNode)
        }
        nodeTreeMap.remove(node)
    }

    private fun DefaultMutableTreeNode.refreshTreeNode(model: DefaultTreeModel, parentNode: DiskNode, node: DiskNode) {
        if (parentNode != node) {
            reorderNode(model = model, node = node)
        }

        val expanded = tree.isExpanded(TreePath(this.path))
        val parentTreeNode = parent as DefaultMutableTreeNode?
        when (expanded) {
            true -> parentTreeNode?.reorderOtherNodes(model = model, node = parentNode)
            else -> parentTreeNode?.reorderNode(model = model, node = parentNode)
        }

        model.nodeChanged(this)
    }

    // Workaround for JTree bug with reordering of expanded nodes
    private fun DefaultMutableTreeNode.reorderOtherNodes(model: DefaultTreeModel, node: DiskNode) {
        val parentNode = node.parent
        if (parentNode != null) {
            parentNode.children.filterNot { it == node }.forEach { child ->
                val treeNode = nodeTreeMap[child]
                if (treeNode != null) {
                    model.removeNodeFromParent(treeNode)
                }
            }
            parentNode.children.filterNot { it == node }.forEach { child ->
                val treeNode = nodeTreeMap[child]
                if (treeNode != null) {
                    model.insertNodeInto(
                        treeNode,
                        this,
                        calcInsertPosition(size = child.size, from = 0, to = this.childCount, getSize = getChildSize, container = this)
                    )
                }
            }
        }
    }

    private fun DefaultMutableTreeNode.reorderNode(model: DefaultTreeModel, node: DiskNode) {
        val treeNode = nodeTreeMap[node]
        if (treeNode != null) {
            model.removeNodeFromParent(treeNode)
            model.insertNodeInto(
                treeNode,
                this,
                calcInsertPosition(size = node.size, from = 0, to = this.childCount, getSize = getChildSize, container = this)
            )
        }
    }

    private fun DefaultMutableTreeNode.expand() {
        when (val node = userObject) {
            is DirectoryNode -> {
                parent?.also { parent ->
                    parent.children().asIterator()
                        .asSequence()
                        .filter { it != this }
                        .forEach {
                            val childNode = (it as DefaultMutableTreeNode)
                            childNode.collapse()
                            tree.collapsePath(TreePath(childNode.path))
                        }
                }

                logger.debug { "dir expand" }
                val model = tree.model as DefaultTreeModel

                this.removeAllChildren()
                node.children.forEach {
                    addTreeNode(model = model, node = it)
                }
                model.reload(this)
            }
        }
    }

    private fun DefaultMutableTreeNode.collapse() {
        when (val node = userObject) {
            is DirectoryNode -> {
                logger.debug { "dir collapse" }
                val model = tree.model as DefaultTreeModel

                node.children.forEach {
                    removeTreeNode(model = model, node = it)
                }
                this.removeAllChildren()
                // JTree bug with disappearing expander workaround
                val dummyChild = DefaultMutableTreeNode()
                model.insertNodeInto(dummyChild, this, childCount)
            }
        }
    }

    override fun treeWillExpand(event: TreeExpansionEvent) {
        val node = event.path.lastPathComponent as DefaultMutableTreeNode
        node.expand()
    }

    override fun treeWillCollapse(event: TreeExpansionEvent) {
        val node = event.path.lastPathComponent as DefaultMutableTreeNode
        node.collapse()
    }

    private val getChildSize: DefaultMutableTreeNode.(Int) -> Long =
        { i -> ((this.getChildAt(i) as DefaultMutableTreeNode?)?.userObject as DiskNode?)?.size ?: 0 }
}
