package com.src.networks1project;

public class Main {

    public static void main(String[] args) {

        Thread commandInput = new Thread(new CommandInputHandler());
        commandInput.setName("CommandInputHandler");

        commandInput.start();
    }
}
