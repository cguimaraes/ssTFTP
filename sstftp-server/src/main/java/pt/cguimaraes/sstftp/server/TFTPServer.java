//=============================================================================
// Brief     : TFTP Server Main
// Author(s) : Carlos Guimarães <carlos.em.guimaraes@gmail.com>
// ----------------------------------------------------------------------------
// ssTFTP - Open Trivial File Transfer Protocol
//
// Copyright (C) 2008-2013 Carlos Guimarães
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
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class TFTPServer extends Thread {

	private static TFTPSocket socket;
	private static String localDir;

	public void send(TFTPMessage msg) {
		try {
			socket.send(msg);
		} catch (IOException e) {
			Logger.getGlobal().severe("Socket exception");
		}
	}

	// Generic TFTP message handler
	public void handler(TFTPMessage msg) throws SocketException, NoSuchMethodException, SecurityException {
		ServerSession session = null;
		switch(msg.getOpcode()) {
			case TFTPMessage.RRQ: {
				ReadRequestMessage msgRRQ = (ReadRequestMessage) msg;
				session = new ServerSession(localDir, msgRRQ.getFileName(), msgRRQ.getMode(), 0, msgRRQ.getPort(), msgRRQ.getIp(), (short)msgRRQ.getOpcode());
			} break;

			case TFTPMessage.WRQ: {
				WriteRequestMessage msgWRQ = (WriteRequestMessage) msg;
				session = new ServerSession(localDir, msgWRQ.getFileName(), msgWRQ.getMode(), 1, msgWRQ.getPort(), msgWRQ.getIp(), (short)msgWRQ.getOpcode());
			} break;

			default: {
				ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
				send(msgError);

				return;
			}
		}

		if(session != null)
			session.start();
	}

	@SuppressWarnings("static-access")
	public static void main(String args[]) throws Exception
	{
		Logger logger = Logger.getLogger("sstftp-server");
		logger.setLevel(Level.ALL);

		// create the Options
		Options options = new Options();
		options.addOption(OptionBuilder.withLongOpt("help")
				.withDescription("print this message")
				.create('h'));
		options.addOption(OptionBuilder.withLongOpt("port")
				.withDescription("listening port (default: 69)")
				.hasArgs(1)
				.create('p'));
		options.addOption(OptionBuilder.withLongOpt("directory")
				.withDescription("path to the directory that contains the files")
				.hasArgs(1)
				.isRequired()
				.create('d'));
		options.addOption(OptionBuilder.withLongOpt("retries")
				.withDescription("maximum retries (default: 3)")
				.hasArgs(1)
				.create('r'));
		options.addOption(OptionBuilder.withLongOpt("timeout")
				.withDescription("timeout to retransmissions (ms) (default: 2000)")
				.hasArgs(1)
				.create('t'));
		options.addOption(OptionBuilder.withLongOpt("log")
				.withDescription("Log level [0-2] (default: 1)")
				.hasArgs(1)
				.create('l'));

		int port = 69;
		int retries = -1;
		int timeout = -1;

		try {
			CommandLineParser parser = new GnuParser();
			CommandLine line = parser.parse(options, args);

			// If help is defined
			if(line.hasOption('h')) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(80, "sstftp ", "", options, "", true);
				System.exit(0);
			}

			// Parse action
			localDir = line.getOptionValue('d').toLowerCase();
			if(!new File(localDir).exists())
				throw new ParseException("Local directory does not exist");

			// Parse port number
			if(line.hasOption('p')) {
				port = Integer.parseInt(line.getOptionValue('p'));
				if(port < 0 || port > 65535)
					throw new ParseException("Invalid port number");
			}

			// Parse maximum retries
			if(line.hasOption('r')) {
				port = Integer.parseInt(line.getOptionValue('r'));
				if(port < 0)
					throw new ParseException("Invalid maximum retries value");
			}

			// Parse timeout to retransmissions
			if(line.hasOption('t')) {
				port = Integer.parseInt(line.getOptionValue('t'));
				if(port < 0)
					throw new ParseException("Invalid timeout to retransmissions");
			}

			// Parse log level
			logger.setLevel(Level.ALL); // Default log level
			if(line.hasOption('l')) {
				switch (Integer.parseInt(line.getOptionValue('l'))) {
				case 0:
					logger.setLevel(Level.OFF);
					break;
				case 1:
					logger.setLevel(Level.INFO);
					break;
				case 2:
					logger.setLevel(Level.ALL);
					break;
				default:
					throw new ParseException("Invalid log level");
				}
			}

		} catch (ParseException e) {
			logger.severe(e.getMessage());

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(80, "sstftp-server ", "", options, "", true);
			System.exit(1);
		}

		TFTPServer server = new TFTPServer();
        Method handler = TFTPServer.class.getMethod("handler", new Class[]{TFTPMessage.class});

		socket = new TFTPSocket(server, handler);
		socket.bind(InetAddress.getByName("0.0.0.0"), port);
		if(retries != -1)
			socket.setRetries(retries);
		if(timeout != -1)
			socket.setTimeout(timeout);

		Thread t = new Thread(socket);
        t.start();
	}
}