package com.src.networks1project;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class Server implements Runnable {
    public static final String SERVER_PREFIX = "Server ";

    private static AtomicReference<CurrentState> serverStatus = new AtomicReference<>(CurrentState.OFF);
    private static ThreadGroup serverThreadGroup = new ThreadGroup("ServerThreadGroup");
    private DatagramSocket socket;
    private int port;
    private String file;
    private int expectedClientCount;
    
    HashMap<Integer, SocketAddress> clientMap = new HashMap<>();

    private enum CurrentState {
        OFF,
        READY,
        REGISTRATION,
        WAIT,
        TRANSMIT,
        DONE;
    }

    public static CurrentState getServerStatus() {
        return serverStatus.get();
    }

    public Server(int port, String file, int expectedClientCount) {
        this.port = port;
        this.file = file;
        this.expectedClientCount = expectedClientCount;
        new Thread(serverThreadGroup, this).start();
        System.out.println(SERVER_PREFIX + serverThreadGroup + " ready");
    }

    public static void stop() {
        serverStatus.set(CurrentState.OFF);
        serverThreadGroup.interrupt();
        System.out.println(SERVER_PREFIX + serverThreadGroup + " stopping");
    }

    public void run() {
        serverStatus.set(CurrentState.READY);
        System.out.println(SERVER_PREFIX + "Expected Clients: " + expectedClientCount);

        try {
            socket = new LossDatagramSocket(port);
            System.out.println(SERVER_PREFIX + "Address: " + socket.getLocalSocketAddress());

            registration(expectedClientCount);

            // Execute Socket transmit
            if (serverStatus.get() == CurrentState.WAIT) {
                transmit();
            }

            // report on the number of packets sent and received
            System.out.println("");
            System.out.println(SERVER_PREFIX + "Packets sent: " + CommandInputHandler.getPacketsSent());
            System.out.println(SERVER_PREFIX + "Packets received: " + CommandInputHandler.getPacketsReceived());
            System.out.println(SERVER_PREFIX + "Packet Retransmissions: " + (CommandInputHandler.getPacketsSent() - CommandInputHandler.getPacketsReceived()));

        } catch (SocketException ex) {
            System.out.println(SERVER_PREFIX + "Socket error: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println(SERVER_PREFIX + "I/O error: " + ex.getMessage());
        } catch (InterruptedException ei) {
            System.out.println(SERVER_PREFIX + "Interrupt Error" + ei.getMessage());
        } finally {
            socket.close();
        }
    }

    // Function that collects all clients then returns a ready
    private void registration(int expectedClientCount) throws IOException, InterruptedException {
        serverStatus.set(CurrentState.REGISTRATION);
        System.out.println(SERVER_PREFIX + getServerStatus());
        while (serverStatus.get() == CurrentState.REGISTRATION) {
            // Waits for client registration
            byte[] clientIDBuffer = new byte[4];
            DatagramPacket clientRegistration = new DatagramPacket(clientIDBuffer, clientIDBuffer.length);
            socket.receive(clientRegistration);
            SocketAddress clientAddress = clientRegistration.getSocketAddress();

            // Gets client ID from client request and add it to clientMap
            int clientID = ByteBuffer.wrap(clientIDBuffer).getInt();
            clientMap.put(clientID, clientAddress);

            // Sends acknowledgement to client
            byte[] acknowledgementBuffer = new byte[1];
            DatagramPacket acknowledgementPacket = new DatagramPacket(acknowledgementBuffer,
                    acknowledgementBuffer.length, clientAddress);
            socket.send(acknowledgementPacket);

            System.out.println(SERVER_PREFIX + "Client " + clientID + clientAddress + " registered");
            if (expectedClientCount == clientMap.size()) {
                break;
            }
        }
        Thread.sleep(100);
        System.out.println(SERVER_PREFIX + "All clients registered");
        serverStatus.set(CurrentState.WAIT);
    }

    private void transmit() {
        serverStatus.set(CurrentState.TRANSMIT);
        System.out.println(SERVER_PREFIX + getServerStatus());
        while (serverStatus.get() == CurrentState.TRANSMIT) {
            clientMap.forEach((clientID, clientAddress) -> {
                try {
                    SenderWrapper transmitHandler = new SenderWrapper(CommandInputHandler.getPacketSize());
                    transmitHandler.sendPacket(socket, clientAddress, clientID, 5, file);
                } catch (NoSuchAlgorithmException | IOException e) {
                    e.printStackTrace();
                }
            });
            serverStatus.set(CurrentState.DONE);
        }
    }
}