//=============================================================================
// Brief     : TFTP Server Session
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.OptionAcknowledgeMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class ServerSession extends Thread {

	private TFTPSocket socket;

	private int bSize;
	private int bSizeMax;
	private long fileSize;
	private long tSizeMax;
	private int opcode;
	private String mode;
	private HashMap<String, String> options;

	private RandomAccessFile file;

	private boolean sentLast = false;
	private boolean initialized = true;

	public ServerSession(String localDir, int retries, int timeout, int bSizeMax, long tSizeMax, TFTPMessage msg) throws NoSuchMethodException, SecurityException, SocketException
	{
		// Initialize TFTP Socket
		Method handler = null;
		switch(msg.getOpcode()) {
			case TFTPMessage.RRQ: {
				handler = ServerSession.class.getMethod("handler_get", new Class[]{TFTPMessage.class});
			} break;

			case TFTPMessage.WRQ: {
				handler = ServerSession.class.getMethod("handler_put", new Class[]{TFTPMessage.class});
			} break;
		}

		this.socket = new TFTPSocket(msg.getIp(), msg.getPort(), this, handler);
		this.socket.setRetries(retries);
		this.socket.setTimeout(timeout);

		Thread t = new Thread(socket);
		t.start();

		// Configure session
		this.bSizeMax = bSizeMax;
		this.bSize = 512; // Default block size
		this.tSizeMax = tSizeMax;
		this.fileSize = -1;
		try {
			switch(msg.getOpcode()) {
				case TFTPMessage.RRQ: {
					ReadRequestMessage msgRRQ = (ReadRequestMessage) msg;
					this.mode = msgRRQ.getMode();
					this.opcode = msgRRQ.getOpcode();
					this.options = msgRRQ.getOptions();

					file = new RandomAccessFile(localDir + msgRRQ.getFileName(), "r");
				} break;

				case TFTPMessage.WRQ: {
					WriteRequestMessage msgWRQ = (WriteRequestMessage) msg;
					this.mode = msgWRQ.getMode();
					this.opcode = msgWRQ.getOpcode();
					this.options = msgWRQ.getOptions();

					file = new RandomAccessFile(localDir + msgWRQ.getFileName(), "rw");
				} break;
			}
		} catch (FileNotFoundException e) {
			initialized = false;
			ErrorMessage msgError = new ErrorMessage(ErrorMessage.FILE_NOT_FOUND);
			send(msgError);

			Logger.getGlobal().info("File not found");
		}
	}

	// Send TFTP message via current session
	public void send(TFTPMessage msg) {
		try {
			socket.send(msg);
		} catch (IOException e) {
			Logger.getGlobal().severe(e.getMessage());
		}
	}

	// TFTP message handler for GET action
	public void handler_get(TFTPMessage msg) {
		switch(msg.getOpcode()) {
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

	// TFTP message handler for PUT action
	public void handler_put(TFTPMessage msg) {
		switch(msg.getOpcode()) {
			case TFTPMessage.DATA: {
				DataMessage msgData = (DataMessage) msg;
				handleData(msgData);
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
	private void handleData(DataMessage dataMsg) {
		try {
			if(mode.equals("octet")) {
				file.write(dataMsg.getData(), 0, dataMsg.getData().length);
			} else if(mode.equals("netascii")) {
				@SuppressWarnings("resource")
				FromNetASCIIOutputStream is = new FromNetASCIIOutputStream(Channels.newOutputStream(file.getChannel()));
				is.write(dataMsg.getData(), 0, dataMsg.getData().length);
			}
		} catch (IOException e) {
			ErrorMessage msgError = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
			send(msgError);

			Logger.getGlobal().warning("Cannot write on file");
			socket.close();
			Thread.currentThread().interrupt();
			return;
		}

		// Acknowledge the TFTP Data message
		AcknowledgeMessage msgAck = new AcknowledgeMessage(dataMsg.getBlockNumber());
		send(msgAck);

		// If data length lower than block size, transfer is complete
		if(dataMsg.getData().length < bSize) {
			Logger.getGlobal().info("Transfer complete");
			try {
				if(fileSize != -1 && file.length() != fileSize)
					Logger.getGlobal().warning("File size is different from the transfer size reported by the TFTP Server.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			socket.close();
			Thread.currentThread().interrupt();
			return;
		}
	}

	// Handle TFTP Acknowledge message: send data to server
	private void handleAcknowledge(AcknowledgeMessage ackMsg) {
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
					socket.close();
					Thread.currentThread().interrupt();
					return;
				}
			}

			DataMessage msgData = new DataMessage(ackMsg.getBlockNumber() + 1, Arrays.copyOfRange(b, 0, n));
			send(msgData);
		} catch (IOException e) {
			ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
			send(errorMsg);

			Logger.getGlobal().warning("Cannot read file");
			socket.close();
			Thread.currentThread().interrupt();
			return;
		}
	}

	// Handle TFTP Error message
	private void handleError(ErrorMessage msgError) {
		Logger.getGlobal().info("Error (" + msgError.getErrorCode() + "): " + msgError.getErrorMsg());
		socket.close();
		Thread.currentThread().interrupt();
		return;
	}

	// Start session that will handle the TFTP client request
	public void run()
	{
		// Parse TFTP Options
		for(Entry<String, String> entry : options.entrySet()) {
			switch(entry.getKey()) {
				case "blksize": {
					int tmp = Integer.parseInt(entry.getValue());
					if(bSizeMax == -1 || bSizeMax >= tmp)
						bSize = tmp;
					else {
						bSize = bSizeMax;
						entry.setValue(Integer.toString(bSize));
					}
				} break;

				case "tsize": {
					switch(opcode) {
						case TFTPMessage.RRQ: {
							try {
								entry.setValue(Long.toString(file.length()));
							} catch (IOException e) {
								e.printStackTrace();
							}
						} break;

						case TFTPMessage.WRQ: {
							long fileSize = Integer.parseInt(entry.getValue());
							if(tSizeMax != -1 && fileSize > tSizeMax) {
								Logger.getGlobal().warning("File to upload exceeds the maximum size allowed");
								ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.DISK_FULL_OR_ALLOCATION_EXCEEDED);
								send(errorMsg);

								socket.close();
								Thread.currentThread().interrupt();
								return;
							}
							
						} break;
					}
				} break;

				default:
					// Option not supported
					options.remove(entry.getKey());
			}
		}

		if(options.size() != 0) {
			OptionAcknowledgeMessage msgOAck = new OptionAcknowledgeMessage(options);
			send(msgOAck);

			return;
		} else {
			// Response if no options to acknowledge
			if(opcode == TFTPMessage.RRQ) {
				// Fake acknowledge message to start sending the file
				AcknowledgeMessage msgAck = new AcknowledgeMessage(0);
				handleAcknowledge(msgAck);
			} else if(opcode == TFTPMessage.WRQ) {
				AcknowledgeMessage msgAck = new AcknowledgeMessage(0);
				send(msgAck);
			}
		}
	}

	boolean isInitialized() {
		return initialized;
	}
}