# Networks 1 Project

Implementing an efficient many to many communication protocol over UDP

## Sources

- [GeekforGeeks UDP Datagrams](https://www.geeksforgeeks.org/working-udp-datagramsockets-java/)
- [ByteBuffer Explained](https://www.mindprod.com/jgloss/bytebuffer.html)
- [Visual Illustration of GobackN](https://www2.tkn.tu-berlin.de/teaching/rn/animations/gbn_sr/)

## Project Details

- Setup Orchestration of activities (setup client and server)
  - Thread Pool for clients ✓
  - Setup Packet Exchange Format (orchestartion/bootstrapping) ✓
- Create wrapper function ✓
- Extend DatagramPacket send() and receive() with checksum and GoBackN ✓
- Sync for sending frames
- Verify Command Input Parameters
- Add WAIT segement before file transfer
- Add packet loss to DatagramSocket ✓
- Graph and Measure with Wireshark
- Optimisation / Cleaning up / Linting / Reliability
- Extra Features ? (encryption, file checksum)
- Report

##### Client Thread Pool

Implemented inside the Command Input Handler. Client is designed to be threaded so multiple instances can be created.

##### Generate a test file

This powershell command will create a 100MB file in your project directory.

```powershell
$out = new-object byte[] 104857600; (new-object Random).NextBytes($out); [IO.File]::WriteAllBytes('test.bin', $out)
```

##### Handshake Exchange Format

The Server will know from the start how many clients are expected to connect. Once the server is started it will wait for all the expected clients to connect, adding each one to a list. Finally when all clients are connected, the server will transmit the file (for now) round robin style or sequencially. Here S stands for the server and C for a client.

C -> S Registration - introduction of client with client ID and client Address(IP, Port) for futur reference

S -> C Acknowledgement Wait - acknowledgement of client, added to the list and wait for all clients

C         Ready Wait - ready client receive, awaiting file (NOT YET IMPLEMENTED)

S -> C Ready Receive - file transmission

C -> S Done UnRegister - file transfers complete and client thread shutdown

This is a macro view of the program and does not include error checking since it is implemented at every packet.

This organisation allows the transactions to be standard and allows the program to report status of server and clients.

Once all the transmissions are done the clients then server will stop themselves but the command line will stay activated in case the program needs to be restarted.

##### Data Frame Architecture

The main part of the program is the transmission of the main file. Here there need to be checks to ensure that the file is sent correctly and fast.

The file is split up into buffers on the fly and put into a frame buffer about the size of the window.

The server decides a packet size, in our case 4096 bytes

The frame itself has 4 parts:
- Sequence Number/Frame Number (INT, 4 bytes)
- EOL/Ack number (INT, 4 bytes)
- Checksum (Byte[], 32 bytes)
- Message Fragment (Byte[], packetSize - 40 bytes)

These are bundles together and sent to the client. Using GoBackN the client sends back the received frame when it receives it. This is after checking if the checksum is correct by hashing the Message Fragment.

If all is correct the server can transmit the whole file.

## 1. Goal

The goal of this project is to implement an efficient protocol allowing the communication among multiple participants. For our project, you have to create up to 10 clients, where each client is a separate process, that connect to one server that stores a file that will be further downloaded by all clients at the same time.

### 1.1 Program

The tool to initiate the clients will be started as follows from the command line:

`toolname`, `id process`, `number of processes`, `filename`, `probability`, `protocol`, `window size`

- `id process`: is the number of the current process (for instance 2)
- `number of processes`: total number of processes that will join this communication (for instance 10)
- `filename`: name of file to be send to each client that is connected to this server
- `probability`: probability of an UDP send not to be successful - this is to simulate network errors and thus retransmissions.
- `protocol`: Go-back-N over UDP (optional also selective repeat is possible).
- `window size`: the size of the window for Go-back-N

Once started, each process will wait until all the processes have joined the session and then start downloading a file (provided by the filename) from the server.

You have to use only UDP for your program. You will need to provide a wrapper function to the UDP send function that ensures that the success of really sending data is (1-probability). In case failures occur, you will need to ensure that retransmissions are handled correctly. After execution your program should have received correctly the transmitted files from all the processes and report on the display the total number of bytes/packets received, total number of bytes/packets sent and how many retransmissions (received/sent) were done.

## 1.2 Protocol and data transfer

Each client is using Go-back-N with a respective windows size in the range 1-10. Before the next datagram can be downloaded, all (!) clients need to have received the former datagram. The sliding window is advanced only if all clients have received the packet.

Hint: A synchronisation needs to happen between the processes. Please note that the sending should be done to all clients from the same and only server process - you’re not allowed to start a server instance for each client. You have to use the same window scheme (Go-back (n)) for all clients, but for each sent packet maintain individual timers (one timer per client/process).

## 3.2 Report

Your report needs to address the following topics:

1. General architecture covering:

   (a) Bootstrapping: this means sending/receiving starts only when all processes have joined and somehow the IP addresses of them are known to the other processes.

   (b) Details on how you simulated the loss and the protocols (Go-back-n)

   (c) Details on how to verify that the received file has no corrupted content

   (d) Details on how you can achieve savings in bandwidth and better delivery times

   (e) Details on parameters choice for Go-back-N (timeouts, window size, etc)
2. Performance evaluation:

   (a) Graphs/tables showing the impact of different probabilities (0.05, 0.10 , 0.15, 0.20, 0.30 and 0.40 and protocol (timeout, window size) on the total time (delay) and bandwidth.

   (b) These graphs should cover at least 2 scenarios. Scenario 1: two processes download 1 GB files and scenario 2: 10 processes download 10 files in a sequence, et least 50 MB per file.
3. Wireshark screenshots:

   (a) Show statistics only for the communication of the tool. This should include statistics about endpoints (IP adresses, ports), #bytes exchanged, average time for downloading a segment. Hint: You can use display filters for this task.

Important remark: All the material that you use and was not developed by you (code, text, etc.) needs to be quoted and cited correctly in your report.
