package com.codetinkerer.servedir;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

public class ServeDir {

    public static void main(String[] args) throws InterruptedException {
        Logger rootLogger = (Logger) LoggerFactory.getLogger("root");
        rootLogger.setLevel(Level.INFO);

        int port = 8000;
        String dirPath = System.getProperty("user.dir");
        new CliRunner(dirPath, port).runServer();
    }
}
