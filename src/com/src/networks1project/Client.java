package com.src.networks1project;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Arrays;

public class Client implements Runnable {
    private static Thread worker;
    private static AtomicReference<CurrentState> clientStatus = new AtomicReference<>(CurrentState.OFF);
    private DatagramSocket socket;
    private String filePath = "src/ClientFiles/ClientFile";
    private int serverPort;
    private int clientPort;
    private int clientID;

    private enum CurrentState {
        OFF,
        READY,
        REGISTRATION,
        ACKNOWLEDGEMENT,
        WAIT,
        RECEIVE,
        DONE;
    }

    public static CurrentState getClientStatus() {
        return clientStatus.get();
    }

    public Client(int clientPort, int serverPort) {
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        // Generates a random client ID of the range 100-1000
        Random random = new Random();
        this.clientID = random.nextInt(1000 - 100 + 1) + 100;
        // Worker attaches the current object to a thread
        worker = new Thread(this);
        System.out.println("Client " + clientID + " " + worker + " " + getClientStatus());
    }

    public static void stop() {
        clientStatus.set(CurrentState.OFF);
        System.out.println("Client " + worker + " stopping");
        worker.interrupt();
    }

    private void registration(SocketAddress serverAddress) throws InterruptedException {
        clientStatus.set(CurrentState.REGISTRATION);
        System.out.println("Client " + clientID + " " + getClientStatus());
        try {
            while (clientStatus.get() == CurrentState.REGISTRATION) {
                // Registration - sends client ID to server
                byte[] clientIDBuffer = ByteBuffer.allocate(4).putInt(clientID).array();
                DatagramPacket clientIDPacket = new DatagramPacket(clientIDBuffer, clientIDBuffer.length,
                        serverAddress);
                DatagramPacket acknowledgementWait = new DatagramPacket(new byte[1], 1);
                boolean isAcknowledged = acknowledgementWait.getData()[0] == 1;

                while (!isAcknowledged) {
                    try {
                        socket.send(clientIDPacket);
                        socket.receive(acknowledgementWait);
                        break;
                    } catch (SocketTimeoutException e) {
                    }
                }
                Thread.sleep(10);
                clientStatus.set(CurrentState.ACKNOWLEDGEMENT);
                System.out.println("Client " + clientID + " " + getClientStatus());
            }
        } catch (SocketException | UnknownHostException ex) {
            System.out.println("Client Socket error: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("Client I/O error: " + ex.getMessage());
        }
    }

    private void receive(SocketAddress serverAddress) throws NoSuchAlgorithmException {
        clientStatus.set(CurrentState.RECEIVE);
        System.out.println("Client " + clientID + " " + getClientStatus());
        try {
            while (clientStatus.get() == CurrentState.RECEIVE) {
                // Receive - receives file from server

                int packetSize = CommandInputHandler.getPacketSize();

                AtomicBoolean clientEOL = new AtomicBoolean(false);

                int clientRequestNumber = 0;

                // Prepares the output file
                File outputFile = new File(filePath + clientID);
                if (!outputFile.exists()) {
                    outputFile.getParentFile().mkdirs();
                    boolean created = outputFile.createNewFile();
                    if (created) {
                        System.out.println("Client: File created: " + outputFile.getName());
                    } else {
                        System.out.println("Client: File already exists: " + outputFile.getName());
                    }
                }

                FileOutputStream fileOutputStream = new FileOutputStream(outputFile);

                // Main loop to receive packets
                while (!clientEOL.get()) {
                    try {

                        byte[] receiveMessage = new byte[packetSize];
                        DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
                        socket.receive(receivePacket);

                        if (receivePacket.getLength() != packetSize) {
                            throw new IOException("Packet size is too small");
                        }

                        ByteBuffer receivedBuffer = ByteBuffer.wrap(receiveMessage);

                        int receivedSequenceNumber = receivedBuffer.getInt();
                        int receivedFlagNumber = receivedBuffer.getInt();

                        byte[] receivedChecksum = new byte[32];
                        receivedBuffer.get(receivedChecksum, 0, 32);

                        byte[] receivedDataBuffer = new byte[packetSize - 40];
                        receivedBuffer.get(receivedDataBuffer, 0, packetSize - 40);

                        if (receivedSequenceNumber != clientRequestNumber) {
                            throw new IOException("Sequence mismatch");
                        }

                        // This is to veryfy EOF end of file
                        if (receivedFlagNumber != 0) {
                            System.out.println("Client: End of file with flag " + receivedFlagNumber);
                        }

                        byte[] calculatedChecksum = Utils.hashByteBuffer(receivedDataBuffer);

                        if ((receivedFlagNumber == 0) && Arrays.equals(calculatedChecksum, receivedChecksum)) {
                            fileOutputStream.write(receivedDataBuffer);
                            CommandInputHandler.incrementPacketsReceived();
                            byte[] clientAcknowledgeBuffer = ByteBuffer.allocate(4).putInt(clientRequestNumber).array();
                            DatagramPacket clientAcknowledgePacket = new DatagramPacket(clientAcknowledgeBuffer,
                                    clientAcknowledgeBuffer.length,
                                    serverAddress);
                            socket.send(clientAcknowledgePacket);
                            System.out
                                    .println("Client " + clientID + " sending acknowledgement " + clientRequestNumber);
                            clientRequestNumber++;
                        } else if ((receivedFlagNumber != 0)
                                && Arrays.equals(calculatedChecksum, receivedChecksum)) {
                            // removes the last 0's in the file based on the receivedFlagNumber
                            byte[] lastBytesBuffer = new byte[(packetSize - 40) - receivedFlagNumber];
                            System.arraycopy(receivedDataBuffer, 0, lastBytesBuffer, 0, lastBytesBuffer.length);
                            fileOutputStream.write(lastBytesBuffer);
                            CommandInputHandler.incrementPacketsReceived();
                            fileOutputStream.close();
                            System.out.println(
                                    "Client " + clientID + " sending EOL acknowledgement " + clientRequestNumber);
                            byte[] clientAcknowledgeBuffer = ByteBuffer.allocate(4).putInt(clientRequestNumber).array();
                            DatagramPacket clientAcknowledgePacket = new DatagramPacket(clientAcknowledgeBuffer,
                                    clientAcknowledgeBuffer.length,
                                    serverAddress);
                            socket.send(clientAcknowledgePacket);
                            clientEOL.set(true);
                        } else {
                            throw new IOException("Checksums do not match");
                        }
                    } catch (IOException e) {
                        // System.out.println("Client " + clientID + " Error : " + e.getMessage());
                    }
                }

                clientStatus.set(CurrentState.DONE);
                System.out.println("Client " + clientID + " " + getClientStatus());
            }
        } catch (SocketException | UnknownHostException ex) {
            System.out.println("Client Socket error: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("Client I/O error: " + ex.getMessage());
        }
    }

    public void run() {
        SocketAddress serverAddress;
        clientStatus.set(CurrentState.READY);
        System.out.println("Client " + clientID + " " + getClientStatus());
        try {
            InetAddress serverIP = InetAddress.getByName("localhost");
            serverAddress = new InetSocketAddress(serverIP, serverPort);
            socket = new DatagramSocket(clientPort);
            socket.setSoTimeout(100);
            registration(serverAddress);
            clientStatus.set(CurrentState.WAIT);
            receive(serverAddress);

        } catch (IOException e) {
            System.out.println("Client I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Client Interrupt error: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}
