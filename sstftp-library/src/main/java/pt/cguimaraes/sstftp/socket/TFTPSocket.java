// =============================================================================
// Brief     : TFTP Socket
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ssTFTP. If not, write to the Free Software Foundation,
// Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
// =============================================================================

package pt.cguimaraes.sstftp.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import pt.cguimaraes.sstftp.TFTPConstants;
import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.OptionAcknowledgeMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;

/**
 * TFTP Socket implementation for sending and receiving TFTP messages.
 *
 * This class handles the low-level UDP socket communication for TFTP protocol.
 * It manages message serialization/deserialization, automatic retransmission,
 * and timeout handling. Messages are passed to an external handler via reflection.
 *
 * Thread Safety: This class uses volatile fields for thread-safe access to
 * shared state between the socket receive thread and external callers.
 *
 * @author Carlos Guimarães
 * @version 0.2
 */
public class TFTPSocket implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TFTPSocket.class.getName());

    // Use default MTU of 1500
    final static int MTU = TFTPConstants.DEFAULT_MTU;

    // Maximum retries
    private int retries = TFTPConstants.DEFAULT_RETRIES;

    // Socket timeout
    private int timeout = TFTPConstants.DEFAULT_TIMEOUT_MS;

    // Socket variables
    private DatagramSocket socket;
    private DatagramPacket socketPacket;
    private boolean running;

    // External handler - use WeakReference to prevent memory leak
    private WeakReference<Object> externalClass;
    private Method externalHandler;

    // There is only one message pending, so it is fine to set a single timer
    private volatile Timer timer;

    // Last block received
    private volatile int lastBlock;

    // Last acknowledge received
    private volatile int lastAck;

    /**
     * Creates a TFTP socket without binding to a specific address/port.
     * The socket will bind when a packet is first sent/received.
     *
     * @param externalClass the object containing the handler method
     * @param externalHandler the method to invoke when messages are received
     * @throws SocketException if the socket cannot be created
     * @throws NullPointerException if externalClass or externalHandler is null
     */
    public TFTPSocket(Object externalClass, Method externalHandler) throws SocketException {
        this.socket = new DatagramSocket(null);

        byte[] socketData = new byte[MTU];
        this.socketPacket = new DatagramPacket(socketData, socketData.length);

        this.externalClass = new WeakReference<>(Objects.requireNonNull(externalClass, "externalClass cannot be null"));
        this.externalHandler = Objects.requireNonNull(externalHandler, "externalHandler cannot be null");
    }

    public TFTPSocket(InetAddress ipAddress, int port, Object externalClass, Method externalHandler)
            throws SocketException {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got: " + port);
        }
        if (externalClass == null) {
            throw new NullPointerException("externalClass cannot be null");
        }
        if (externalHandler == null) {
            throw new NullPointerException("externalHandler cannot be null");
        }

        this.socket = new DatagramSocket();
        try {
            this.socket.setSoTimeout(5000);  // 5 second timeout to allow graceful shutdown
        } catch (SocketException e) {
            this.socket.close();
            throw e;
        }

        byte[] socketData = new byte[MTU];
        this.socketPacket = new DatagramPacket(socketData, socketData.length, ipAddress, port);

        this.externalClass = new WeakReference<>(externalClass);
        this.externalHandler = externalHandler;
    }

    /**
     * Binds the socket to a specific address and port.
     *
     * @param ipAddress the IP address to bind to
     * @param port the port number to bind to
     * @throws SocketException if the socket cannot be bound
     */
    /**
     * Binds the socket to a specific address and port.
     *
     * @param ipAddress the IP address to bind to
     * @param port the port number to bind to
     * @throws SocketException if the socket cannot be bound
     */
    public void bind(InetAddress ipAddress, int port) throws SocketException {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got: " + port);
        }
        try {
            socket.bind(new InetSocketAddress(ipAddress, port));
            socket.setSoTimeout(5000);  // Set timeout after binding
        } catch (SocketException e) {
            LOGGER.log(Level.SEVERE, "Failed to bind socket to " + ipAddress + ":" + port, e);
            throw e;
        }
    }

    /**
     * Sends a TFTP message with automatic retransmission.
     * For DATA and ACK messages, a timer is started to retransmit if no response is received.
     * For ERROR messages, no retransmission is attempted.
     *
     * @param msg the TFTP message to send
     * @throws NullPointerException if msg is null
     */
    public void send(TFTPMessage msg) {
        Objects.requireNonNull(msg, "msg cannot be null");
        boolean enableRetransmission = true;

        // If message to send is an Data or Acknowledge message
        // update last block or last acknowledge sent respectively
        switch (msg.getOpcode()) {
            case TFTPMessage.DATA: {
                DataMessage msgData = (DataMessage) msg;
                lastBlock = msgData.getBlockNumber();
                break;
            }

            case TFTPMessage.ACK: {
                AcknowledgeMessage msgAck = (AcknowledgeMessage) msg;
                lastAck = msgAck.getBlockNumber();
                break;
            }

            case TFTPMessage.ERROR: {
                enableRetransmission = false;
                break;
            }

            default: {
                // Do nothing
                break;
            }
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        msg.toBytes(stream);

        socketPacket.setData(stream.toByteArray());
        try {
            socket.send(socketPacket);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending TFTP message", e);
        }

        // Start timer for retransmissions
        if (enableRetransmission == true) {
            synchronized (this) {
                // Cancel any existing timer before creating new one
                if (this.timer != null) {
                    this.timer.cancel();
                    this.timer.purge();
                }
                this.timer = new Timer("TFTP-Retransmit-" + System.nanoTime(), true);  // daemon thread
                this.timer.schedule(new TimerTask() {
                    int i = 0;

                    public void run() {
                        if (i < retries) {
                            ++i;
                            try {
                                socket.send(socketPacket);
                            } catch (IOException e) {
                                LOGGER.log(Level.SEVERE, "Error retransmitting TFTP message", e);
                            }
                        }
                    }
                }, timeout, timeout);
            }
        }
    }

    /**
     * Starts the socket receiver thread. This method runs the main receive loop
     * that listens for incoming TFTP messages and dispatches them to the handler.
     *
     * This method blocks until the socket is closed or an error occurs.
     * Should be run in a separate thread.
     */
    public void run() {
        byte[] recvData = new byte[MTU];
        DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);

        running = true;
        try {
            while (running) {
                try {
                    socket.receive(recvPacket);
                    socketPacket.setPort(recvPacket.getPort());
                } catch (SocketTimeoutException e) {
                    // Timeout is normal; check if still running and continue
                    if (!running) {
                        break;
                    }
                    continue;
                } catch (SocketException e) {
                    if (!running) {
                        // Socket was closed intentionally
                        break;
                    }
                    LOGGER.log(Level.SEVERE, "Socket error during receive", e);
                    break;
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                    LOGGER.log(Level.SEVERE, "IO error during receive", e);
                    break;
                }

                TFTPMessage msg = null;
                int opcode = ((recvPacket.getData()[0] & 0xFF) << 8) | (recvPacket.getData()[1] & 0xFF);
            switch (opcode) {
                case TFTPMessage.RRQ: {
                    ReadRequestMessage msgRRQ = new ReadRequestMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgRRQ.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgRRQ;
                    break;
                }

                case TFTPMessage.WRQ: {
                    WriteRequestMessage msgWRQ = new WriteRequestMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgWRQ.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgWRQ;
                    break;
                }

                case TFTPMessage.DATA: {
                    DataMessage msgData = new DataMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgData.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgData;

                    // If next data block was received cancel timer
                    if (((lastAck + 1) & 0xFFFF) == msgData.getBlockNumber()) {
                        stopTimer(timer);
                    }
                    break;
                }

                case TFTPMessage.ACK: {
                    AcknowledgeMessage msgAck = new AcknowledgeMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgAck.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgAck;

                    // If acknowledge to the current data block was received cancel timer
                    if (lastBlock == msgAck.getBlockNumber()) {
                        stopTimer(timer);
                    }
                    break;
                }

                case TFTPMessage.ERROR: {
                    stopTimer(timer);

                    ErrorMessage msgError = new ErrorMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgError.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgError;
                    break;
                }

                case TFTPMessage.OACK: {
                    stopTimer(timer);

                    OptionAcknowledgeMessage msgOAck = new OptionAcknowledgeMessage(
                            recvPacket.getAddress(), recvPacket.getPort());
                    msgOAck.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgOAck;
                    break;
                }

                default: {
                    return;
                }
            }

            // Send message to external handler
            try {
                Object handler = externalClass.get();
                if (handler != null) {
                    externalHandler.invoke(handler, msg);
                } else {
                    LOGGER.log(Level.WARNING, "External handler has been garbage collected; discarding message");
                }
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                LOGGER.log(Level.SEVERE, "Error invoking message handler", e);
            }
            }
        } finally {
            close();
        }
    }

    /**
     * Closes the socket and stops the receiver thread.
     * Cancels any pending timers.
     */
    public void close() {
        running = false;
        synchronized (this) {
            stopTimer(timer);
            timer = null;
        }

        try {
            socket.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing socket", e);
        }
    }

    /**
     * Gets the number of retries for message transmission.
     *
     * @return the number of retries
     */
    public int getRetries() {
        return retries;
    }

    /**
     * Sets the number of retries for message transmission.
     *
     * @param retries the number of retries (must be positive)
     */
    public void setRetries(int retries) {
        this.retries = retries;
    }

    /**
     * Gets the timeout duration in milliseconds.
     *
     * @return the timeout in milliseconds
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout duration in milliseconds.
     *
     * @param timeout the timeout in milliseconds (must be positive)
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Stops the retransmission timer if it is running.
     *
     * @param timer the timer to stop (can be null)
     */
    private void stopTimer(Timer timer) {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }
}
