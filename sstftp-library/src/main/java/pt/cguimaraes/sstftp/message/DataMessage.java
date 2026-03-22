//=============================================================================
// Brief     : Data Message
// Author(s) : Carlos Guimarães <carlos.em.guimaraes@gmail.com>
// ----------------------------------------------------------------------------
// ssTFTP - Super Simple Trivial File Transfer Protocol
//
// Copyright (C) 2008-2026 Carlos Guimarães
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
import java.nio.charset.StandardCharsets;

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
        if (data != null && data.length > 0) {
            stream.write(data, 0, data.length);
        }
    }

    public void fromBytes(ByteArrayInputStream stream) {
        super.fromBytes(stream);
        int highByte = stream.read();
        int lowByte = stream.read();
        if (highByte == -1 || lowByte == -1) {
            throw new IllegalArgumentException("Invalid DATA message: incomplete block number");
        }
        blockNumber = ((highByte & 0xFF) << 8) | (lowByte & 0xFF);

        int availableBytes = stream.available();
        if (availableBytes > 0) {
            data = new byte[availableBytes];
            int bytesRead = stream.read(data, 0, availableBytes);
            if (bytesRead != availableBytes) {
                throw new IllegalArgumentException("Invalid DATA message: failed to read all data");
            }
        } else {
            data = new byte[0];
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
