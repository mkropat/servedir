package com.codetinkerer.servedir;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

public class StaticFilesInitializer extends ChannelInitializer<SocketChannel> {
    final String dirPath;

    public StaticFilesInitializer(String dirPath) {
        this.dirPath = dirPath;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        final ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpServerExpectContinueHandler());
        p.addLast(new ChunkedWriteHandler());
        p.addLast(new StaticFilesChannelHandler(dirPath));
    }
}
