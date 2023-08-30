package org.volchnik.dprofiler.view

import java.awt.BorderLayout
import java.awt.BorderLayout.PAGE_START
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
    private val changeDir = JButton("Select directory")
    private val directoryLabel: JLabel
    private var fileChooser = JFileChooser()
    private var fileTree: FileTreeComponent

    init {
        val defaultPath = Paths.get("").toAbsolutePath().toString()
        fileTree = FileTreeComponent(Path.of(defaultPath))
        directoryLabel = JLabel(defaultPath)

        val panel = JPanel(FlowLayout(LEFT))
        panel.add(changeDir)
        panel.add(directoryLabel)

        add(panel, PAGE_START)
        add(fileTree)

        changeDir.addActionListener(this)

        fileChooser.setDialogTitle("Select current directory")
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES)
        fileChooser.setAcceptAllFileFilterUsed(false)
    }

    fun shotdown() {
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
                fileTree = FileTreeComponent(currentPath)
                add(fileTree)
                revalidate()
                directoryLabel.text = currentPath.toString()
            }
        }
    }
}