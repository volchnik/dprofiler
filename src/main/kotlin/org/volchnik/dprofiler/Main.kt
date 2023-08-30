package org.volchnik.dprofiler

import org.volchnik.dprofiler.view.BasePanelComponent
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities


fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Disk profiler")
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)

        val basePanel = BasePanelComponent()
        frame.add(basePanel)
        frame.pack()
        frame.size = Dimension(600, 600)
        frame.isVisible = true

        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(windowEvent: WindowEvent) {
                basePanel.shotdown()
            }
        })
    }
}
