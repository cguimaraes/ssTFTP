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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class TFTPServer extends Thread {

	private TFTPSocket socket;

	private String localDir;
	private int retries;
	private int timeout;
	private int blksize;

	public TFTPServer(int port, String localDir, int retries, int timeout, int blksize) throws SocketException, NoSuchMethodException, SecurityException, UnknownHostException {
		this.localDir = localDir;
		this.retries  = retries;
		this.timeout  = timeout;
		this.blksize  = blksize;

		Method handler = TFTPServer.class.getMethod("handler", new Class[]{TFTPMessage.class});

		socket = new TFTPSocket(this, handler);
		socket.bind(InetAddress.getByName("0.0.0.0"), port);
		socket.setRetries(retries);
		socket.setTimeout(timeout);

		Thread t = new Thread(socket);
        t.start();
	}

	public void send(TFTPMessage msg) {
		try {
			socket.send(msg);
		} catch (IOException e) {
			Logger.getGlobal().severe(e.getMessage());
		}
	}

	// Generic TFTP message handler
	public void handler(TFTPMessage msg) throws IOException, NoSuchMethodException, SecurityException, SocketException {
		ServerSession session = null;

		session = new ServerSession(localDir, retries, timeout, blksize, msg);

		if(session.isInitialized())
			session.start();
	}
}