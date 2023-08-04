//=============================================================================
// Brief     : TFTP Client Main
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

package pt.cguimaraes.sstftp.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.OptionAcknowledgeMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

public class TFTPClient {

    private TFTPSocket socket;

    private String action;
    private String mode;

    private int blksize;
    private int interval;
    private long fileSize;
    private HashMap<String, String> options;

    private RandomAccessFile file;
    private boolean sentLast = false;

    public TFTPClient(InetAddress dstIp, int dstPort, String action, String mode, String path,
            int retries, int interval, int blksize, HashMap<String, String> options)
            throws NoSuchMethodException, SecurityException, SocketException, FileNotFoundException {

        this.action = action;
        this.mode = mode;
        this.blksize = blksize;
        this.interval = interval;
        this.fileSize = -1;
        this.options = options;

        Method handler = null;
        if (action.equals("put")) {
            handler = TFTPClient.class.getMethod("handler_put", new Class[] { TFTPMessage.class });
        } else if (action.equals("get")) {
            handler = TFTPClient.class.getMethod("handler_get", new Class[] { TFTPMessage.class });
        }

        socket = new TFTPSocket(dstIp, dstPort, this, handler);
        socket.setRetries(retries);
        socket.setTimeout(interval);

        if (action.equals("put")) {
            WriteRequestMessage msgWRQ = new WriteRequestMessage(path, mode, options);
            socket.send(msgWRQ);
            file = new RandomAccessFile(path, "r");

            Logger.getGlobal().info("Uploading " + path + " to server in " + mode + " mode...");
        } else if (action.equals("get")) {
            ReadRequestMessage msgRRQ = new ReadRequestMessage(path, mode, options);
            socket.send(msgRRQ);

            file = new RandomAccessFile(path, "rw");
            Logger.getGlobal().info("Downloading " + path + " from server in " + mode + " mode...");
        }

        socket.run();
    }

    // TFTP message handler for PUT action
    public void handler_put(TFTPMessage msg) {
        switch (msg.getOpcode()) {
            case TFTPMessage.ACK: {
                AcknowledgeMessage msgAck = (AcknowledgeMessage) msg;
                handleAcknowledge(msgAck);
                break;
            }

            case TFTPMessage.ERROR: {
                ErrorMessage msgError = (ErrorMessage) msg;
                handleError(msgError);
                break;
            }

            case TFTPMessage.OACK: {
                OptionAcknowledgeMessage msgOAck = (OptionAcknowledgeMessage) msg;
                handleOptionAcknowledge(msgOAck, action);
                break;
            }

            default: {
                ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                socket.send(msgError);

                Logger.getGlobal().warning("Illegal TFTP Operation");
                System.exit(1);
                break;
            }
        }
    }

    // TFTP message handler for GET action
    public void handler_get(TFTPMessage msg) {
        switch (msg.getOpcode()) {
            case TFTPMessage.DATA: {
                DataMessage msgData = (DataMessage) msg;
                handleData(msgData);
                break;
            }

            case TFTPMessage.ERROR: {
                ErrorMessage msgError = (ErrorMessage) msg;
                handleError(msgError);
                break;
            }

            case TFTPMessage.OACK: {
                OptionAcknowledgeMessage msgOAck = (OptionAcknowledgeMessage) msg;
                handleOptionAcknowledge(msgOAck, action);
                break;
            }

            default: {
                ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                socket.send(msgError);

                Logger.getGlobal().warning("Illegal TFTP Operation");
                System.exit(1);
                break;
            }
        }
    }

