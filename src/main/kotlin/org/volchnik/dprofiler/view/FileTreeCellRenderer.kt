package org.volchnik.dprofiler.view

import org.volchnik.dprofiler.model.DirectoryNode
import org.volchnik.dprofiler.model.FileNode
import java.awt.*
import java.awt.FlowLayout.LEFT
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import kotlin.math.max
import kotlin.math.min


const val MAX_SIZE_STRIPE_WIDTH = 600
class FileTreeCellRenderer(val tree: JTree) : TreeCellRenderer {

    private val panel = DiskNodeJPanel(FlowLayout(LEFT))

    private val fileName = JLabel()

    private val fileSize = JLabel()

    private val fileCount = JLabel()

    private val openFolderIcon = ImageIcon(javaClass.getResource("/icons/open_folder.png"))

    private val closedFolderIcon = ImageIcon(javaClass.getResource("/icons/closed_folder.png"))

    private val fileIcon = ImageIcon(javaClass.getResource("/icons/file.png"))

    init {
        fileName.setForeground(Color.BLACK)
        panel.add(fileName)

        panel.add(Box.createHorizontalStrut(10));

        fileSize.setForeground(Color.BLUE)
        panel.add(fileSize)

        panel.add(Box.createHorizontalStrut(10));

        fileCount.setForeground(Color.BLUE)
        panel.add(fileCount)
    }

    inner class DiskNodeJPanel(layout: LayoutManager) : JPanel(layout) {
        var size: Long = 0L
        var maxSize: Long = 1L
        var row: Int = 0
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val r: Rectangle = tree.getRowBounds(row)
            val sizeLevel = min(max(0.0, size / maxSize.toDouble()), 1.0)
            g.color = Color(247, (211 * (1.0 - sizeLevel)).toInt(), 90)
            g.fillRect(0, 0, (MAX_SIZE_STRIPE_WIDTH * sizeLevel).toInt(), r.height);
        }

        override fun getPreferredSize(): Dimension {
            val d = super.getPreferredSize()
            d.width = MAX_SIZE_STRIPE_WIDTH
            return d
        }
    }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        panel.row = row

        when (value) {
            is DefaultMutableTreeNode -> {
                when (val node = value.userObject) {
                    is DirectoryNode -> {
                        fileName.icon = if (expanded) openFolderIcon else closedFolderIcon
                        fileName.text = node.name
                        fileSize.text = "size: ${node.size.formatToReadableSize()}"
                        fileCount.text = "files: ${node.count}"
                        panel.size = node.size
                        panel.maxSize = node.root().size
                    }
                    is FileNode -> {
                        fileName.icon = fileIcon
                        fileName.text = node.name
                        fileSize.text = "size: ${node.size.formatToReadableSize()}"
                        fileCount.text = ""
                        panel.size = node.size
                        panel.maxSize = node.root().size
                    }
                }
                panel.setEnabled(tree.isEnabled);
                panel.setOpaque(false)
            }
        }

        return panel
    }

    private fun Long.formatToReadableSize(): String = when(this) {
        in 1_024 .. 1_048_575 -> (this / 1_024.0).formatSize() + " KB"
        in 1_048_576 .. 1_073_741_823 -> (this / 1_048_576.0).formatSize() + " MB"
        in 1_073_741_824 .. Long.MAX_VALUE -> (this / 1_073_741_824.0).formatSize() + " GB"
        else -> toString() + " B"
    }

    private fun Double.formatSize() = "%.${2}f".format(this)
}