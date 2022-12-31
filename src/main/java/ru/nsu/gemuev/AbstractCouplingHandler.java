package ru.nsu.gemuev;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

@Log4j2
@Getter
public abstract class AbstractCouplingHandler implements Handler{

    protected AbstractCouplingHandler partner = null;
    protected final ByteBuffer buffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);
    protected boolean isShutdownInput = false;
    protected boolean isShutdownOutput = false;
    protected final SelectionKey key;
    protected final SocketChannel channel;

    public AbstractCouplingHandler(@NonNull SelectionKey key){
        this.key = key;
        key.interestOps(0);
        channel = (SocketChannel) key.channel();
    }

    public void setInputEvent(boolean isInput){
        if(isInput) {
            SelectionKeyUtils.turnOnReadOption(key);
        }
        else{
            SelectionKeyUtils.turnOffReadOption(key);
        }
    }

    public void setOutputEvent(boolean isOutput){
        if(isOutput) {
            SelectionKeyUtils.turnOnWriteOption(key);
        }
        else{
            SelectionKeyUtils.turnOffWriteOption(key);
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
        throw new UnsupportedOperationException("bruh");
    }

    public void readToBuffer(){
        if(buffer.hasRemaining() || isShutdownInput()){
            setInputEvent(false);
            partner.setOutputEvent(true);
            return;
        }

        try {
            buffer.clear();
            int read_ = channel.read(buffer);
            buffer.flip();
            if(read_ == -1){
                isShutdownInput = true;
                channel.shutdownInput();
            }
            partner.setOutputEvent(true);
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
                if (!partnerBuffer.hasRemaining()) {
                    partner.setInputEvent(true);
                    setOutputEvent(false);
                }
            } else {
                if (partner.isShutdownInput()) {
                    channel.shutdownOutput();
                    setOutputEvent(false);
                    partner.setInputEvent(false);
                }
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }
}