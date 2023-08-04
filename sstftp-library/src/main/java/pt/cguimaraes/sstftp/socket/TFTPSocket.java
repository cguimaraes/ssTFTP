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

import pt.cguimaraes.sstftp.message.AcknowledgeMessage;
import pt.cguimaraes.sstftp.message.DataMessage;
import pt.cguimaraes.sstftp.message.ErrorMessage;
import pt.cguimaraes.sstftp.message.OptionAcknowledgeMessage;
import pt.cguimaraes.sstftp.message.ReadRequestMessage;
import pt.cguimaraes.sstftp.message.TFTPMessage;
import pt.cguimaraes.sstftp.message.WriteRequestMessage;

public class TFTPSocket {

    // Use default MTU of 1500
    final static int MTU = 1500;

    private InetAddress ipAddress;
    private int port;

    // Socket variables
    private DatagramSocket socket;
    private boolean running;

    // External handler
    private Object externalClass;
    private Method externalHandler;

    public TFTPSocket(Object externalClass, Method externalHandler) throws SocketException {
        this.socket = new DatagramSocket(null);

        this.externalClass = externalClass;
        this.externalHandler = externalHandler;
    }

    public TFTPSocket(InetAddress ipAddress, int port, Object externalClass, Method externalHandler)
            throws SocketException {
        this.socket = new DatagramSocket();

        this.ipAddress = ipAddress;
        this.port = port;

        this.externalClass = externalClass;
        this.externalHandler = externalHandler;
    }

    public void bind(InetAddress ipAddress, int port) throws SocketException {
        socket.bind(new InetSocketAddress(ipAddress, port));
    }

    public void send(TFTPMessage msg) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        msg.toBytes(stream);

        byte[] socketData = new byte[MTU];
        DatagramPacket socketPacket = new DatagramPacket(socketData, socketData.length, ipAddress, port);
        socketPacket.setData(stream.toByteArray());
        try {
            socket.send(socketPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Start TFTP socket
    public void run() {
        byte[] recvData = new byte[MTU];
        DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length, ipAddress, port);

        running = true;
        while (running) {
            try {
                socket.receive(recvPacket);
                port = recvPacket.getPort();
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
                    break;
                }

                case TFTPMessage.ACK: {
                    AcknowledgeMessage msgAck = new AcknowledgeMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgAck.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgAck;
                    break;
                }

                case TFTPMessage.ERROR: {
                    ErrorMessage msgError = new ErrorMessage(recvPacket.getAddress(), recvPacket.getPort());
                    msgError.fromBytes(new ByteArrayInputStream(recvPacket.getData(), 0,
                            recvPacket.getLength()));
                    msg = msgError;
                    break;
                }

                case TFTPMessage.OACK: {
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
        socket.close();
        running = false;
    }
}
