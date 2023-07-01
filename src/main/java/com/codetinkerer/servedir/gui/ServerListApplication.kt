package com.codetinkerer.servedir.gui

import com.formdev.flatlaf.FlatLightLaf
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

class ServerListApplication {
    companion object {
        @JvmStatic
        fun run() {
            SwingUtilities.invokeLater {
                FlatLightLaf.setup()
                ServerListApplication()
            }
        }
    }

    private val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
    private val workerGroup: EventLoopGroup = NioEventLoopGroup()

    private val mainWindow = ServerListWindow(bossGroup, workerGroup)
    private val trayIcon = createTrayMenu()

    init {
        SystemTray.getSystemTray().add(trayIcon)
    }

    private fun createTrayMenu(): TrayIcon {
        if (!SystemTray.isSupported()) {
            throw UnsupportedOperationException("Unable to start: system tray not supported")
        }

        val defaultFont = Font.decode(null)
        val adjustmentRatio = 2.5f // TODO: scale based on DPI
        val scaledFont = defaultFont.deriveFont(defaultFont.size * adjustmentRatio)

        val popupMenu = PopupMenu()

        val exitItem = MenuItem("Exit")
        exitItem.font = scaledFont
        exitItem.addActionListener {
            shutdown()
        }
        popupMenu.add(exitItem)

        val icon = Toolkit.getDefaultToolkit().getImage("server-icon.png")

        val trayIcon = TrayIcon(icon, "servedir", popupMenu)
        trayIcon.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.source == trayIcon && e.button == MouseEvent.BUTTON1 && !mainWindow.isVisible) {
                    mainWindow.isVisible = true
                    mainWindow.state = Frame.NORMAL
                    mainWindow.requestFocus()
                }
            }
        })
        return trayIcon
    }

    private fun shutdown() {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        exitProcess(0)
    }
}