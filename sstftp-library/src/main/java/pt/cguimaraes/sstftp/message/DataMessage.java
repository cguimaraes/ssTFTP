//=============================================================================
// Brief     : Data Message
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

public class DataMessage extends TFTPMessage {

    private int blockNumber;
    private byte[] data;

    public DataMessage() {
        super();
        this.opcode = DATA;
        blockNumber = 0;
        data = new byte[0];
    }

    public DataMessage(InetAddress address, int port) {
        super(address, port);
    }

    public DataMessage(int blockNumber, byte[] data) {
        super();
        this.opcode = DATA;
        this.blockNumber = blockNumber;
        this.data = data;
    }

    public void toBytes(ByteArrayOutputStream stream) {
        super.toBytes(stream);
        stream.write((byte) ((blockNumber & 0xFF00) >> 8));
        stream.write((byte) (blockNumber & 0x00FF));
        stream.write(data, 0, data.length);
    }

    public void fromBytes(ByteArrayInputStream stream) {
        super.fromBytes(stream);
        blockNumber = (stream.read() << 8) | stream.read();
        data = new byte[stream.available()];
        for (int i = 0; stream.available() > 0; ++i) {
            data[i] = (byte) stream.read();
        }
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