    // Handle TFTP Data message: write data to file
    private void handleData(DataMessage msgData) {
        try {
            if (mode.equals("octet")) {
                file.write(msgData.getData(), 0, msgData.getData().length);
            } else if (mode.equals("netascii")) {
                @SuppressWarnings("resource")
                FromNetASCIIOutputStream is = new FromNetASCIIOutputStream(Channels.newOutputStream(file.getChannel()));
                is.write(msgData.getData(), 0, msgData.getData().length);
            }
        } catch (IOException e) {
            ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
            socket.send(errorMsg);

            Logger.getGlobal().warning("Cannot write on file");
            System.exit(1);
        }

        // Acknowledge the TFTP Data message
        AcknowledgeMessage msgAck = new AcknowledgeMessage(msgData.getBlockNumber());
        socket.send(msgAck);

        // If data length lower than block size, transfer is complete
        if (msgData.getData().length < blksize) {
            Logger.getGlobal().info("Transfer complete");
            try {
                if (fileSize != -1 && file.length() != fileSize) {
                    Logger.getGlobal()
                            .warning("File size is different from the transfer size reported by the TFTP Server.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.exit(0);
        }
    }

    // Handle TFTP Acknowledge message: send data to server
    private void handleAcknowledge(AcknowledgeMessage msgAck) {
        byte[] b = new byte[blksize];

        try {
            int n = -1;

            if (mode.equals("octet")) {
                n = file.read(b);
            } else if (mode.equals("netascii")) {
                @SuppressWarnings("resource")
                ToNetASCIIInputStream is = new ToNetASCIIInputStream(Channels.newInputStream(file.getChannel()));
                n = is.read(b);
            }

            // If data to send is lower than block size, transfer is complete
            // Note: transfer is only complete when acknowledge to the last
            // TFTP Data message is received
            if (n < blksize) {
                if (!sentLast) {
                    sentLast = true;

                    // If file.length % bSize == 0, send last data packet
                    // with no data
                    if (n == -1) {
                        n = 0;
                    }
                } else {
                    Logger.getGlobal().info("Transfer complete");
                    System.exit(0);
                }
            }

            DataMessage msgData = new DataMessage(msgAck.getBlockNumber() + 1, Arrays.copyOfRange(b, 0, n));
            socket.send(msgData);
        } catch (IOException e) {
            ErrorMessage msgError = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
            socket.send(msgError);

            Logger.getGlobal().warning("Cannot read file");
            System.exit(1);
        }
    }

    // Handle TFTP Error message
    private void handleError(ErrorMessage msgError) {
        Logger.getGlobal().info("Error (" + msgError.getErrorCode() + "): " + msgError.getErrorMsg());
        socket.close();
        System.exit(0);
    }

    // Handle TFTP Option Acknowledge message
    private void handleOptionAcknowledge(OptionAcknowledgeMessage msgOAck, String action) {
        HashMap<String, String> optionsOAck = msgOAck.getOptions();

        for (Entry<String, String> entry : optionsOAck.entrySet()) {
            // Process only options which negotiation was requested
            if (options.containsKey(entry.getKey())) {
                switch (entry.getKey()) {
                    case "blksize": {
                        blksize = Integer.parseInt(entry.getValue());
                        break;
                    }

                    case "tsize": {
                        fileSize = Long.parseLong(entry.getValue());
                        break;
                    }

                    case "interval": {
                        int receivedInterval = Integer.parseInt(entry.getValue()) * 1000;
                        if (receivedInterval > 0 && receivedInterval <= interval) {
                            interval = receivedInterval;
                            socket.setTimeout(interval);
                        } else {
                            ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                            socket.send(msgError);

                            Logger.getGlobal()
                                    .warning(
                                            "Timeout interval is higher than the one defined by client or not in a valid range");
                            System.exit(1);
                            break;
                        }
                        break;
                    }

                    default: {
                        optionsOAck.remove(entry.getKey()); // Option not supported
                        break;
                    }

                }
            }
        }

        if (action.compareTo("get") == 0) {
            AcknowledgeMessage ackMsg = new AcknowledgeMessage(0);
            socket.send(ackMsg);
        } else if (action.compareTo("put") == 0) {
            // Fake acknowledge message to start sending the file
            AcknowledgeMessage ackMsg = new AcknowledgeMessage(0);
            handleAcknowledge(ackMsg);
        }
    }
}
