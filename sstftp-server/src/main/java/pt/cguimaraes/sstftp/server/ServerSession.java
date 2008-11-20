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
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.logging.Logger;

import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class ServerSession extends Thread
{
	private TFTPSocket socket;
	private int bSize;
	private RandomAccessFile file;
	private short opcode;
	private String mode;
	private boolean sentLast = false;
	private boolean initialized = true;

	public ServerSession(String localDir, String fileName, String mode, int action, int port, InetAddress ipAddress, short opcode) throws NoSuchMethodException, SecurityException, SocketException
	{
		this.bSize = 512; // Default block size
		this.opcode = opcode;
		this.mode = mode;

		Method handler = ServerSession.class.getMethod("handler", new Class[]{TFTPMessage.class});
		this.socket = new TFTPSocket(ipAddress, port, this, handler);
		Thread t = new Thread(socket);
		t.start();

		try {
			if (action == 0)
				file = new RandomAccessFile(localDir + "/" + fileName, "r");
			else
				file = new RandomAccessFile(localDir + "/" + fileName, "rw");
		} catch (FileNotFoundException e) {
			ErrorMessage msgError = new ErrorMessage(ErrorMessage.FILE_NOT_FOUND);
			send(msgError);

			initialized = false;
			Logger.getGlobal().info("File not found");
		}
	}

	public void send(TFTPMessage msg) {
		try {
			socket.send(msg);
		} catch (IOException e) {
			Logger.getGlobal().severe("Socket exception");
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
				socket.close();
				Thread.currentThread().interrupt();
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
		}

		// Acknowledge the TFTP Data message
		AcknowledgeMessage msgAck = new AcknowledgeMessage(dataMsg.getBlockNumber());
		send(msgAck);

		// If data length lower than block size, transfer is complete
		if(dataMsg.getData().length < bSize) {
			Logger.getGlobal().info("Transfer complete");
			socket.close();
			Thread.currentThread().interrupt();
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
		}
	}

	// Handle TFTP Error message
	private void handleError(ErrorMessage msgError) {
		Logger.getGlobal().info("Error (" + msgError.getErrorCode() + "): " + msgError.getErrorMsg());
		socket.close();
		Thread.currentThread().interrupt();
	}

	// Start session that will handle the TFTP client request
	public void run()
	{
		if(!initialized) {
			socket.close();
			Thread.currentThread().interrupt();
			return;
		}

		if(opcode == TFTPMessage.RRQ) {
			// Fake acknowledge message corresponding to first bytes
			AcknowledgeMessage ackMsg = new AcknowledgeMessage(0);
			handleAcknowledge(ackMsg);
		} else if(opcode == TFTPMessage.WRQ) {
			AcknowledgeMessage ackMsg = new AcknowledgeMessage(0);
			send(ackMsg);
		}
	}
}