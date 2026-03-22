//=============================================================================
// Brief     : TFTP Server
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

package pt.cguimaraes.sstftp.server;

import java.io.File;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

    public static void main(String[] args)
            throws SocketException, NoSuchMethodException, SecurityException, UnknownHostException {
        Logger logger = Logger.getLogger("sstftp-server");
        logger.setLevel(Level.ALL);

        // create the Options
        Options arguments = new Options();
        arguments.addOption(Option.builder("h")
                .longOpt("help")
                .desc("print this message")
                .build());
        arguments.addOption(Option.builder("p")
                .longOpt("port")
                .desc("listening port (default: 69)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("d")
                .longOpt("directory")
                .desc("path to the directory that contains the files")
                .hasArg()
                .required()
                .build());
        arguments.addOption(Option.builder("r")
                .longOpt("retries")
                .desc("maximum retries (default: 3)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("t")
                .longOpt("timeout")
                .desc("timeout interval to retransmissions (ms) [1-255000] (default: 2000)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("b")
                .longOpt("blksize")
                .desc("Maximum block size allowed (default: no limit)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("s")
                .longOpt("tsize")
                .desc("Maximum file size allowed (default: no limit)")
                .hasArg()
                .build());
        arguments.addOption(Option.builder("v")
                .longOpt("log")
                .desc("Log level [0-2] (default: 1)")
                .hasArg()
                .build());

        int port = 69;
        String localDir = "";
        int retries = 3;
        int interval = 2000;
        int blksize = -1;
        long tsize = -1;

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
            String dirOption = line.getOptionValue('d');
            if (dirOption == null || dirOption.trim().isEmpty()) {
                throw new ParseException("Local directory is required");
            }
            java.nio.file.Path localPath = Paths.get(dirOption).toAbsolutePath();
            if (!Files.isDirectory(localPath)) {
                throw new ParseException("Local directory does not exist: " + dirOption);
            }
            localDir = localPath.toString() + File.separator;

            // Parse port number
            if (line.hasOption('p')) {
                String portStr = line.getOptionValue('p');
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 0 || port > 65535) {
                        throw new ParseException("Invalid port number: " + port);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid port number: " + portStr);
                }
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
            if (line.hasOption('i')) {
                String intervalStr = line.getOptionValue('i');
                try {
                    interval = Integer.parseInt(intervalStr);
                    if (interval <= 0 || interval > 255000) {
                        throw new ParseException("Invalid timeout interval to retransmissions: " + interval);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid interval: " + intervalStr);
                }
            }

            // Parse maximum block size
            if (line.hasOption('b')) {
                String blkStr = line.getOptionValue('b');
                try {
                    blksize = Integer.parseInt(blkStr);
                    if (blksize < 0) {
                        throw new ParseException("Invalid maximum block size: " + blksize);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid block size: " + blkStr);
                }
            }

            // Parse maximum transfer size
            if (line.hasOption('s')) {
                String tsizeStr = line.getOptionValue('s');
                try {
                    tsize = Long.parseLong(tsizeStr);
                    if (tsize < 0) {
                        throw new ParseException("Invalid maximum file size: " + tsize);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid file size: " + tsizeStr);
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
            formatter.printHelp(80, "sstftp-server ", "", arguments, "", true);
            System.exit(1);
        }

        new TFTPServer(port, localDir, retries, interval, blksize, tsize);
    }
}
