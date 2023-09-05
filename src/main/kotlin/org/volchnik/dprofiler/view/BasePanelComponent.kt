package org.volchnik.dprofiler.view

import java.awt.BorderLayout
import java.awt.BorderLayout.PAGE_START
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.FlowLayout.LEFT
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel


class BasePanelComponent: JPanel(BorderLayout()), ActionListener {
    private val changeDir = JButton("<html><b>Change directory</b></html>")
    private val directoryLabel: JLabel
    private var fileChooser = JFileChooser()
    private var fileTree: FileTreeComponent
    private val panelStatus = JPanel(FlowLayout(LEFT))

    init {
        val defaultPath = Paths.get("").toAbsolutePath().toString()

        fileTree = FileTreeComponent(basePath = Path.of(defaultPath), statusPanel = panelStatus)
        directoryLabel = JLabel(defaultPath)
        directoryLabel.preferredSize = Dimension(450, 40)

        val panel = JPanel(FlowLayout(LEFT))

        panel.add(changeDir)
        panel.add(panelStatus)
        panel.add(directoryLabel)

        add(panel, PAGE_START)
        add(fileTree)

        changeDir.addActionListener(this)

        fileChooser.setDialogTitle("Select current directory")
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES)
        fileChooser.setAcceptAllFileFilterUsed(false)
    }

    fun shutdown() {
        fileTree.shutdown()
    }

    override fun actionPerformed(event: ActionEvent) {
        if (event.source == changeDir) {
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {

                var selectedFile = fileChooser.selectedFile
                if (!selectedFile.isDirectory()) {
                    selectedFile = selectedFile.getParentFile()
                }

                fileTree.let {
                    remove(it)
                    it.shutdown()
                }

                val currentPath = selectedFile.toPath()
                fileTree = FileTreeComponent(basePath = currentPath, statusPanel = panelStatus)
                add(fileTree)
                revalidate()
                directoryLabel.text = currentPath.toString()
            }
        }
    }
}