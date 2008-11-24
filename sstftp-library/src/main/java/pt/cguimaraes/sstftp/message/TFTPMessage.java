//=============================================================================
// Brief     : TFTP Message
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

package pt.cguimaraes.sstftp.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;

public abstract class TFTPMessage {

	public final static int RRQ   = 1;
	public final static int WRQ   = 2;
	public final static int DATA  = 3;
	public final static int ACK   = 4;
	public final static int ERROR = 5;
	public final static int OACK  = 6;

	protected InetAddress ip;
	protected int port;
	protected int opcode;

	public TFTPMessage() {
		this.ip = null;
		this.port = -1;
	}

	public TFTPMessage(InetAddress ip, int port) {
		this.ip = ip;
		this.port = port;
	}

	public InetAddress getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public int getOpcode() {
		return opcode;
	}

	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}

	public void toBytes(ByteArrayOutputStream stream) {
		stream.write((byte) ((opcode & 0xFF00) >> 8));
        stream.write((byte) (opcode & 0x00FF));
	}

	public void fromBytes(ByteArrayInputStream stream) {
		opcode = (stream.read() << 8) | stream.read();
	}
}
