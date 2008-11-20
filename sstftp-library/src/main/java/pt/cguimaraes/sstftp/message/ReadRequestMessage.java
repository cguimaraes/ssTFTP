//=============================================================================
// Brief     : Read Request Message
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

public class ReadRequestMessage extends TFTPMessage {

	private String fileName;
	private String mode;

	public ReadRequestMessage() {
		super();
		this.opcode = RRQ;
	}

	public ReadRequestMessage(InetAddress address, int port) {
		super(address, port);
	}

	public ReadRequestMessage(String fileName, String mode) {
		super();
		this.opcode = RRQ;
		this.fileName = fileName;
		this.mode = mode;
	}

	public void toBytes(ByteArrayOutputStream stream) {
		super.toBytes(stream);

		byte[] tmp = fileName.getBytes();
		stream.write(tmp, 0, tmp.length);
		stream.write(0);
		tmp = mode.getBytes();
		stream.write(tmp, 0, tmp.length);
		stream.write(0);
	}

	public void fromBytes(ByteArrayInputStream stream) {
		super.fromBytes(stream);

		StringBuilder strBuilder = new StringBuilder();
		byte tmp;
		while((tmp = (byte) stream.read()) != 0x00) {
			strBuilder.append((char)tmp);
		}
		fileName = strBuilder.toString();

		strBuilder = new StringBuilder();
		while((tmp = (byte) stream.read()) != 0x00) {
			strBuilder.append((char)tmp);
		}
		mode = strBuilder.toString();
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}
}
