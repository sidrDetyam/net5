package ru.nsu.gemuev;

public class Main {
    public static void main(String[] args) {
        if(args.length != 1){
            throw new IllegalArgumentException("Wrong count of args");
        }
        final int port = Integer.parseInt(args[0]);
        Proxy proxy = new Proxy(port);
        proxy.run();
    }
}
