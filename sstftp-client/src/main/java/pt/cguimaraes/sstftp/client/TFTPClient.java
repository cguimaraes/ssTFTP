//=============================================================================
// Brief     : TFTP Client
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

package pt.cguimaraes.sstftp.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;

import pt.cguimaraes.sstftp.TFTPAction;
import pt.cguimaraes.sstftp.TFTPConstants;
import pt.cguimaraes.sstftp.TFTPMode;
import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.OptionAcknowledgeMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;
import pt.cguimaraes.sstftp.socket.TFTPSocket;

/**
 * TFTP Client implementation for downloading and uploading files.
 *
 * This client supports both GET (download) and PUT (upload) operations
 * with configurable block size, timeout, and retries.
 *
 * The client automatically handles:
 * - Message serialization and transmission
 * - Automatic retransmission on timeout
 * - File I/O with proper resource cleanup
 * - Both OCTET (binary) and NETASCII (text) transfer modes
 *
 * @author Carlos Guimarães
 * @version 0.2
 */
public class TFTPClient {

    private static final Logger LOGGER = Logger.getLogger(TFTPClient.class.getName());

    private TFTPSocket socket;

    private TFTPAction action;
    private TFTPMode mode;

    private int blksize;
    private int interval;
    private long fileSize;
    private HashMap<String, String> options;

    private RandomAccessFile file;
    private boolean sentLast = false;

