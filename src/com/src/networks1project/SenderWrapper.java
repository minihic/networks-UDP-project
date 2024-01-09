package com.src.networks1project;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

public class SenderWrapper {
    private AtomicReference<TransmitState> transmitStatus = new AtomicReference<>(TransmitState.WAIT);
    private int packetSize;
    private int finalEOLFrame;

    private enum TransmitState {
        WAIT, TRANSMIT, LAST, DONE;
    }

    public TransmitState getTransmitStatus() {
        return transmitStatus.get();
    }

    public SenderWrapper(int packetSize) {
        this.packetSize = packetSize;
    }

    public void sendPacket(DatagramSocket serverSocket, SocketAddress clienSocketAddress, int clientID, int windowSize, String file)
            throws IOException, NoSuchAlgorithmException {
        int sequenceBase = 0;
        int sequenceMax = windowSize;
        serverSocket.setSoTimeout(10);
        ByteBuffer message = readFileToByteBuffer(file);

        // Creates the initial frame buffer
        LinkedList<ByteBuffer> frameBuffer = new LinkedList<>();
        for (int i = 0; i < windowSize; i++) {
            frameBuffer.addLast(createNewFrame(i, 0, packetSize, message));
        }

        System.out.println(Server.SERVER_PREFIX + "window size " + windowSize);
        System.out.println(Server.SERVER_PREFIX + "Frame buffer size: " + frameBuffer.size());
        System.out.println(Server.SERVER_PREFIX + "Frame buffer: " + frameBuffer);
        System.out.println("");

        while (transmitStatus.get() != TransmitState.DONE) {
            try {
                int j = 0;
                while (j < windowSize) {
                    ByteBuffer sendBuffer = frameBuffer.get(j);
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer.array(), sendBuffer.array().length,
                            clienSocketAddress);
                    serverSocket.send(sendPacket);
                    sendBuffer.clear();
                    CommandInputHandler.incrementPacketsSend();
                    j++;
                }
            } catch (Exception e) {
                System.out.println(Server.SERVER_PREFIX + clientID + " Error : " + e.getMessage());
            }

            try {
                // Waits for ACK packet from client
                byte[] serverRequestMessage = new byte[4];
                DatagramPacket serverRequestPacket = new DatagramPacket(serverRequestMessage,
                        serverRequestMessage.length, clienSocketAddress);
                serverSocket.receive(serverRequestPacket);
                ByteBuffer serverRequestBuffer = ByteBuffer.wrap(serverRequestMessage);
                int serverRequestNumber = serverRequestBuffer.getInt();
                serverRequestBuffer.clear();

                // Checks if the ACK is the last frame
                if ((serverRequestNumber == finalEOLFrame) && (transmitStatus.get() == TransmitState.LAST)) {
                    transmitStatus.set(TransmitState.DONE);
                    System.out.println(Server.SERVER_PREFIX + clientID + " received EOL acknowledgement " + serverRequestNumber);
                    System.out.println(Server.SERVER_PREFIX + clientID + " " + getTransmitStatus());
                    break;
                }

                // Otherwise iterates to next request
                System.out.println(Server.SERVER_PREFIX + clientID + " received acknowledgement " + serverRequestNumber);
                serverRequestNumber++;

                // Iterates the window frame if ACK is received
                if ((serverRequestNumber > sequenceBase) && (TransmitState.LAST != transmitStatus.get())) {
                    sequenceMax = (sequenceMax - sequenceBase) + serverRequestNumber;
                    sequenceBase = serverRequestNumber;
                    frameBuffer.removeFirst();
                    frameBuffer.addLast(createNewFrame(sequenceMax - 1, 0, packetSize, message));
                }
            } catch (IOException e) {
                System.out.println(Server.SERVER_PREFIX + clientID + " Error : " + e.getMessage());
            }
        }
    }

    private ByteBuffer createNewFrame(int sequenceNumber, int flag, int packetSize, ByteBuffer message)
            throws NoSuchAlgorithmException {
        finalEOLFrame = 999999999;
        int messageSize = packetSize - 40;
        ByteBuffer frame = ByteBuffer.allocate(packetSize);
        byte[] sendMessage = new byte[messageSize];
        int emptyBytes = 0;

        if (message.remaining() < sendMessage.length) {
            emptyBytes = messageSize - message.remaining();
            message.get(sendMessage, 0, message.remaining());
            flag = emptyBytes;
            finalEOLFrame = sequenceNumber;
            transmitStatus.set(TransmitState.LAST);
            System.out.println(Server.SERVER_PREFIX + "End of file " + finalEOLFrame);
        } else {
            message.get(sendMessage, 0, messageSize);
        }

        byte[] checksum = Utils.hashByteBuffer(sendMessage);
        ByteBuffer sendChecksum = ByteBuffer.wrap(checksum);

        frame.putInt(sequenceNumber);
        frame.putInt(flag);
        frame.put(sendChecksum.array());
        frame.put(sendMessage);

        return frame;
    }

    private ByteBuffer readFileToByteBuffer(String filePath) throws IOException {
        File file = new File(filePath);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            FileChannel fileChannel = fileInputStream.getChannel();
            long fileSize = fileChannel.size();
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            int bytesRead = fileChannel.read(buffer);
            buffer.flip();
            System.out.println("Bytes read: " + bytesRead);
            return buffer;
        } catch (IOException e) {
            throw new IOException("Error reading file: " + e.getMessage());
        }
    }
}