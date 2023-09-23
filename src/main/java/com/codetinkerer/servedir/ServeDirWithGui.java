package com.codetinkerer.servedir;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.codetinkerer.servedir.gui.ServerListApplication;
import org.slf4j.LoggerFactory;

public class ServeDirWithGui {
    public static void main(String[] args) throws InterruptedException {
        Logger rootLogger = (Logger) LoggerFactory.getLogger("root");
        rootLogger.setLevel(Level.INFO);

        if (args.length == 1 && "-g".equals(args[0])) {
            System.out.println("starting gui");
            ServerListApplication.run();
            return;
        }

        int port = 8000;
        String dirPath = System.getProperty("user.dir");
        new CliRunner(dirPath, port).runServer();
    }
}
