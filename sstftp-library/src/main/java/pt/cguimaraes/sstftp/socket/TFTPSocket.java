// =============================================================================
// Brief : TFTP Socket
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.OptionAcknowledgeMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;

public class TFTPSocket implements Runnable {

    // Use default MTU of 1500
    final static int MTU = 1500;

    // Maximum retries
    private int retries = 3;

    // Socket timeout
    private int timeout = 2000;

    // Socket variables
    private DatagramSocket socket;
    private DatagramPacket socketPacket;
    private boolean running;

    // External handler
    private Object externalClass;
    private Method externalHandler;

    // There is only one message pending, so it is fine to set a single timer
    private Timer timer;

    // Last block received
    private int lastBlock;

    // Last acknowledge received
    private int lastAck;

    public TFTPSocket(Object externalClass, Method externalHandler) throws SocketException {
        this.socket = new DatagramSocket(null);

        byte[] socketData = new byte[MTU];
        this.socketPacket = new DatagramPacket(socketData, socketData.length);

        this.externalClass = externalClass;
        this.externalHandler = externalHandler;
    }

    public TFTPSocket(InetAddress ipAddress, int port, Object externalClass, Method externalHandler)
            throws SocketException {
        this.socket = new DatagramSocket();

        byte[] socketData = new byte[MTU];
        this.socketPacket = new DatagramPacket(socketData, socketData.length, ipAddress, port);

        this.externalClass = externalClass;
        this.externalHandler = externalHandler;
    }

    public void bind(InetAddress ipAddress, int port) throws SocketException {
        socket.bind(new InetSocketAddress(ipAddress, port));
    }

    public void send(TFTPMessage msg) {
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
            e.printStackTrace();
        }

        // Start timer for retransmissions
        if (enableRetransmission == true) {
            this.timer = new Timer();
            this.timer.schedule(new TimerTask() {
                int i = 0;

                public void run() {
                    if (i < retries) {
                        ++i;
                        try {
                            socket.send(socketPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, timeout, timeout);
        }
    }

    // Start TFTP socket
    public void run() {
        byte[] recvData = new byte[MTU];
        DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);

        running = true;
        while (running) {
            try {
                socket.receive(recvPacket);
                socketPacket.setPort(recvPacket.getPort());
            } catch (SocketException e) {
                e.printStackTrace();
                break;
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }

            TFTPMessage msg = null;
            int opcode = (recvPacket.getData()[0] | recvPacket.getData()[1]);
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
                    if (lastAck + 1 == msgData.getBlockNumber()) {
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
                externalHandler.invoke(externalClass, msg);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        stopTimer(timer);

        socket.close();
        running = false;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    private void stopTimer(Timer timer) {
        timer.cancel();
        timer.purge();
    }
}
