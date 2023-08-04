//=============================================================================
// Brief     : TFTP Server Session
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

package pt.cguimaraes.sstftp.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
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

public class ServerSession {

    private TFTPSocket socket;

    private int bSize;
    private int bSizeMax;
    private int windowsize;
    private int retries;
    private int interval;
    private long fileSize;
    private long tSizeMax;
    private int opcode;
    private String mode;
    private HashMap<String, String> options;

    private RandomAccessFile file;

    private boolean sentLast = false;
    private boolean initialized = true;

    private Timer timer;
    private TFTPMessage retransmitMsg;

    private int lastAck = 0;
    private int lastData = 0;

    public ServerSession(String localDir, int retries, int interval, int bSizeMax, long tSizeMax, int windowsize,
            TFTPMessage msg)
            throws NoSuchMethodException, SecurityException, SocketException, Exception {
        // Initialize TFTP Socket
        Method handler = null;
        switch (msg.getOpcode()) {
            case TFTPMessage.RRQ: {
                handler = ServerSession.class.getMethod("handler_get", new Class[] { TFTPMessage.class });
                break;
            }

            case TFTPMessage.WRQ: {
                handler = ServerSession.class.getMethod("handler_put", new Class[] { TFTPMessage.class });
                break;
            }

            default: {
                throw new Exception("Invalid initial request message type");
            }
        }

        this.socket = new TFTPSocket(msg.getIp(), msg.getPort(), this, handler);

        // Configure session
        this.bSizeMax = bSizeMax;
        this.bSize = 512; // Default block size
        this.retries = retries;
        this.windowsize = windowsize;
        this.interval = interval;
        this.tSizeMax = tSizeMax;
        this.fileSize = -1;

        try {
            switch (msg.getOpcode()) {
                case TFTPMessage.RRQ: {
                    ReadRequestMessage msgRRQ = (ReadRequestMessage) msg;
                    this.mode = msgRRQ.getMode();
                    this.opcode = msgRRQ.getOpcode();
                    this.options = msgRRQ.getOptions();

                    file = new RandomAccessFile(localDir + msgRRQ.getFileName(), "r");
                    break;
                }

                case TFTPMessage.WRQ: {
                    WriteRequestMessage msgWRQ = (WriteRequestMessage) msg;
                    this.mode = msgWRQ.getMode();
                    this.opcode = msgWRQ.getOpcode();
                    this.options = msgWRQ.getOptions();

                    file = new RandomAccessFile(localDir + msgWRQ.getFileName(), "rw");
                    break;
                }

                default: {
                    // Do nothing
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            initialized = false;
            ErrorMessage msgError = new ErrorMessage(ErrorMessage.FILE_NOT_FOUND);
            socket.send(msgError);

            Logger.getGlobal().info("File not found");
        }
    }

    // TFTP message handler for GET action
    public void handler_get(TFTPMessage msg) {
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

    // TFTP message handler for PUT action
    public void handler_put(TFTPMessage msg) {
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
        if (msgData.getData().length < bSize) {
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
            byte[] b = new byte[bSize];
            try {
                file.seek(i * bSize);
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
            if (n < bSize) {
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
        return;
    }

    // Start session that will handle the TFTP client request
    public void run() {
        socket.run();

        // Parse TFTP Options
        for (Entry<String, String> entry : options.entrySet()) {
            switch (entry.getKey()) {
                case "blksize": {
                    int tmp = Integer.parseInt(entry.getValue());
                    if (bSizeMax == -1 || bSizeMax >= tmp)
                        bSize = tmp;
                    else {
                        bSize = bSizeMax;
                        entry.setValue(Integer.toString(bSize));
                    }
                    break;
                }

                case "tsize": {
                    switch (opcode) {
                        case TFTPMessage.RRQ: {
                            try {
                                entry.setValue(Long.toString(file.length()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        }

                        case TFTPMessage.WRQ: {
                            fileSize = Integer.parseInt(entry.getValue());
                            if (tSizeMax != -1 && fileSize > tSizeMax) {
                                Logger.getGlobal().warning("File to upload exceeds the maximum size allowed");
                                ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.DISK_FULL_OR_ALLOCATION_EXCEEDED);
                                socket.send(errorMsg);

                                socket.close();
                                return;
                            }
                            break;
                        }
                    }
                    break;
                }

                case "interval": {
                    int receivedInterval = Integer.parseInt(entry.getValue());
                    if (receivedInterval > 0 && receivedInterval < 256) { // Always accept interval request by client if
                                                                          // in valid range
                        interval = receivedInterval * 1000;
                    } else {
                        Logger.getGlobal().warning("Received timeout interval option is out of accepted range");
                        ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                        socket.send(errorMsg);

                        socket.close();
                        return;
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

                default:
                    // Option not supported
                    options.remove(entry.getKey());
            }
        }

        if (options.size() != 0) {
            OptionAcknowledgeMessage msgOAck = new OptionAcknowledgeMessage(options);
            socket.send(msgOAck);
            return;
        }

        // Response if no options to acknowledge
        if (opcode == TFTPMessage.RRQ) {
            // Fake acknowledge message to start sending the file
            AcknowledgeMessage msgAck = new AcknowledgeMessage(0);
            handleAcknowledge(msgAck);
        } else if (opcode == TFTPMessage.WRQ) {
            AcknowledgeMessage msgAck = new AcknowledgeMessage(0);
            socket.send(msgAck);
        }
    }

    boolean isInitialized() {
        return initialized;
    }

    private void stopTimer(Timer timer) {
        timer.cancel();
        timer.purge();
    }
}
