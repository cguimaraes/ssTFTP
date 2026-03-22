//=============================================================================
// Brief     : TFTP Client Main
// Author(s) : Carlos Guimarães <carlos.em.guimaraes@gmail.com>
// ----------------------------------------------------------------------------
// ssTFTP - Super Simple Trivial File Transfer Protocol
//
// Copyright (C) 2008-2026 Carlos Guimarães
//
// This file is part of ssTFTP.
//
// ssTFTP is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ssTFTP is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ssTFTP. If not, write to the Free Software Foundation,
// Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//=============================================================================

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
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

    public static void main(String args[]) throws Exception {
        Logger logger = Logger.getLogger("sstftp-client");
        logger.setLevel(Level.ALL);

        // create the Options
        Options arguments = new Options();
        arguments.addOption(Option.builder("h")
                .longOpt("help")
                .desc("print this message")
                .build());
        arguments.addOption(Option.builder("m")
                .longOpt("mode")
                .desc("specifies file transfer mode (default: octet)")
                .hasArg()
                .argName("octet|netascii")
                .build());
        arguments.addOption(Option.builder("c")
                .longOpt("server")
                .desc("server host")
                .hasArg()
                .required()
                .build());
        arguments.addOption(Option.builder("p")
                .longOpt("port")
                .desc("server port (default: 69)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("f")
                .longOpt("file")
                .desc("filename")
                .hasArg()
                .required()
                .build());
        arguments.addOption(Option.builder("a")
                .longOpt("action")
                .desc("action to perform")
                .hasArg()
                .argName("get|put")
                .required()
                .build());
        arguments.addOption(Option.builder("b")
                .longOpt("blksize")
                .desc("block size (default: 512)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("s")
                .longOpt("no-tsize")
                .desc("do not request file size (default: enabled)")
                .build());
        arguments.addOption(Option.builder("r")
                .longOpt("retries")
                .desc("maximum retries (default: 3)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("t")
                .longOpt("interval")
                .desc("timeout interval to retransmissions (ms) [1-255000] (default: 2000)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("v")
                .longOpt("log")
                .desc("Log level [0-2] (default: 1)")
                .hasArg()
                .build());

        String action = "";
        String path = "";
        String mode = "octet";

        InetAddress dstIp = null;
        int dstPort = 69;
        int blksize = 512; // Default block size
        int retries = 3;
        int interval = 2000;
        boolean tsize = true;
        HashMap<String, String> options = new HashMap<String, String>();

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine line = parser.parse(arguments, args);

            // If help is defined
            if (line.hasOption('h')) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(80, "sstftp ", "", arguments, "", true);
                System.exit(0);
            }

            // Parse action
            String actionStr = line.getOptionValue('a');
            if (actionStr == null || actionStr.trim().isEmpty()) {
                throw new ParseException("Action is required");
            }
            action = actionStr.toLowerCase();
            if (!action.equals("get") && !action.equals("put")) {
                throw new ParseException("Invalid action: " + action);
            }

            // Parse mode
            if (line.hasOption('m')) {
                mode = line.getOptionValue('m').toLowerCase();
                if (!mode.equals("octet") && !mode.equals("netascii")) {
                    throw new ParseException("Invalid mode: " + mode);
                }
            }

            // Parse hostname
            String serverStr = line.getOptionValue('c');
            if (serverStr == null || serverStr.trim().isEmpty()) {
                throw new ParseException("Server address is required");
            }
            try {
                dstIp = InetAddress.getByName(serverStr);
            } catch (UnknownHostException e) {
                throw new ParseException("Could not find hostname: " + serverStr);
            }

            // Parse fileName
            String fileStr = line.getOptionValue('f');
            if (fileStr == null || fileStr.trim().isEmpty()) {
                throw new ParseException("Filename is required");
            }
            path = fileStr;

            // Parse port number
            if (line.hasOption('p')) {
                String portStr = line.getOptionValue('p');
                try {
                    dstPort = Integer.parseInt(portStr);
                    if (dstPort < 0 || dstPort > 65535) {
                        throw new ParseException("Invalid port number: " + dstPort);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid port number: " + portStr);
                }
            }

            // Parse block size
            if (line.hasOption('b')) {
                String blkStr = line.getOptionValue('b');
                try {
                    int blkVal = Integer.parseInt(blkStr);
                    if (blkVal < 0) {
                        throw new ParseException("Invalid block size: " + blkVal);
                    }
                    options.put("blksize", blkStr);
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid block size: " + blkStr);
                }
            }

            // Parse receive/send file length
            if (line.hasOption('s')) {
                tsize = false;
            } else {
                options.put("tsize", "0");  // Request file size
            }

            // Parse maximum retries
            if (line.hasOption('r')) {
                String retriesStr = line.getOptionValue('r');
                try {
                    retries = Integer.parseInt(retriesStr);
                    if (retries < 0) {
                        throw new ParseException("Invalid maximum retries value: " + retries);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid retries: " + retriesStr);
                }
            }

            // Parse timeout interval to retransmissions
            if (line.hasOption('t')) {
                String intervalStr = line.getOptionValue('t');
                try {
                    interval = Integer.parseInt(intervalStr);
                    if (interval <= 0 || interval > 255000) {
                        throw new ParseException("Invalid timeout interval to retransmissions: " + interval);
                    }

                    // Timeout Interval in TFTP is defined in seconds
                    long roundUpSec = Double.valueOf(Math.ceil(interval / 1000.0)).longValue();
                    options.put("interval", Long.toString(roundUpSec));
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid interval: " + intervalStr);
                }
            }

            // Parse log level
            logger.setLevel(Level.ALL); // Default log level
            if (line.hasOption('v')) {
                String logStr = line.getOptionValue('v');
                try {
                    switch (Integer.parseInt(logStr)) {
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
                            throw new ParseException("Invalid log level: " + logStr);
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid log level: " + logStr);
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

        new TFTPClient(dstIp, dstPort, action, mode, path, retries, interval, blksize, options);
    }
}
