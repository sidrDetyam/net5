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
    @Getter
    private final SocketChannel serverChannel;
    private final ClientHandler clientHandler;
    @Getter
    private ByteBuffer inputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);
    @Getter
    private ByteBuffer outputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);

    public ServerHandler(@NonNull SelectionKey serverKey, @NonNull ClientHandler clientHandler) {
        serverChannel = (SocketChannel) serverKey.channel();
        try {
            serverChannel.configureBlocking(false);
        } catch (IOException e) {
            log.error(e);
            close();
        }
        this.clientHandler = clientHandler;
        serverKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE);
    }

    @Override
    public void close() {
        clientHandler.close();
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

    public void setInputEvent(boolean isInput) {
        if (isInput) {
            SelectionKeyUtils.turnOnReadOption(clientKey);
        } else {
            SelectionKeyUtils.turnOffReadOption(clientKey);
        }
    }

    public void setOutputEvent(boolean isOutput) {
        if (isOutput) {
            SelectionKeyUtils.turnOnWriteOption(clientKey);
        } else {
            SelectionKeyUtils.turnOffWriteOption(clientKey);
        }
    }


    private void write2server() {
        ByteBuffer clientBuffer = clientHandler.getInputBuffer();
        try {
            if (clientBuffer.hasRemaining()) {
                serverChannel.write(clientBuffer);
                if (!clientBuffer.hasRemaining()) {
                    clientHandler.setInputEvent(true);
                    setOutputEvent(false);
                }
            } else {
                if (clientHandler.isShutdownInput()) {
                    serverChannel.shutdownOutput();
                    setOutputEvent(false);
                    clientHandler.setInputEvent(false);
                }
            }
        } catch (IOException e) {
            log.error(e);
            clientHandler.close();
        }
    }


    private void readToBuffer() {
        if(inputBuffer.hasRemaining() || isShutdownInput()){
            setInputEvent(false);
            serverHandler.setOutputEvent(true);
            return;
        }

        try {
            inputBuffer.clear();
            int read_ = clientChannel.read(inputBuffer);
            inputBuffer.flip();
            if(read_ == -1){
                isShutdownInput = true;
                clientChannel.shutdownInput();
            }
            serverHandler.setOutputEvent(true);
        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }

    public void writeFromBuffer() {

    }
}
