package ru.nsu.gemuev.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

@RequiredArgsConstructor
@Log4j2
public class AcceptHandler implements Handler{

    private final ServerSocketChannel serverSocketChannel;
    private final DnsHandler dnsHandler;
    private final Selector selector;

    @Override
    public void accept() {
        try {
            SocketChannel channel = serverSocketChannel.accept();
            channel.configureBlocking(false);
            SelectionKey key = channel.register(selector, 0);
            key.attach(new ClientHandler(key, dnsHandler));
        }
        catch (IOException e){
            log.error(e);
        }
    }

    @Override
    public void close() {
        try {
            serverSocketChannel.close();
        }
        catch (IOException e){
            log.error(e);
        }
    }

    @Override
    public void read() {
        throw new UnsupportedOperationException("bruh");
    }

    @Override
    public void write() {
        throw new UnsupportedOperationException("bruh");
    }

    @Override
    public void connect() {
        throw new UnsupportedOperationException("bruh");
    }
}
