package com.src.networks1project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandInputHandler implements Runnable {
    private String toolName;
    private int idProcess;
    private int numProcesses;
    private String fileName;
    private static Double probability;
    private String protocol;
    private static int windowSize;
    private int serverPort;
    private static int packetSize = 4096;
    private static AtomicInteger packetsReceived = new AtomicInteger(0);
    private static AtomicInteger packetsSent = new AtomicInteger(0);


    // Thread Pool executor init
    ExecutorService executor = Executors.newFixedThreadPool(5);

    public static int getWindowSize() {
        return windowSize;
    }

    public static int getPacketSize() {
        return packetSize;
    }

    public static double getProbability() {
        return probability;
    }

    public static void incrementPacketsReceived() {
        packetsReceived.incrementAndGet();
    }

    public static void incrementPacketsSend() {
        packetsSent.incrementAndGet();
    }

    public static int getPacketsReceived() {
        return packetsReceived.get();
    }

    public static int getPacketsSent() {
        return packetsSent.get();
    }

    public void run() {
        // Setup default values
        idProcess = generateRandomID();
        numProcesses = 4;
        probability = 0.5;
        protocol = "GobackN";
        windowSize = 5;
        serverPort = 5362;

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println(" ");
        System.out.println("Welcome to UDP client server tool!");
        System.out.println("Enter input: type 'help' for help or type 'exit' to quit.");
        System.out.println(" ");

        try {
            String userInput;
            while (true) {
                userInput = reader.readLine();
                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }
                handleInput(userInput);
            }
        } catch (IOException e) {
            System.out.println(" ");
            System.out.println("Error reading input. Exiting.");
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                System.out.println(" ");
                System.out.println("Error closing BufferedReader");
            }
        }
    }

    private void handleInput(String input) {

        // Puts the input into String Array
        String[] parts = input.split("\\s+");

        try {
            for (int i = 0; i < parts.length; i++) {
                switch (parts[i]) {
                    case "-t":
                        toolName = parts[++i];
                        break;
                    case "-i":
                        idProcess = Integer.parseInt(parts[++i]);
                        break;
                    case "-n":
                        numProcesses = Integer.parseInt(parts[++i]);
                        break;
                    case "-f":
                        fileName = parts[++i];
                        break;
                    case "-p":
                        probability = Double.parseDouble(parts[++i]);
                        break;
                    case "-r":
                        protocol = parts[++i];
                        break;
                    case "-w":
                        windowSize = Integer.parseInt(parts[++i]);
                        break;
                    case "--port":
                        serverPort = Integer.parseInt(parts[++i]);
                        break;
                    case "--packet-size":
                        packetSize = Integer.parseInt(parts[++i]);
                        break;
                    case "help":
                        displayHelp();
                        break;
                    case "display":
                        displayCurrentArguments();
                        break;
                    case "start-server":
                        new Server(serverPort, "tinytest.bin", numProcesses);
                        break;
                    case "stop-server":
                        Server.stop();
                        break;
                    case "start-clients":
                        startClients(numProcesses);
                        break;
                    case "stop-clients":
                        executor.shutdown();
                        System.out.println("Command: All Clients stopped");
                        break;
                    case "status":
                        System.out.println("Command: Server Status = " + Server.getServerStatus());
                        break;
                    default:
                        System.out.println(" ");
                        System.out.println("Invalid argument: " + parts[i]);
                        System.out.println(" ");
                        return;
                }
            }
            System.out.println(" ");

        } catch (NumberFormatException e) {
            System.out.println(" ");
            System.out.println("Error parsing input. Please enter valid input values.");
            System.out.println(" ");
        }
    }

    private void startClients(int numProcesses) {
        for (int i = 0; i < numProcesses; i++) {
            Runnable clientWorker = new Client(i + 100, serverPort);
            executor.execute(clientWorker);
        }
        System.out.println("Command: All Clients started");
    }

    private static Random random = new Random();

    private static int generateRandomID() {
        return random.nextInt(900) + 100;
    }

    private void displayCurrentArguments() {
        System.out.println(" ");
        System.out.println("Tool Name: " + toolName);
        System.out.println("ID Process: " + idProcess);
        System.out.println("Number of Processes: " + numProcesses);
        System.out.println("File Name: " + fileName);
        System.out.println("Probability: " + probability);
        System.out.println("Protocol: " + protocol);
        System.out.println("Window Size: " + windowSize);
    }

    private void displayHelp() {
        System.out.println(" ");
        System.out.println("Usage: java CommandLineInputHandler [options]");
        System.out.println("Options:");
        System.out.println("help               Display help information");
        System.out.println("display            Display current arguments");
        System.out.println("-t <toolname>      Tool name");
        System.out.println("-i <idprocess>     ID process");
        System.out.println("-n <numprocesses>  Number of processes (default: 10)");
        System.out.println("-f <filename>      File name");
        System.out.println("-p <probability>   Probability (default: 0.5)");
        System.out.println("-r <protocol>      Protocol (default: GoBackN)");
        System.out.println("-w <windowsize>    Window size (default: 10)");
    }

}
