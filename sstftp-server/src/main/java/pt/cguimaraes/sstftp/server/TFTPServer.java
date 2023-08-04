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

import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class TFTPServer {

    private TFTPSocket socket;

    private String localDir;
    private int retries;
    private int interval;
    private int blksize;
    private int windowsize;
    private long tsize;

    public TFTPServer(int port, String localDir, int retries, int interval, int blksize, long tsize, int windowsize)
            throws SocketException, NoSuchMethodException, SecurityException, UnknownHostException {
        this.localDir = localDir;
        this.retries = retries;
        this.interval = interval;
        this.blksize = blksize;
        this.windowsize = windowsize;
        this.tsize = tsize;

        Method handler = TFTPServer.class.getMethod("handler", new Class[] { TFTPMessage.class });

        socket = new TFTPSocket(this, handler);
        socket.bind(InetAddress.getByName("0.0.0.0"), port);

        socket.run();
    }

    // Generic TFTP message handler
    public void handler(TFTPMessage msg)
            throws NoSuchMethodException, SecurityException, SocketException, Exception {
        ServerSession session = new ServerSession(localDir, retries, interval, blksize, tsize, windowsize, msg);
        if (session.isInitialized()) {
            session.run();
        }
    }
}
