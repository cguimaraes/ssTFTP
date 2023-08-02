//=============================================================================
// Brief     : Error Message
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

public class ErrorMessage extends TFTPMessage {

    public final static int NOT_DEFINED = 0;
    public final static int FILE_NOT_FOUND = 1;
    public final static int ACCESS_VIOLATION = 2;
    public final static int DISK_FULL_OR_ALLOCATION_EXCEEDED = 3;
    public final static int ILLEGAL_TFTP_OPERATION = 4;
    public final static int UNKNOWN_TRANSFER_ID = 5;
    public final static int FILE_ALREADY_EXISTS = 6;
    public final static int NO_SUCH_USER = 7;
    private final static String[] errorString = {
            "Not defined",
            "File not found",
            "Access violation",
            "Disk full or allocation exceeded",
            "Illegal TFTP operation",
            "Unknown transfer ID",
            "File already exists",
            "No such user"
    };

    private int errorCode;
    private String errorMsg;

    public ErrorMessage() {
        super();
        this.opcode = ERROR;
        this.errorCode = 0;
    }

    public ErrorMessage(int errorCode, String errorMsg) {
        super();
        this.opcode = ERROR;
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public ErrorMessage(InetAddress address, int port) {
        super(address, port);
    }

    public ErrorMessage(int errorCode) {
        super();
        this.opcode = ERROR;
        this.errorCode = errorCode;
        this.errorMsg = errorString[errorCode];
    }

    public void toBytes(ByteArrayOutputStream stream) {
        super.toBytes(stream);
        stream.write((byte) ((errorCode & 0xFF00) >> 8));
        stream.write((byte) (errorCode & 0x00FF));

        byte[] tmp = errorMsg.getBytes();
        stream.write(tmp, 0, tmp.length);
        stream.write(0);
    }

    public void fromBytes(ByteArrayInputStream stream) {
        super.fromBytes(stream);

        errorCode = (stream.read() << 8) | stream.read();

        StringBuilder strBuilder = new StringBuilder();
        byte tmp;
        while ((tmp = (byte) stream.read()) != 0x00) {
            strBuilder.append((char) tmp);
        }
        errorMsg = strBuilder.toString();
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
