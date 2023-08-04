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
import java.util.Timer;
import java.util.TimerTask;
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

    private int retries;
    private int blksize;
    private int windowsize;
    private int interval;
    private long fileSize = -1;
    private HashMap<String, String> options;

    private String path;
    private RandomAccessFile file;
    private boolean sentLast = false;

    private Timer timer;
    private TFTPMessage retransmitMsg;

    private int lastAck = 0;
    private int lastData = 0;

    public TFTPClient(InetAddress dstIp, int dstPort, String action, String mode, String path,
            int retries, int interval, int blksize, int windowsize, HashMap<String, String> options)
            throws NoSuchMethodException, SecurityException, SocketException, FileNotFoundException {

        this.action = action;
        this.mode = mode;
        this.retries = retries;
        this.blksize = blksize;
        this.windowsize = windowsize;
        this.interval = interval;
        this.options = options;
        this.path = path;

        Method handler = null;
        if (action.equals("put")) {
            handler = TFTPClient.class.getMethod("handler_put", new Class[] { TFTPMessage.class });
        } else if (action.equals("get")) {
            handler = TFTPClient.class.getMethod("handler_get", new Class[] { TFTPMessage.class });
        }

        socket = new TFTPSocket(dstIp, dstPort, this, handler);
    }

    public void run() throws FileNotFoundException {
        if (action.equals("put")) {
            WriteRequestMessage msgWRQ = new WriteRequestMessage(path, mode, options);
            retransmitMsg = msgWRQ;

            socket.send(msgWRQ);
            file = new RandomAccessFile(path, "r");

            Logger.getGlobal().info("Uploading " + path + " to server in " + mode + " mode...");
        } else if (action.equals("get")) {
            ReadRequestMessage msgRRQ = new ReadRequestMessage(path, mode, options);
            retransmitMsg = msgRRQ;

            socket.send(msgRRQ);
            file = new RandomAccessFile(path, "rw");

            Logger.getGlobal().info("Downloading " + path + " from server in " + mode + " mode...");
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {
            int i = 0;

            public void run() {
                if (i < retries) {
                    socket.send(retransmitMsg);
                    i++;
                }
            }
        }, interval, interval);

        socket.run();
    }

    // TFTP message handler for PUT action
    public void handler_put(TFTPMessage msg) {
        stopTimer(timer);
        switch (msg.getOpcode()) {
            case TFTPMessage.ACK: {
                stopTimer(timer);
                AcknowledgeMessage msgAck = (AcknowledgeMessage) msg;
                handleAcknowledge(msgAck);
                break;
            }

            case TFTPMessage.ERROR: {
                stopTimer(timer);
                ErrorMessage msgError = (ErrorMessage) msg;
                handleError(msgError);
                break;
            }

            case TFTPMessage.OACK: {
                stopTimer(timer);
                OptionAcknowledgeMessage msgOAck = (OptionAcknowledgeMessage) msg;
                handleOptionAcknowledge(msgOAck, action);
                break;
            }

            default: {
                stopTimer(timer);
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
                stopTimer(timer);
                ErrorMessage msgError = (ErrorMessage) msg;
                handleError(msgError);
                break;
            }

            case TFTPMessage.OACK: {
                stopTimer(timer);
                OptionAcknowledgeMessage msgOAck = (OptionAcknowledgeMessage) msg;
                handleOptionAcknowledge(msgOAck, action);
                break;
            }

            default: {
                stopTimer(timer);
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
        boolean sendAck = false;
        if (lastData + 1 == msgData.getBlockNumber()) {
            lastData = msgData.getBlockNumber();
            try {
                if (mode.equals("octet")) {
                    file.write(msgData.getData(), 0, msgData.getData().length);
                } else if (mode.equals("netascii")) {
                    @SuppressWarnings("resource")
                    FromNetASCIIOutputStream is = new FromNetASCIIOutputStream(
                            Channels.newOutputStream(file.getChannel()));
                    is.write(msgData.getData(), 0, msgData.getData().length);
                }
            } catch (IOException e) {
                ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
                socket.send(errorMsg);

                Logger.getGlobal().warning("Cannot write on file");
                System.exit(1);
            }
        } else {
            sendAck = true;
        }

        // Acknowledge the TFTP Data message
        if (sendAck == true || lastAck + windowsize == msgData.getBlockNumber()) {
            stopTimer(timer);
            AcknowledgeMessage msgAck = new AcknowledgeMessage(msgData.getBlockNumber());
            lastAck = msgAck.getBlockNumber();
            socket.send(msgAck);

            timer = new Timer();
            timer.schedule(new TimerTask() {
                int i = 0;

                public void run() {
                    if (i < retries) {
                        AcknowledgeMessage msgAck = new AcknowledgeMessage(lastData);
                        lastAck = msgAck.getBlockNumber();
                        socket.send(msgAck);
                        i++;
                    }
                }
            }, interval, interval);
        }

        // If data length lower than block size, transfer is complete
        if (msgData.getData().length < blksize) {
            stopTimer(timer);
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
        if (msgAck.getBlockNumber() <= lastAck || msgAck.getBlockNumber() > lastAck + windowsize) {
            return;
        }
        retransmitMsg = msgAck;

        for (int i = msgAck.getBlockNumber(); i > msgAck.getBlockNumber() + windowsize; i++) {
            int n = -1;
            byte[] b = new byte[blksize];
            try {
                file.seek(i * blksize);
                if (mode.equals("octet")) {
                    n = file.read(b);
                } else if (mode.equals("netascii")) {
                    @SuppressWarnings("resource")
                    ToNetASCIIInputStream is = new ToNetASCIIInputStream(Channels.newInputStream(file.getChannel()));
                    n = is.read(b);
                }
            } catch (IOException e) {
                ErrorMessage msgError = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
                socket.send(msgError);

                Logger.getGlobal().warning("Cannot read file");
                System.exit(1);
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

            DataMessage msgData = new DataMessage(msgAck.getBlockNumber() + i, Arrays.copyOfRange(b, 0, n));
            socket.send(msgData);
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {
            int i = 0;

            public void run() {
                if (i < retries) {
                    handleAcknowledge((AcknowledgeMessage) retransmitMsg);
                    i++;
                }
            }
        }, interval, interval);
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

                    case "windowsize": {
                        int receivedWindowsize = Integer.parseInt(entry.getValue());
                        if (receivedWindowsize > 0 && receivedWindowsize <= windowsize) {
                            windowsize = receivedWindowsize;
                        } else {
                            ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                            socket.send(msgError);

                            Logger.getGlobal()
                                    .warning(
                                            "Window size is higher than the one defined by client or not in a valid range");
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

    private void stopTimer(Timer timer) {
        timer.cancel();
        timer.purge();
    }
}
