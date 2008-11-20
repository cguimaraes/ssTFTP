//=============================================================================
// Brief     : TFTP Client Main
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

package pt.cguimaraes.sstftp.client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.util.Arrays;
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
import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class TFTPClient {

	private static TFTPSocket socket;
	private static int bSize;
	private static String mode;
	private static String fileName;
	private static RandomAccessFile file;
	private static boolean sentLast = false;

	public void send(TFTPMessage msg) {
		try {
			socket.send(msg);
		} catch (IOException e) {
			Logger.getGlobal().severe("Socket exception");
			System.exit(1);
		}
	}

	// Generic TFTP message handler
	public void handler(TFTPMessage msg) {
		switch(msg.getOpcode()) {
			case TFTPMessage.DATA: {
				DataMessage msgData = (DataMessage) msg;
				handleData(msgData);
			} break;

			case TFTPMessage.ACK: {
				AcknowledgeMessage msgAck = (AcknowledgeMessage) msg;
				handleAcknowledge(msgAck);
			} break;

			case TFTPMessage.ERROR: {
				ErrorMessage msgError = (ErrorMessage) msg;
				handleError(msgError);
			} break;

			default: {
				ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
				send(msgError);

				Logger.getGlobal().warning("Illegal TFTP Operation");
				System.exit(1);
				break;
			}
		}
	}

	// Handle TFTP Data message: write data to file
	private void handleData(DataMessage msgData) {
		try {
			if(mode.equals("octet")) {
				file.write(msgData.getData(), 0, msgData.getData().length);
			} else if(mode.equals("netascii")) {
				@SuppressWarnings("resource")
				FromNetASCIIOutputStream is = new FromNetASCIIOutputStream(Channels.newOutputStream(file.getChannel()));
				is.write(msgData.getData(), 0, msgData.getData().length);
			}
		} catch (IOException e) {
			ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
			send(errorMsg);

			Logger.getGlobal().warning("Cannot write on file");
			System.exit(1);
		}

		// Acknowledge the TFTP Data message
		AcknowledgeMessage msgAck = new AcknowledgeMessage(msgData.getBlockNumber());
		send(msgAck);

		// If data length lower than block size, transfer is complete
		if(msgData.getData().length < bSize) {
			Logger.getGlobal().info("Transfer complete");
			System.exit(0);
		}
	}

	// Handle TFTP Acknowledge message: send data to server
	private void handleAcknowledge(AcknowledgeMessage msgAck) {
		byte[] b = new byte[bSize];

		try {
			int n = -1;

			if(mode.equals("octet")) {
				n = file.read(b);
			} else if(mode.equals("netascii")) {
				@SuppressWarnings("resource")
				ToNetASCIIInputStream is = new ToNetASCIIInputStream(Channels.newInputStream(file.getChannel()));
				n = is.read(b);
			}

			// If data to send is lower than block size, transfer is complete
			// Note: transfer is only complete when acknowledge to the last
			//       TFTP Data message is received
			if (n < bSize) {
				if(!sentLast) {
					sentLast = true;

					// If file.length % bSize == 0, send last data packet
					// with no data
					if(n == -1)
						n = 0;
				} else {
					Logger.getGlobal().info("Transfer complete");
					System.exit(0);
				}
			}

			DataMessage msgData = new DataMessage(msgAck.getBlockNumber() + 1, Arrays.copyOfRange(b, 0, n));
			send(msgData);
		} catch (IOException e) {
			ErrorMessage msgError = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
			send(msgError);

			Logger.getGlobal().warning("Cannot read file");
			System.exit(1);
		}
	}

	// Handle TFTP Error message
	private void handleError(ErrorMessage msgError) {
		Logger.getGlobal().info("Error (" + msgError.getErrorCode() + "): " + msgError.getErrorMsg());
		socket.close();
		System.exit(0);
	}

	@SuppressWarnings("static-access")
	public static void main(String args[]) throws Exception
	{
		Logger logger = Logger.getLogger("sstftp-client");

		// create the Options
		Options options = new Options();
		options.addOption(OptionBuilder.withLongOpt("help")
				.withDescription("print this message")
				.create('h'));
		options.addOption(OptionBuilder.withLongOpt("mode")
				.withDescription("specifies file transfer mode (default: octet)")
				.hasArgs(1)
				.withArgName("octet|netascii")
				.create('m'));
		options.addOption(OptionBuilder.withLongOpt("server")
				.withDescription("server host")
				.hasArgs(1)
				.isRequired()
				.create('s'));
		options.addOption(OptionBuilder.withLongOpt("port")
				.withDescription("server port (default: 69)")
				.hasArgs(1)
				.create('p'));
		options.addOption(OptionBuilder.withLongOpt("file")
				.withDescription("filename")
				.hasArgs(1)
				.isRequired()
				.create('f'));
		options.addOption(OptionBuilder.withLongOpt("action")
				.withDescription("action to perform")
				.hasArgs(1)
				.withArgName("get|put")
				.isRequired()
				.create('a'));
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

		String action = "";
		InetAddress ipAddress = null;
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
			action = line.getOptionValue('a').toLowerCase();
			if(!action.equals("get") && !action.equals("put"))
				throw new ParseException("Invalid action");

			// Parse mode
			mode = line.getOptionValue('m').toLowerCase();
			if(!mode.equals("octet") && !mode.equals("netascii"))
				throw new ParseException("Invalid mode");

			// Parse hostname
			try {
				ipAddress = InetAddress.getByName(line.getOptionValue('s'));
			} catch (UnknownHostException e) {
				throw new ParseException("Could not find hostname");
			}

			// Parse fileName
			fileName = line.getOptionValue('f');

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
			formatter.printHelp(80, "sstftp-client ", "", options, "", true);
			System.exit(1);
		}

		if(action.equals("put")) {
			File f = new File(fileName);
			if (!f.exists() || !f.canRead()) {
				logger.severe("File does not exists or cannot be opened");
				System.exit(1);
			}
			file = new RandomAccessFile(fileName, "r");
		} else if(action.equals("get")) {
			File f = new File(fileName);
			if (f.exists()) {
				logger.severe(fileName + " already exists. Override [Y/n]? ");
				@SuppressWarnings("resource")
				String input = new Scanner(System.in).next();
				if (input.toLowerCase().charAt(0) != 'y')
					System.exit(0);
			}
			file = new RandomAccessFile(fileName, "rw");
		}

		// Parse block size
		bSize = 512; // Default block size

		TFTPClient client = new TFTPClient();
        Method handler = TFTPClient.class.getMethod("handler", new Class[]{TFTPMessage.class});

		socket = new TFTPSocket(ipAddress, port, client, handler);
		if(retries != -1)
			socket.setRetries(retries);
		if(timeout != -1)
			socket.setTimeout(timeout);

		Thread t = new Thread(socket);
        t.start();

		if (action.equals("put")) {
			WriteRequestMessage msgWRQ = new WriteRequestMessage(fileName, mode);
			client.send(msgWRQ);

			logger.info("Uploading " + fileName + " to server in " + mode + " mode...");
		} else if (action.equals("get")) {
			ReadRequestMessage msgRRQ = new ReadRequestMessage(fileName, mode);
			client.send(msgRRQ);

			logger.info("Downloading " + fileName + " from server in " + mode + " mode...");
		}
	}
}