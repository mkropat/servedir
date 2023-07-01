package com.codetinkerer.servedir.gui

import com.codetinkerer.servedir.StaticFilesInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.concurrent.Future

class ServerRunner(
    private val bossGroup: EventLoopGroup?,
    private val workerGroup: EventLoopGroup?,
    val config: ServerConfig
) {
    private var ch: Channel? = null
    private var state: ServerState = ServerState.STOPPED

    fun port(): Int? = config.port

    fun setPort(port: Int) {
        config.port = port
    }

    fun setPort(port: String) {
        try {
            config.port = port.toInt()
        } catch (e: NumberFormatException) {
            config.port = null
        }
    }

    fun dirPath(): String = config.directoryPath

    fun dirPathFormatted(): String {
        val homeDir = System.getenv("HOME")
        var path = dirPath()
        if (path.startsWith(homeDir)) {
            path = path.replaceFirst(homeDir, "~")
        }
        return path
    }

    fun setDirPath(dirPath: String) {
        config.directoryPath = dirPath
    }

    fun isRunning(): Boolean = state == ServerState.STARTED || state == ServerState.STOPPING

    fun isValid(): Boolean = port() != null

    fun isInTransition(): Boolean = state == ServerState.STARTING || state == ServerState.STOPPING

    fun desiredState(): ServerState =
        if (state == ServerState.STARTING || state == ServerState.STARTED) ServerState.STARTED else ServerState.STOPPED

    fun toggle(): Future<*> {
        return if (isRunning()) stop() else start()
    }

    fun start(): Future<*> {
        if (state != ServerState.STOPPED) {
            //FIXME
        }
        state = ServerState.STARTING
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .handler(LoggingHandler(LogLevel.DEBUG))
            .childHandler(StaticFilesInitializer(dirPath()))
        return b.bind(port()!!).addListener(ChannelFutureListener { future: ChannelFuture ->
            if (!future.isSuccess) {
                // FIXME: handle error
            }
            ch = future.channel()
            state = ServerState.STARTED
        })
    }

    fun stop(): Future<*> {
        if (state != ServerState.STARTED) {
            //FIXME
        }
        state = ServerState.STOPPING
        return ch!!.disconnect().addListener {
            ch = null
            state = ServerState.STOPPED
        }
    }
}