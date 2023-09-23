package com.codetinkerer.servedir;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.LoggerFactory;

public class CliRunner {
    final org.slf4j.Logger logger = LoggerFactory.getLogger(CliRunner.class);
    final String dirPath;
    final int port;

    public CliRunner(String dirPath, int port) {
        this.dirPath = dirPath;
        this.port = port;
    }

    public void runServer() throws InterruptedException {
        final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        final EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            final ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new StaticFilesInitializer(dirPath));

            final Channel ch = b.bind(port).sync().channel();

            logger.info("Serving %s at http://localhost:%d/".formatted(dirPath, port));

            ch.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}