package com.src.networks1project;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class LossDatagramSocket extends DatagramSocket {

    public LossDatagramSocket(int port) throws SocketException {
        super(port);
    }


    @Override
    public void send(DatagramPacket p) throws IOException {
        if ((Math.random() < CommandInputHandler.getProbability()) && (p.getLength() >= CommandInputHandler.getPacketSize())) {
            //fills the packet with empty bytes
            byte[] corruptedData = new byte[p.getLength()];
            p.setData(corruptedData);
        }
        super.send(p);
    }
}
