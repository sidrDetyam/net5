package ru.nsu.gemuev;

import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ServerHandler implements Handler {
    @Getter
    private final SocketChannel serverChannel;

    private final ByteBuffer clientInputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);
    private final ByteBuffer clientOutputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);

    public ServerHandler(ClientHandler clientHandler){

    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public void read() {

    }

    @Override
    public void write() {

    }

    @Override
    public void connect() {

    }

    @Override
    public void accept() {

    }
}
