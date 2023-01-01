package ru.nsu.gemuev.handlers;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

@Log4j2
@Getter
public abstract class AbstractCouplingHandler implements Handler{

    protected AbstractCouplingHandler partner = null;
    protected final ByteBuffer buffer = ByteBuffer.allocate(Socks5Constants.DEF_BUF_SIZE);
    protected boolean isEndOfInput = false;
    protected final SelectionKey key;
    protected final SocketChannel channel;

    public AbstractCouplingHandler(@NonNull SelectionKey key){
        this.key = key;
        key.interestOps(0);
        channel = (SocketChannel) key.channel();
    }

    public void setUpOptions(int opts, boolean isEnable){
        if(isEnable){
            key.interestOpsOr(opts);
        }
        else{
            key.interestOpsAnd(~opts);
        }
    }

    @Override
    public void connect() {
        try {
            channel.finishConnect();
        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }


    @Override
    public void accept() {
        throw new UnsupportedOperationException("Accept not supported by this handler");
    }

    public void readToBuffer(){
        if(buffer.hasRemaining()){
            setUpOptions(OP_READ, false);
            partner.setUpOptions(OP_WRITE, true);
            return;
        }

        try {
            buffer.clear();
            int read_ = channel.read(buffer);
            buffer.flip();
            if(read_ == -1){
                isEndOfInput = true;
                setUpOptions(OP_READ, false);
                partner.setUpOptions(OP_WRITE, false);
            }
            else{
                setUpOptions(OP_READ, read_ == 0);
                partner.setUpOptions(OP_WRITE, read_ != 0);
            }

            if(isEndOfInput() && partner.isEndOfInput()){
                close();
            }
        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }

    public void write2Channel() {
        ByteBuffer partnerBuffer = partner.getBuffer();
        try {
            if (partnerBuffer.hasRemaining()) {
                channel.write(partnerBuffer);
            }
            if (!partnerBuffer.hasRemaining()) {
                partner.setUpOptions(OP_READ, true);
                setUpOptions(OP_WRITE, false);
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }
}
