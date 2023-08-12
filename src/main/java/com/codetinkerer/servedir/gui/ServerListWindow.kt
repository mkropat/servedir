package com.codetinkerer.servedir.gui

import io.netty.channel.EventLoopGroup
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ServerListWindow(
    private val bossGroup: EventLoopGroup,
    private val workerGroup: EventLoopGroup
) : JFrame("Server List") {
    private val configPersistence = ConfigPersistence()
    private val logger = LoggerFactory.getLogger(ServerListWindow::class.java)
    private val serverListPanel = JPanel(GridBagLayout())

    private val servers: ArrayList<ServerRunner> = ArrayList(configPersistence.load().map { ServerRunner(bossGroup, workerGroup, it) })

    init {
        defaultCloseOperation = HIDE_ON_CLOSE
        layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)
        setSize(900, 300)
        setLocationRelativeTo(null)

        servers.forEach {
            addServerToUi(it)
        }

        val scrollPane = JScrollPane(serverListPanel)
        contentPane.add(scrollPane)

        val addButton = JButton("Add Server")
        addButton.addActionListener(this::handleAdd)
        contentPane.add(addButton)

        isVisible = true
    }

    private fun handleAdd(e: ActionEvent) {
        openDirectoryPicker(null) { selectedDirectory ->
            val occupiedPorts = servers.mapNotNull { s -> s.port() }
            val nextPort = (occupiedPorts.maxOrNull() ?: 0) + 1
            val port = nextPort.coerceAtLeast(3000)
            val addedServer = ServerRunner(bossGroup, workerGroup, ServerConfig(selectedDirectory.path, port, ServerState.STOPPED))
            servers.add(addedServer)
            logger.info("added server: ${servers.size}")
            addServerToUi(addedServer)
            saveConfig()
        }
    }

    private fun saveConfig() {
        configPersistence.save(servers.map { it.config })
    }

    private fun addServerToUi(server: ServerRunner) {
        val serverPanel = JPanel(BorderLayout(18, 6))
        val startStopButton = JButton(if (server.isRunning()) "[]" else "[>")
        serverPanel.add(startStopButton, BorderLayout.WEST)
        val dirLabel = JLabel(server.dirPathFormatted())
        serverPanel.add(dirLabel, BorderLayout.CENTER)

        val portPanel = JPanel(BorderLayout())
        serverPanel.add(portPanel, BorderLayout.EAST)

        val portTextField = JTextField(server.port()?.toString() ?: "")
        portPanel.add(portTextField)

        val portLink = JButton(server.port()?.toString()).apply {
            border = null
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = Color.BLUE
        }
        portLink.addActionListener {
            val url = "http://localhost:${server.port()}/"
            try {
                Desktop.getDesktop().browse(URI(url))

            } catch (e: UnsupportedOperationException) {
                ProcessBuilder("xdg-open", url).start()
            }
        }

        val constraints = GridBagConstraints()
        constraints.gridy = serverListPanel.componentCount
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.weightx = 1.0
        constraints.insets = Insets(5, 5, 5, 5)
        serverListPanel.add(serverPanel, constraints)
        serverListPanel.revalidate()

        startStopButton.addActionListener {
            server.toggle()
                .addListener {
                    SwingUtilities.invokeLater {
                        startStopButton.text = if (server.isRunning()) "[]" else "[>"
                        startStopButton.isEnabled = !server.isInTransition()
                        portTextField.isEnabled = !server.isRunning()
                        if (server.isRunning()) {
                            portPanel.remove(portTextField)
                            portPanel.add(portLink)
                        } else {
                            portPanel.remove(portLink)
                            portPanel.add(portTextField)
                        }
                        portPanel.revalidate()
                        portPanel.repaint()
                    }
                }
            startStopButton.text = if (server.isRunning()) "[]" else "[>"
            startStopButton.isEnabled = !server.isInTransition()
            portTextField.isEnabled = !server.isRunning()
        }

        dirLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) {
                    openDirectoryPicker(File(server.dirPath())) { selectedDirectory ->
                        server.setDirPath(selectedDirectory.path)
                        dirLabel.text = server.dirPathFormatted()
                        saveConfig()
                    }
                }
            }
        })

        portTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                updatePort(server, portTextField.text)
            }

            override fun removeUpdate(e: DocumentEvent) {
                updatePort(server, portTextField.text)
            }

            override fun changedUpdate(e: DocumentEvent) {}
        })
    }

    private fun updatePort(server: ServerRunner, port: String) {
        server.setPort(port)
        saveConfig()
    }

    fun openDirectoryPicker(initialDirectory: File?, onComplete: (File) -> Unit) {
        val fileChooser = JFileChooser()
        fileChooser.selectedFile = initialDirectory
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

        val result = fileChooser.showOpenDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            onComplete(fileChooser.selectedFile)
        }
    }
}
