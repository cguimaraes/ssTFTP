//=============================================================================
// Brief     : Acknowledge Message
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

public class AcknowledgeMessage extends TFTPMessage {

	private int blockNumber;

	public AcknowledgeMessage() {
		super();
		this.opcode = ACK;
		blockNumber = 0;
	}

	public AcknowledgeMessage(InetAddress address, int port) {
		super(address, port);
	}

	public AcknowledgeMessage(int blockNumber) {
		super();
		this.opcode = ACK;
		this.blockNumber = blockNumber;
	}

	public void toBytes(ByteArrayOutputStream stream) {
		super.toBytes(stream);
		stream.write((byte) ((blockNumber & 0xFF00) >> 8));
        stream.write((byte) (blockNumber & 0x00FF));
	}

	public void fromBytes(ByteArrayInputStream stream) {
		super.fromBytes(stream);
		blockNumber = (stream.read() << 8) | stream.read();
	}

	public int getBlockNumber() {
		return blockNumber;
	}

	public void setBlockNumber(int blockNumber) {
		this.blockNumber = blockNumber;
	}
}
