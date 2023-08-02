//=============================================================================
// Brief     : Test Message Serialization/Deserialization
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

package pt.cguimaraes.sstftp.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;

class MessageTest {
    public static void main(String args[]) throws Exception {
        {
            String fileName = "ssTFTP.txt";
            String mode = "octet";

            System.out.println("Testing ReadRequestMessage...");
            ReadRequestMessage msg = new ReadRequestMessage(fileName, mode);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            msg.toBytes(stream);

            ByteArrayInputStream stream2 = new ByteArrayInputStream(stream.toByteArray());
            ReadRequestMessage msg2 = new ReadRequestMessage();
            msg2.fromBytes(stream2);

            if (msg2.getOpcode() == TFTPMessage.RRQ) {
                System.out.println("Opcode: check");
            } else {
                System.out.println("Opcode: not check");
            }

            if (msg2.getFileName().compareTo(fileName) == 0) {
                System.out.println("Filename: check");
            } else {
                System.out.println("Filename: not check");
            }

            if (msg2.getMode().compareTo(mode) == 0) {
                System.out.println("Mode: check");
            } else {
                System.out.println("Mode: not check");
            }

            System.out.println("\n");
        }

        {
            String fileName = "ssTFTP.txt";
            String mode = "octet";

            System.out.println("Testing WriteRequestMessage...");
            WriteRequestMessage msg = new WriteRequestMessage(fileName, mode);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            msg.toBytes(stream);

            ByteArrayInputStream stream2 = new ByteArrayInputStream(stream.toByteArray());
            WriteRequestMessage msg2 = new WriteRequestMessage();
            msg2.fromBytes(stream2);

            if (msg2.getOpcode() == TFTPMessage.WRQ) {
                System.out.println("Opcode: check");
            } else {
                System.out.println("Opcode: not check");
            }

            if (msg2.getFileName().compareTo(fileName) == 0) {
                System.out.println("Filename: check");
            } else {
                System.out.println("Filename: not check");
            }

            if (msg2.getMode().compareTo(mode) == 0) {
                System.out.println("Mode: check");
            } else {
                System.out.println("Mode: not check");
            }

            System.out.println("\n");
        }

        {
            String data = "dataaaa";
            int blockNumber = 3;

            System.out.println("Testing DataMessage...");
            DataMessage msg = new DataMessage(blockNumber, new String(data).getBytes());
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            msg.toBytes(stream);

            ByteArrayInputStream stream2 = new ByteArrayInputStream(stream.toByteArray());
            DataMessage msg2 = new DataMessage();
            msg2.fromBytes(stream2);

            if (msg2.getOpcode() == TFTPMessage.DATA) {
                System.out.println("Opcode: check");
            } else {
                System.out.println("Opcode: not check");
            }

            if (msg2.getBlockNumber() == blockNumber) {
                System.out.println("Block number: check");
            } else {
                System.out.println("Block number: not check");
            }

            if (new String(msg2.getData()).compareTo(data) == 0) {
                System.out.println("Data: check");
            } else {
                System.out.println("Data: not check");
            }

            System.out.println("\n");
        }

        {
            int blockNumber = 5;

            System.out.println("Testing AcknowledgeMessage");
            AcknowledgeMessage msg = new AcknowledgeMessage(blockNumber);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            msg.toBytes(stream);

            ByteArrayInputStream stream2 = new ByteArrayInputStream(stream.toByteArray());
            AcknowledgeMessage msg2 = new AcknowledgeMessage();
            msg2.fromBytes(stream2);

            if (msg2.getOpcode() == TFTPMessage.ACK) {
                System.out.println("Opcode: check");
            } else {
                System.out.println("Opcode: not check");
            }

            if (msg2.getBlockNumber() == blockNumber) {
                System.out.println("Block number: check");
            } else {
                System.out.println("Block number: not check");
            }

            System.out.println("\n");
        }

        {
            int errorCode = 5;
            String errorMsg = "Human readable error message";

            System.out.println("Testing ErrorMessage...");
            ErrorMessage msg = new ErrorMessage(errorCode, errorMsg);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            msg.toBytes(stream);

            ByteArrayInputStream stream2 = new ByteArrayInputStream(stream.toByteArray());
            ErrorMessage msg2 = new ErrorMessage();
            msg2.fromBytes(stream2);

            if (msg2.getOpcode() == TFTPMessage.ERROR) {
                System.out.println("Opcode: check");
            } else {
                System.out.println("Opcode: not check");
            }

            if (msg2.getErrorCode() == errorCode) {
                System.out.println("Error Code: check");
            } else {
                System.out.println("Error Code: not check");
            }

            if (msg2.getErrorMsg().compareTo(errorMsg) == 0) {
                System.out.println("Error Message: check");
            } else {
                System.out.println("Error Message: not check");
            }

            System.out.println("\n");
        }
    }
}
