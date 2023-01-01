package ru.nsu.gemuev.handlers;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.nio.channels.SelectionKey;

@Log4j2
public class ServerHandler extends AbstractCouplingHandler {

    public ServerHandler(@NonNull SelectionKey serverKey, @NonNull ClientHandler clientHandler) {
        super(serverKey);
        partner = clientHandler;
        buffer.flip();
        serverKey.interestOps(SelectionKey.OP_READ);
    }

    @Override
    public void close() {
        partner.close();
    }

    @Override
    public void read() {
        readToBuffer();
    }

    @Override
    public void write() {
        write2Channel();
    }
}
