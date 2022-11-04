package com.codetinkerer.servedir;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

class StaticFilesChannelHandler extends SimpleChannelInboundHandler<HttpRequest> {

    static final String[] indexFilenames = new String[]{"index.htm", "index.html"};

    final Logger logger = LoggerFactory.getLogger(StaticFilesChannelHandler.class);
    final String dirPath;

    public StaticFilesChannelHandler(String dirPath) {
        this.dirPath = dirPath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest req) {
        if (req.protocolVersion() != HttpVersion.HTTP_1_1) {
            sendErrorResponse(ctx, req, HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED, "Error: unsupported HTTP version\r\n");
            return;
        }

        if (!req.decoderResult().isSuccess()) {
            logger.debug("Error when parsing request");
            sendErrorResponse(ctx, req, HttpResponseStatus.BAD_REQUEST, "Error: unable to decode request\r\n");
            return;
        }

        URI uri = parseUri(req.uri());
        if (uri == null || uri.getHost() != null) {
            logger.debug("Error when parsing request path. Invalid format: %s".formatted(req.uri()));
            sendErrorResponse(ctx, req, HttpResponseStatus.BAD_REQUEST, "Error: unsupported path format\r\n");
            return;
        }

        Path location = locatePathInStaticFiles(uri.getPath());
        if (location == null) {
            sendNotFoundResponse(ctx, req);
            return;
        }

        if (Files.isDirectory(location) && !uri.getPath().endsWith("/")) {
            URI redirectPath = replaceUriPath(uri, uri.getPath() + "/");
            FullHttpResponse redirectResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
            redirectResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            redirectResponse.headers().set(HttpHeaderNames.LOCATION, redirectPath.toString());
            sendHappyResponse(ctx, req, redirectResponse);
            return;
        }

        if (Files.isDirectory(location)) {
            location = getIndexChild(location);

            if (location == null) {
                sendNotFoundResponse(ctx, req);
                return;
            }
        }

        if (!Files.isReadable(location)) {
            logger.warn("Error: file not readable: %s".formatted(location));

            sendNotFoundResponse(ctx, req);
            return;
        }

        String contentType = determineContentType(location);

        ZonedDateTime lastModifiedTime = getLastModifiedTime(location);
        if (lastModifiedTime == null) {
            logger.warn("Error: unable to read last modified time on: %s".formatted(location));
            sendNotFoundResponse(ctx, req);
            return;
        }

        try (RandomAccessFile file = openFile(location)) {
            if (file == null) {
                sendNotFoundResponse(ctx, req);
                return;
            }

            long fileLength;
            try {
                fileLength = file.length();
            } catch (IOException ex) {
                sendNotFoundResponse(ctx, req);
                return;
            }

            if (!HttpMethod.GET.equals(req.method()) && !HttpMethod.HEAD.equals(req.method())) {
                sendErrorResponse(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, "%s method not supported\r\n".formatted(req.method().name()));
                return;
            }

            ZonedDateTime ifModifiedSince = getIfModifiedSince(req);
            if (ifModifiedSince != null && !ifModifiedSince.isBefore(lastModifiedTime.truncatedTo(ChronoUnit.SECONDS))) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
                sendHappyResponse(ctx, req, response);
                return;
            }

            String fileETag = computeETag(lastModifiedTime);
            String ifNoneMatchString = req.headers().get(HttpHeaderNames.IF_NONE_MATCH, "");
            Set<String> ifNoneMatch = Arrays.stream(ifNoneMatchString.split(","))
                    .map(t -> t.trim())
                    .collect(Collectors.toSet());
            if (ifNoneMatch.contains(fileETag)) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
                sendHappyResponse(ctx, req, response);
                return;
            }

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=0");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(now));
            response.headers().set(HttpHeaderNames.ETAG, fileETag);
            response.headers().set(HttpHeaderNames.LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModifiedTime));

            logger.info("%s %s → %d".formatted(req.method(), req.uri(), response.status().code()));

            if (HttpMethod.HEAD.equals(req.method())) {
                sendHappyResponse(ctx, req, response);
                return;
            }

            ctx.write(response);

            ctx.write(new DefaultFileRegion(file.getChannel(), 0, fileLength));

            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            if (!HttpUtil.isKeepAlive(req)) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (IOException ex) {
            // The file.close() failed, ignore
        }
    }

    private static String computeETag(ZonedDateTime lastModified) {
        return String.valueOf(lastModified.toInstant().getEpochSecond());
    }

    private RandomAccessFile openFile(Path filePath) {
        try {
            return new RandomAccessFile(filePath.toFile(), "r");
        } catch (IOException ex) {
            return null;
        }
    }

    private ZonedDateTime getLastModifiedTime(Path filePath) {
        try {
            FileTime fileTime = Files.getLastModifiedTime(filePath);
            return ZonedDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"));
        } catch (IOException ex) {
            return null;
        }
    }

    private String determineContentType(Path filePath) {
        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "text/plain";
            }
            return contentType;
        } catch (IOException ex) {
            return "text/plain";
        }
    }

    private ZonedDateTime getIfModifiedSince(HttpRequest req) {
        String value = req.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Path locatePathInStaticFiles(String path) {
        String[] pathParts = Arrays.stream(path.split("/"))
                .filter(p -> !p.isEmpty())
                .toArray(String[]::new);

        // Walk down the directory hierarchy, matching the path as we go. There's probably a more efficient way to do
        // this, but my main concern is preventing any sort of file access outside the static files directory.
        Path location = Paths.get(dirPath);
        for (String pathPart : pathParts) {
            Path matchingChild = getChild(location, pathPart);
            if (matchingChild == null) {
                return null;
            }
            if (Files.isSymbolicLink(matchingChild)) {
                logger.warn("Error: for security reasons, symlinks are not supported: %s".formatted(matchingChild));
                return null;
            }
            location = matchingChild;
        }

        return location;
    }

    private Path getChild(Path directory, String childName) {
        DirectoryStream<Path> dirContents;
        try {
            dirContents = Files.newDirectoryStream(directory);
        } catch (IOException ex) {
            return null;
        }
        for (Path child : dirContents) {
            if (child.getFileName().toString().equals(childName)) {
                return child;
            }
        }
        return null;
    }

    private Path getIndexChild(Path directory) {
        for (String fileName : indexFilenames) {
            Path index = getChild(directory, fileName);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private void sendNotFoundResponse(ChannelHandlerContext ctx, HttpRequest req) {
        sendErrorResponse(ctx, req, HttpResponseStatus.NOT_FOUND, "Sorry, can't find that file\r\n");
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status, String message) {
        logger.warn("%s %s → %d".formatted(req.method(), req.uri(), status.code()));

        ByteBuf content = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        HttpUtil.setContentLength(response, content.readableBytes());
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        writeResponse(ctx, req, response);
    }

    private void sendHappyResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response) {
        logger.info("%s %s → %d".formatted(req.method(), req.uri(), response.status().code()));

        writeResponse(ctx, req, response);
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response) {
        if (!response.headers().contains(HttpHeaderNames.DATE)) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
            response.headers().set(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(now));
        }

        boolean isKeepAlive = HttpUtil.isKeepAlive(req);
        if (!isKeepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        ChannelFuture flushPromise = ctx.writeAndFlush(response);
        if (!isKeepAlive) {
            flushPromise.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static URI replaceUriPath(URI uri, String updatedPath) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), updatedPath, uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static URI parseUri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}