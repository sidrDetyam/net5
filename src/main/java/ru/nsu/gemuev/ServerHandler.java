package ru.nsu.gemuev;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

@Log4j2
public class ServerHandler implements Handler {
    private final SocketChannel serverChannel;
    private final ClientHandler clientHandler;
    private final ByteBuffer clientInputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);
    private final ByteBuffer clientOutputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);

    public ServerHandler(@NonNull SelectionKey serverKey, @NonNull ClientHandler clientHandler){
        serverChannel = (SocketChannel) serverKey.channel();
        try {
            serverChannel.configureBlocking(false);
        }
        catch (IOException e){
            log.error(e);
            close();
        }
        this.clientHandler = clientHandler;
        serverKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE);
    }

    @Override
    public void close(){
        try {
            clientHandler.close();
            serverChannel.close();
        } catch (IOException e) {
            log.error(e);
        }
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

    private void readToBuffer(){
        try {

        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }

    public void readFromBuffer(@NonNull ByteBuffer to){

    }

    public void writeToBuffer(@NonNull ByteBuffer from){

    }

    public void writeFromBuffer(){

    }
}
