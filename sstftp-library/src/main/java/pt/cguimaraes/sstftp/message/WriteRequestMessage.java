//=============================================================================
// Brief     : Write Request Message
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
import java.util.HashMap;
import java.util.Map.Entry;

public class WriteRequestMessage extends TFTPMessage {

    private String fileName;
    private String mode;
    private HashMap<String, String> options;

    public WriteRequestMessage() {
        super();
        this.opcode = WRQ;
        this.options = new HashMap<String, String>();
    }

    public WriteRequestMessage(InetAddress address, int port) {
        super(address, port);
        this.options = new HashMap<String, String>();
    }

    public WriteRequestMessage(String fileName, String mode) {
        super();
        this.opcode = WRQ;
        this.fileName = fileName;
        this.mode = mode;
        this.options = new HashMap<String, String>();
    }

    public WriteRequestMessage(String fileName, String mode, HashMap<String, String> options) {
        super();
        this.opcode = WRQ;
        this.fileName = fileName;
        this.mode = mode;
        this.options = options;
    }

    public void toBytes(ByteArrayOutputStream stream) {
        super.toBytes(stream);

        byte[] tmp = fileName.getBytes(StandardCharsets.US_ASCII);
        stream.write(tmp, 0, tmp.length);
        stream.write(0);
        tmp = mode.getBytes(StandardCharsets.US_ASCII);
        stream.write(tmp, 0, tmp.length);
        stream.write(0);

        for (Entry<String, String> entry : options.entrySet()) {
            tmp = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            stream.write(tmp, 0, tmp.length);
            stream.write(0);

            tmp = entry.getValue().getBytes(StandardCharsets.US_ASCII);
            stream.write(tmp, 0, tmp.length);
            stream.write(0);
        }
    }

    public void fromBytes(ByteArrayInputStream stream) {
        super.fromBytes(stream);

        StringBuilder strBuilder = new StringBuilder();
        int tmp;

        // Parse filename
        while ((tmp = stream.read()) != -1 && tmp != 0x00) {
            strBuilder.append((char) tmp);
        }
        if (tmp == -1) {
            throw new IllegalArgumentException("Invalid WRQ: missing null terminator after filename");
        }
        fileName = strBuilder.toString();
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("Invalid WRQ: filename cannot be empty");
        }

        // Parse mode
        strBuilder = new StringBuilder();
        while ((tmp = stream.read()) != -1 && tmp != 0x00) {
            strBuilder.append((char) tmp);
        }
        if (tmp == -1) {
            throw new IllegalArgumentException("Invalid WRQ: missing null terminator after mode");
        }
        mode = strBuilder.toString();
        if (mode.isEmpty()) {
            throw new IllegalArgumentException("Invalid WRQ: mode cannot be empty");
        }

        // Parse options
        while (stream.available() > 0) {
            String opt;
            String value;

            strBuilder = new StringBuilder();
            while ((tmp = stream.read()) != -1 && tmp != 0x00) {
                strBuilder.append((char) tmp);
            }
            opt = strBuilder.toString();
            if (opt.isEmpty()) {
                break;  // End of options
            }

            strBuilder = new StringBuilder();
            while ((tmp = stream.read()) != -1 && tmp != 0x00) {
                strBuilder.append((char) tmp);
            }
            if (tmp == -1) {
                throw new IllegalArgumentException("Invalid WRQ: missing null terminator after option value");
            }
            value = strBuilder.toString();

            options.put(opt, value);
        }
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

    public HashMap<String, String> getOptions() {
        return options;
    }

    public void setOptions(HashMap<String, String> options) {
        this.options = options;
    }

    public void setOption(String opt, String value) {
        options.put(opt, value);
    }
}
