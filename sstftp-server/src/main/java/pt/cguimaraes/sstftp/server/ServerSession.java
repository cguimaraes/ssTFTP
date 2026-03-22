//=============================================================================
// Brief     : TFTP Server Session
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

package pt.cguimaraes.sstftp.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
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

public class ServerSession implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ServerSession.class.getName());

    private TFTPSocket socket;

    private int bSize;
    private int bSizeMax;
    private long fileSize;
    private long tSizeMax;
    private int opcode;
    private TFTPMode mode;
    private HashMap<String, String> options;

    private RandomAccessFile file;

    private Thread socketThread;
    private boolean sentLast = false;
    private boolean initialized = true;

    /**
     * Creates a server session for handling a TFTP file transfer request.
     *
     * Initializes the session with server configuration and request message.
     * The session validates the request and prepares file resources.
     *
     * @param localDir the local server directory for file storage
     * @param retries the number of retransmission attempts
     * @param interval the timeout interval in seconds
     * @param bSizeMax the maximum allowed block size in bytes
     * @param tSizeMax the maximum allowed file transfer size in bytes (-1 for unlimited)
     * @param msg the initial TFTP request message (RRQ or WRQ)
     *
     * @throws NoSuchMethodException if the handler method is not found
     * @throws SecurityException if access to methods is denied
     * @throws SocketException if the socket cannot be created
     * @throws Exception if the request message type is invalid
     * @throws NullPointerException if required parameters are null
     */
    public ServerSession(String localDir, int retries, int interval, int bSizeMax, long tSizeMax, TFTPMessage msg)
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
        this.socket.setRetries(retries);
        this.socket.setTimeout(interval);

        // Configure session
        this.bSizeMax = bSizeMax;
        this.bSize = TFTPConstants.DEFAULT_BLOCK_SIZE;
        this.tSizeMax = tSizeMax;
        this.fileSize = -1;
        try {
            switch (msg.getOpcode()) {
                case TFTPMessage.RRQ: {
                    ReadRequestMessage msgRRQ = (ReadRequestMessage) msg;
                    this.mode = TFTPMode.fromString(msgRRQ.getMode());
                    this.opcode = msgRRQ.getOpcode();
                    this.options = msgRRQ.getOptions();

                    String filePath = validateAndNormalizePath(localDir, msgRRQ.getFileName());
                    file = new RandomAccessFile(filePath, "r");
                    break;
                }

                case TFTPMessage.WRQ: {
                    WriteRequestMessage msgWRQ = (WriteRequestMessage) msg;
                    this.mode = TFTPMode.fromString(msgWRQ.getMode());
                    this.opcode = msgWRQ.getOpcode();
                    this.options = msgWRQ.getOptions();

                    String filePath = validateAndNormalizePath(localDir, msgWRQ.getFileName());
                    // Ensure parent directory exists for write operations
                    Path filePathObj = Paths.get(filePath);
                    Files.createDirectories(filePathObj.getParent());
                    this.file = new RandomAccessFile(filePath, "rw");
                    break;
                }

                default: {
                    // Do nothing
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            initialized = false;
            if (this.file != null) {
                try {
                    this.file.close();
                } catch (IOException ignored) {
                    LOGGER.log(Level.WARNING, "Error closing file after FileNotFoundException", ignored);
                }
            }
            ErrorMessage msgError = new ErrorMessage(ErrorMessage.FILE_NOT_FOUND);
            socket.send(msgError);

            LOGGER.info("File not found");
        }

        Thread t = new Thread(socket);
        this.socketThread = t;
        t.start();
    }

    // TFTP message handler for GET action
    public void handler_get(TFTPMessage msg) {
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

            default: {
                ErrorMessage msgError = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                socket.send(msgError);

                LOGGER.warning("Illegal TFTP Operation");
                socket.close();
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
                ErrorMessage msgError = (ErrorMessage) msg;
                handleError(msgError);
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
    private void handleData(DataMessage dataMsg) {
        try {
            if (mode == TFTPMode.OCTET) {
                file.write(dataMsg.getData(), 0, dataMsg.getData().length);
            } else if (mode == TFTPMode.NETASCII) {
                try (FromNetASCIIOutputStream is = new FromNetASCIIOutputStream(Channels.newOutputStream(file.getChannel()))) {
                    is.write(dataMsg.getData(), 0, dataMsg.getData().length);
                }
            }
        } catch (IOException e) {
            ErrorMessage msgError = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
            socket.send(msgError);

            LOGGER.log(Level.WARNING, "Cannot write on file", e);
            socket.close();
            return;
        }

        // Acknowledge the TFTP Data message
        AcknowledgeMessage msgAck = new AcknowledgeMessage(dataMsg.getBlockNumber());
        socket.send(msgAck);

        // If data length lower than block size, transfer is complete
        if (dataMsg.getData().length < bSize) {
            LOGGER.info("Transfer complete");
            try {
                if (fileSize != -1 && file.length() != fileSize) {
                    LOGGER.warning("File size is different from the transfer size reported by the TFTP Server.");
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error checking file size", e);
            }

            socket.close();
            return;
        }
    }

    // Handle TFTP Acknowledge message: send data to server
    private void handleAcknowledge(AcknowledgeMessage ackMsg) {
        byte[] b = new byte[bSize];

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
            if (n < bSize) {
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

            int nextBlockNumber = (ackMsg.getBlockNumber() + 1) & 0xFFFF;
            byte[] dataToSend = (n > 0) ? new byte[n] : new byte[0];
            if (n > 0) {
                System.arraycopy(b, 0, dataToSend, 0, n);
            }
            DataMessage msgData = new DataMessage(nextBlockNumber, dataToSend);
            socket.send(msgData);
        } catch (IOException e) {
            ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ACCESS_VIOLATION);
            socket.send(errorMsg);

            LOGGER.warning("Cannot read file");
            socket.close();
            return;
        }
    }

    // Handle TFTP Error message
    private void handleError(ErrorMessage msgError) {
        LOGGER.info("Error (" + msgError.getErrorCode() + "): " + msgError.getErrorMsg());
        socket.close();
        return;
    }

    // Start session that will handle the TFTP client request
    public void run() {
        try {
            // Parse TFTP Options
            Iterator<Entry<String, String>> iterator = options.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, String> entry = iterator.next();
                switch (entry.getKey()) {
                    case "blksize": {
                        try {
                            int tmp = Integer.parseInt(entry.getValue());
                            if (tmp < TFTPConstants.DEFAULT_BLOCK_SIZE || tmp > TFTPConstants.MAX_BLOCK_SIZE) {
                                LOGGER.warning("Invalid block size: " + tmp + ", using maximum allowed");
                                tmp = Math.min(tmp, TFTPConstants.MAX_BLOCK_SIZE);
                            }
                            if (bSizeMax == -1 || bSizeMax >= tmp) {
                                bSize = tmp;
                            } else {
                                bSize = bSizeMax;
                                entry.setValue(Integer.toString(bSize));
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid block size format: " + e.getMessage());
                            iterator.remove();
                        }
                        break;
                    }

                    case "tsize": {
                        try {
                            switch (opcode) {
                                case TFTPMessage.RRQ: {
                                    try {
                                        entry.setValue(Long.toString(file.length()));
                                    } catch (IOException e) {
                                        LOGGER.log(Level.WARNING, "Error getting file length", e);
                                    }
                                    break;
                                }

                                case TFTPMessage.WRQ: {
                                    fileSize = Long.parseLong(entry.getValue());
                                    if (tSizeMax != -1 && fileSize > tSizeMax) {
                                        LOGGER.warning("File to upload exceeds the maximum size allowed");
                                        ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.DISK_FULL_OR_ALLOCATION_EXCEEDED);
                                        socket.send(errorMsg);
                                        socket.close();
                                        return;
                                    }
                                    break;
                                }
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid transfer size format: " + e.getMessage());
                            iterator.remove();
                        }
                        break;
                    }

                    case "interval": {
                        try {
                            int interval = Integer.parseInt(entry.getValue());
                            if (interval >= TFTPConstants.MIN_TIMEOUT_INTERVAL && interval <= TFTPConstants.MAX_TIMEOUT_INTERVAL) {
                                this.socket.setTimeout(interval * 1000);
                            } else {
                                LOGGER.warning("Received timeout interval option is out of accepted range");
                                ErrorMessage errorMsg = new ErrorMessage(ErrorMessage.ILLEGAL_TFTP_OPERATION);
                                socket.send(errorMsg);
                                socket.close();
                                return;
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid timeout interval format: " + e.getMessage());
                            iterator.remove();
                        }
                        break;
                    }

                    default:
                        // Option not supported
                        iterator.remove();
                }
            }

            if (options.size() != 0) {
                OptionAcknowledgeMessage msgOAck = new OptionAcknowledgeMessage(options);
                socket.send(msgOAck);

                return;
            } else {
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
        } finally {
            // Wait for socket to complete processing before closing file
            if (socketThread != null) {
                try {
                    socketThread.join();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Interrupted waiting for socket thread to close", e);
                }
            }

            // Ensure file is always closed
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing file", e);
                }
            }
        }
    }

    boolean isInitialized() {
        return initialized;
    }

    /**
     * Validates and normalizes a file path to prevent directory traversal attacks.
     * Ensures the resolved file is within the allowed directory.
     *
     * @param baseDir the base directory (server root)
     * @param fileName the requested file name
     * @return the normalized absolute file path
     * @throws FileNotFoundException if the path attempts to escape the base directory
     */
    private String validateAndNormalizePath(String baseDir, String fileName) throws FileNotFoundException {
        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(fileName).normalize();

            // Ensure the resolved path is within the base directory
            if (!filePath.startsWith(basePath)) {
                throw new FileNotFoundException("Access denied: path traversal attempt detected");
            }

            return filePath.toString();
        } catch (Exception e) {
            throw new FileNotFoundException("Invalid file path: " + e.getMessage());
        }
    }
}