    /**
     * Creates a TFTP client and initiates a file transfer.
     *
     * The client will either upload (PUT) or download (GET) the specified file
     * to/from the remote server. The method blocks until the transfer completes
     * or an error occurs.
     *
     * @param dstIp the IP address of the TFTP server
     * @param dstPort the port number of the TFTP server
     * @param action either "get" (download) or "put" (upload)
     * @param mode either "octet" (binary) or "netascii" (text)
     * @param path the file path (relative to server's root for GET, local path for PUT)
     * @param retries the number of retransmission attempts
     * @param interval the timeout interval in seconds
     * @param blksize the block size in bytes
     * @param options TFTP options map (can be null)
     *
     * @throws NoSuchMethodException if the handler method is not found
     * @throws SecurityException if access to methods is denied
     * @throws SocketException if the socket cannot be created
     * @throws FileNotFoundException if the local file is not found for PUT operations
     * @throws NullPointerException if required parameters are null
     */
    public TFTPClient(InetAddress dstIp, int dstPort, String action, String mode, String path,
            int retries, int interval, int blksize, HashMap<String, String> options)
            throws NoSuchMethodException, SecurityException, SocketException, FileNotFoundException {

        this.action = TFTPAction.fromString(Objects.requireNonNull(action, "action cannot be null"));
        this.mode = TFTPMode.fromString(Objects.requireNonNull(mode, "mode cannot be null"));
        this.blksize = blksize;
        this.interval = interval;
        this.fileSize = -1;
        this.options = options;

        Method handler = getHandlerMethod(this.action);

        socket = new TFTPSocket(dstIp, dstPort, this, handler);
        socket.setRetries(retries);
        socket.setTimeout(interval);

        if (this.action == TFTPAction.PUT) {
            WriteRequestMessage msgWRQ = new WriteRequestMessage(path, mode, options);
            socket.send(msgWRQ);
            file = new RandomAccessFile(path, "r");

            LOGGER.info("Uploading " + path + " to server in " + mode + " mode...");
        } else if (this.action == TFTPAction.GET) {
            ReadRequestMessage msgRRQ = new ReadRequestMessage(path, mode, options);
            socket.send(msgRRQ);

            file = new RandomAccessFile(path, "rw");
            LOGGER.info("Downloading " + path + " from server in " + mode + " mode...");
        }

        try {
            socket.run();
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing file", e);
                }
            }
        }
    }

    /**
     * Gets the appropriate handler method based on the action.
     *
     * @param action the TFTP action (GET or PUT)
     * @return the handler method
     * @throws NoSuchMethodException if the method is not found
     */
    private Method getHandlerMethod(TFTPAction action) throws NoSuchMethodException {
        if (action == TFTPAction.PUT) {
            return TFTPClient.class.getMethod("handler_put", new Class[] { TFTPMessage.class });
        } else {
            return TFTPClient.class.getMethod("handler_get", new Class[] { TFTPMessage.class });
        }
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

                LOGGER.warning("Illegal TFTP Operation");
                socket.close();
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

                LOGGER.warning("Illegal TFTP Operation");
                socket.close();
                break;
            }
        }
    }

    // Handle TFTP Data message: write data to file
    private void handleData(DataMessage msgData) {
        try {
            if (mode == TFTPMode.OCTET) {
                file.write(msgData.getData(), 0, msgData.getData().length);
            } else if (mode == TFTPMode.NETASCII) {
                try (FromNetASCIIOutputStream is = new FromNetASCIIOutputStream(Channels.newOutputStream(file.getChannel()))) {
                    is.write(msgData.getData(), 0, msgData.getData().length);
                }
            }
        } catch (IOException e) {
            ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
            socket.send(errorMsg);

            LOGGER.warning("Cannot write on file");
            socket.close();
            return;
        }

        // Acknowledge the TFTP Data message
        AcknowledgeMessage msgAck = new AcknowledgeMessage(msgData.getBlockNumber());
        socket.send(msgAck);

        // If data length lower than block size, transfer is complete
        if (msgData.getData().length < blksize) {
            LOGGER.info("Transfer complete");
            try {
                if (fileSize != -1 && file.length() != fileSize) {
                    LOGGER.warning("File size is different from the transfer size reported by the TFTP Server.");
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error checking file size", e);
            }

            socket.close();
        }
    }

    // Handle TFTP Acknowledge message: send data to server
    private void handleAcknowledge(AcknowledgeMessage msgAck) {
        byte[] b = new byte[blksize];

        try {
            int n = -1;

            if (mode == TFTPMode.OCTET) {
                n = file.read(b);
            } else if (mode == TFTPMode.NETASCII) {
                try (ToNetASCIIInputStream is = new ToNetASCIIInputStream(Channels.newInputStream(file.getChannel()))) {
                    n = is.read(b);
                }
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
                    LOGGER.info("Transfer complete");
                    socket.close();
                    return;
                }
            }

            int nextBlockNumber = (msgAck.getBlockNumber() + 1) & 0xFFFF;
            byte[] dataToSend = new byte[n];
            if (n > 0) {
                System.arraycopy(b, 0, dataToSend, 0, n);
            }
            DataMessage msgData = new DataMessage(nextBlockNumber, dataToSend);
            socket.send(msgData);
        } catch (IOException e) {
            ErrorMessage msgError = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
            socket.send(msgError);

            LOGGER.warning("Cannot read file");
            socket.close();
        }
    }

    // Handle TFTP Error message
    private void handleError(ErrorMessage msgError) {
        LOGGER.info("Error (" + msgError.getErrorCode() + "): " + msgError.getErrorMsg());
        socket.close();
    }

    // Handle TFTP Option Acknowledge message
    private void handleOptionAcknowledge(OptionAcknowledgeMessage msgOAck, TFTPAction action) {
        HashMap<String, String> optionsOAck = msgOAck.getOptions();

        // Check if options were requested
        if (options == null || options.isEmpty()) {
            LOGGER.warning("Received OACK but no options were negotiated");
            socket.close();
            return;
        }

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

                            LOGGER.warning(
                                    "Timeout interval is higher than the one defined by client or not in a valid range");
                            socket.close();
                            return;
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

        if (action == TFTPAction.GET) {
            AcknowledgeMessage ackMsg = new AcknowledgeMessage(0);
            socket.send(ackMsg);
        } else if (action == TFTPAction.PUT) {
            // Fake acknowledge message to start sending the file
            AcknowledgeMessage ackMsg = new AcknowledgeMessage(0);
            handleAcknowledge(ackMsg);
        }
    }
}
