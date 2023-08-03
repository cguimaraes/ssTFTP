package pt.cguimaraes.sstftp.server;

import java.io.File;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

    @SuppressWarnings("static-access")
    public static void main(String[] args)
            throws SocketException, NoSuchMethodException, SecurityException, UnknownHostException {
        Logger logger = Logger.getLogger("sstftp-server");
        logger.setLevel(Level.ALL);

        // create the Options
        Options arguments = new Options();
        arguments.addOption(OptionBuilder.withLongOpt("help")
                .withDescription("print this message")
                .create('h'));
        arguments.addOption(OptionBuilder.withLongOpt("port")
                .withDescription("listening port (default: 69)")
                .hasArgs(1)
                .create('p'));
        arguments.addOption(OptionBuilder.withLongOpt("directory")
                .withDescription("path to the directory that contains the files")
                .hasArgs(1)
                .isRequired()
                .create('d'));
        arguments.addOption(OptionBuilder.withLongOpt("retries")
                .withDescription("maximum retries (default: 3)")
                .hasArgs(1)
                .create('r'));
        arguments.addOption(OptionBuilder.withLongOpt("timeout")
                .withDescription("timeout to retransmissions (ms) (default: 2000)")
                .hasArgs(1)
                .create('t'));
        arguments.addOption(OptionBuilder.withLongOpt("blksize")
                .withDescription("Maximum block size allowed (default: no limit)")
                .hasArgs(1)
                .create('b'));
        arguments.addOption(OptionBuilder.withLongOpt("tsize")
                .withDescription("Maximum file size allowed (default: no limit)")
                .hasArgs(1)
                .create('s'));
        arguments.addOption(OptionBuilder.withLongOpt("log")
                .withDescription("Log level [0-2] (default: 1)")
                .hasArgs(1)
                .create('v'));

        int port = 69;
        String localDir = "";
        int retries = 3;
        int timeout = 2000;
        int blksize = -1;
        long tsize = -1;

        try {
            CommandLineParser parser = new GnuParser();
            CommandLine line = parser.parse(arguments, args);

            // If help is defined
            if (line.hasOption('h')) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(80, "sstftp ", "", arguments, "", true);
                System.exit(0);
            }

            // Parse action
            localDir = line.getOptionValue('d').toLowerCase();
            localDir += (localDir.charAt(localDir.length() - 1) == '/' ? "" : "/");
            if (!new File(localDir).exists()) {
                throw new ParseException("Local directory does not exist");
            }

            // Parse port number
            if (line.hasOption('p')) {
                port = Integer.parseInt(line.getOptionValue('p'));
                if (port < 0 || port > 65535) {
                    throw new ParseException("Invalid port number");
                }
            }

            // Parse maximum retries
            if (line.hasOption('r')) {
                retries = Integer.parseInt(line.getOptionValue('r'));
                if (retries < 0) {
                    throw new ParseException("Invalid maximum retries value");
                }
            }

            // Parse timeout to retransmissions
            if (line.hasOption('t')) {
                timeout = Integer.parseInt(line.getOptionValue('t'));
                if (timeout <= 0 || timeout > 255000) {
                    throw new ParseException("Invalid timeout interval to retransmissions");
                }
            }

            // Parse maximum block size allowed
            if (line.hasOption('b')) {
                blksize = Integer.parseInt(line.getOptionValue('b'));
                if (blksize < 0) {
                    throw new ParseException("Invalid block size");
                }
            }

            // Parse receive/send file length
            if (line.hasOption('s')) {
                tsize = Long.parseLong(line.getOptionValue('s'));
                if (tsize < 0) {
                    throw new ParseException("Invalid file size");
                }
            }

            // Parse log level
            logger.setLevel(Level.ALL); // Default log level
            if (line.hasOption('v')) {
                switch (Integer.parseInt(line.getOptionValue('v'))) {
                    case 0: {
                        logger.setLevel(Level.OFF);
                        break;
                    }
                    case 1: {
                        logger.setLevel(Level.INFO);
                        break;
                    }
                    case 2: {
                        logger.setLevel(Level.ALL);
                        break;
                    }
                    default: {
                        throw new ParseException("Invalid log level");
                    }
                }
            }

        } catch (ParseException e) {
            logger.severe(e.getMessage());

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, "sstftp-server ", "", arguments, "", true);
            System.exit(1);
        }

        new TFTPServer(port, localDir, retries, timeout, blksize, tsize);
    }
}
