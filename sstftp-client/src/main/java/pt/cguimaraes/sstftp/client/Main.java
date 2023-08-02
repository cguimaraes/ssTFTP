package pt.cguimaraes.sstftp.client;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Scanner;
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
    public static void main(String args[]) throws Exception {
        Logger logger = Logger.getLogger("sstftp-client");
        logger.setLevel(Level.ALL);

        // create the Options
        Options arguments = new Options();
        arguments.addOption(OptionBuilder.withLongOpt("help")
                .withDescription("print this message")
                .create('h'));
        arguments.addOption(OptionBuilder.withLongOpt("mode")
                .withDescription("specifies file transfer mode (default: octet)")
                .hasArgs(1)
                .withArgName("octet|netascii")
                .create('m'));
        arguments.addOption(OptionBuilder.withLongOpt("server")
                .withDescription("server host")
                .hasArgs(1)
                .isRequired()
                .create('c'));
        arguments.addOption(OptionBuilder.withLongOpt("port")
                .withDescription("server port (default: 69)")
                .hasArgs(1)
                .create('p'));
        arguments.addOption(OptionBuilder.withLongOpt("file")
                .withDescription("filename")
                .hasArgs(1)
                .isRequired()
                .create('f'));
        arguments.addOption(OptionBuilder.withLongOpt("action")
                .withDescription("action to perform")
                .hasArgs(1)
                .withArgName("get|put")
                .isRequired()
                .create('a'));
        arguments.addOption(OptionBuilder.withLongOpt("blksize")
                .withDescription("block size (default: 512)")
                .hasArgs(1)
                .create('b'));
        arguments.addOption(OptionBuilder.withLongOpt("no-tsize")
                .withDescription("receive/send file length (default: enabled)")
                .hasArgs(0)
                .create('s'));
        arguments.addOption(OptionBuilder.withLongOpt("retries")
                .withDescription("maximum retries (default: 3)")
                .hasArgs(1)
                .create('r'));
        arguments.addOption(OptionBuilder.withLongOpt("timeout")
                .withDescription("timeout to retransmissions (ms) (default: 2000)")
                .hasArgs(1)
                .create('t'));
        arguments.addOption(OptionBuilder.withLongOpt("log")
                .withDescription("Log level [0-2] (default: 1)")
                .hasArgs(1)
                .create('v'));

        String action = "";
        String path = "";
        String mode = "octet";

        InetAddress dstIp = null;
        int dstPort = 69;
        int blksize = 512; // Default block size
        int retries = 3;
        int timeout = 2000;
        boolean tsize = true;
        HashMap<String, String> options = new HashMap<String, String>();

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
            action = line.getOptionValue('a').toLowerCase();
            if (!action.equals("get") && !action.equals("put")) {
                throw new ParseException("Invalid action");
            }

            // Parse mode
            if (line.hasOption('m')) {
                mode = line.getOptionValue('m').toLowerCase();
                if (!mode.equals("octet") && !mode.equals("netascii")) {
                    throw new ParseException("Invalid mode");
                }
            }

            // Parse hostname
            try {
                dstIp = InetAddress.getByName(line.getOptionValue('c'));
            } catch (UnknownHostException e) {
                throw new ParseException("Could not find hostname");
            }

            // Parse fileName
            path = line.getOptionValue('f');

            // Parse port number
            if (line.hasOption('p')) {
                dstPort = Integer.parseInt(line.getOptionValue('p'));
                if (dstPort < 0 || dstPort > 65535) {
                    throw new ParseException("Invalid port number");
                }
            }

            // Parse block size
            if (line.hasOption('b')) {
                options.put("blksize", line.getOptionValue('b'));
                if (Integer.parseInt(line.getOptionValue('b')) < 0) {
                    throw new ParseException("Invalid block size");
                }
            }

            // Parse receive/send file length
            if (line.hasOption('s')) {
                tsize = false;
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
                if (timeout < 0) {
                    throw new ParseException("Invalid timeout to retransmissions");
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
            formatter.printHelp(80, "sstftp-client ", "", arguments, "", true);
            System.exit(1);
        }

        if (action.equals("put")) {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) {
                logger.severe("File does not exists or cannot be opened");
                System.exit(1);
            }

            if (tsize) {
                options.put("tsize", Long.toString(f.length()));
            }

        } else if (action.equals("get")) {
            File f = new File(path);
            if (f.exists()) {
                logger.severe("File already exists. Override [Y/n]? ");
                @SuppressWarnings("resource")
                String input = new Scanner(System.in).next();
                if (input.toLowerCase().charAt(0) != 'y') {
                    System.exit(0);
                }
            }

            if (tsize) {
                options.put("tsize", "0");
            }
        }

        new TFTPClient(dstIp, dstPort, action, mode, path, retries, timeout, blksize, options);
    }
}
