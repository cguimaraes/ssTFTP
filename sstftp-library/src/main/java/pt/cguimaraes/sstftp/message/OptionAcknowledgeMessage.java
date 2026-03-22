//=============================================================================
// Brief     : Acknowledge Message
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

public class OptionAcknowledgeMessage extends TFTPMessage {

    private HashMap<String, String> options;

    public OptionAcknowledgeMessage() {
        super();
        this.opcode = OACK;
        this.options = new HashMap<String, String>();
    }

    public OptionAcknowledgeMessage(InetAddress address, int port) {
        super(address, port);
        this.options = new HashMap<String, String>();
    }

    public OptionAcknowledgeMessage(HashMap<String, String> options) {
        super();
        this.opcode = OACK;
        this.options = options;
    }

    public void toBytes(ByteArrayOutputStream stream) {
        super.toBytes(stream);

        byte[] tmp;
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

        while (stream.available() > 0) {
            int optByte;
            StringBuilder optBuilder = new StringBuilder();

            // Read option name
            while ((optByte = stream.read()) != -1 && optByte != 0x00) {
                optBuilder.append((char) optByte);
            }
            if (optByte == -1) {
                break;  // Premature EOF
            }
            String opt = optBuilder.toString();

            // Read option value
            StringBuilder valBuilder = new StringBuilder();
            while ((optByte = stream.read()) != -1 && optByte != 0x00) {
                valBuilder.append((char) optByte);
            }
            if (optByte == -1) {
                throw new IllegalArgumentException("Invalid OACK: incomplete option pair (missing value)");
            }
            String value = valBuilder.toString();

            options.put(opt, value);
        }
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
