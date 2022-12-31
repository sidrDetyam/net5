package ru.nsu.gemuev;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

@Log4j2
public class ClientHandler implements Handler {

    private final SelectionKey clientKey;
    private final DnsHandler dnsHandler;
    private final SocketChannel clientChannel;
    private final Selector selector;
    private State state;
    private short port;
    private ServerHandler serverHandler = null;
    @Getter
    private boolean isShutdownInput = false;

    @Getter
    private ByteBuffer inputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);
    @Getter
    private ByteBuffer outputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);


    public ClientHandler(@NonNull SelectionKey clientKey, @NonNull DnsHandler dnsHandler) {
        clientChannel = (SocketChannel) clientKey.channel();
        selector = clientKey.selector();
        this.clientKey = clientKey;
        this.dnsHandler = dnsHandler;
        state = State.AWAITING_AUTH_REQUEST;
        clientKey.interestOps(SelectionKey.OP_READ);
    }

    private void constructAuthResponse() {
        outputBuffer.clear();
        outputBuffer.put(Constants.SOCKS5);
        outputBuffer.put(Constants.NO_AUTH);
        outputBuffer.flip();
    }

    private void readAndHandleAuthRequest() {
        if (state != State.AWAITING_AUTH_REQUEST) {
            throw new IllegalStateException("");
        }

        inputBuffer.clear();
        int cnt;
        try {
            cnt = clientChannel.read(inputBuffer);
        } catch (IOException e) {
            log.error(e);
            cnt = -1;
        }
        if (cnt == -1) {
            close();
            return;
        }
        inputBuffer.flip();
        byte socksVersion = inputBuffer.get();
        byte methodAmount = inputBuffer.get();
        byte[] methods = new byte[methodAmount];
        inputBuffer.get(methods);
        if (socksVersion != Constants.SOCKS5 || !ArrayUtils.contains(methods, Constants.NO_AUTH)) {
            close();
            return;
        }

        constructAuthResponse();
        state = State.SENDING_AUTH_RESPONSE;
        clientKey.interestOps(SelectionKey.OP_WRITE);
    }

    private void sendingAuthResponse() {
        if (state != State.SENDING_AUTH_RESPONSE) {
            throw new IllegalStateException("");
        }
        try {
            clientChannel.write(outputBuffer);
            if (!outputBuffer.hasRemaining()) {
                clientKey.interestOps(SelectionKey.OP_READ);
                state = State.AWAITING_CONNECTION_REQUEST;
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }

    public void readAndHandleConnectionRequest() {
        if (state != State.AWAITING_CONNECTION_REQUEST) {
            throw new IllegalStateException("");
        }
        try {
            inputBuffer.clear();
            if (clientChannel.read(inputBuffer) == -1) {
                throw new IOException("unexpected EOF");
            }
            inputBuffer.flip();
            byte socksVersion = inputBuffer.get();
            byte command = inputBuffer.get();
            byte reserved = inputBuffer.get();
            if (socksVersion != Constants.SOCKS5 || command != Constants.TCPIP || reserved != Constants.RESERVED) {
                throw new IOException("incorrect bruh");
            }

            byte addressType = inputBuffer.get();
            byte[] address;
            if (addressType == Constants.IPV4) {
                address = new byte[4];
                inputBuffer.get(address, 0, 4);
            } else if (addressType == Constants.DNS) {
                int nameLength = inputBuffer.get();
                address = new byte[nameLength];
                inputBuffer.get(address, 0, nameLength);
            } else {
                throw new IOException("dkjsnkjsd");
            }
            port = inputBuffer.getShort();

            state = State.RESOLVING;
            clientKey.interestOps(0);
            if(addressType == Constants.DNS){
                dnsHandler.sendRequest(address, this);
            }
            else{
                InetAddress inetAddress = InetAddress.getByAddress(address);
                connectToServer(inetAddress);
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }

    public void connectToServer(@NonNull InetAddress address){
        checkState(State.RESOLVING);
        var inetSocketAddress = new InetSocketAddress(address, port);
        try {
            SocketChannel serverChannel = SocketChannel.open(inetSocketAddress);
            serverChannel.configureBlocking(false);
            var serverKey = serverChannel.register(selector, 0);
            serverHandler = new ServerHandler(serverKey, this);
            state = State.SENDING_CONNECTION_RESPONSE;
            constructConnectionResponseMessage();
            clientKey.interestOps(SelectionKey.OP_WRITE);
        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }

    public void constructConnectionResponseMessage(){
        checkState(State.SENDING_CONNECTION_RESPONSE);
        outputBuffer.clear();
        byte[] resultMessage = ArrayUtils.addAll(new byte[]{Constants.SOCKS5, Constants.OK, Constants.RESERVED, Constants.IPV4},
                Constants.LOCALHOST);
        resultMessage = ArrayUtils.addAll(resultMessage, (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF));
        outputBuffer.put(resultMessage);
        outputBuffer.flip();
    }

    private void checkState(@NonNull State state){
        if (this.state != state) {
            String errorMsg = "Illegal state: expected %s, but found %s"
                    .formatted(this.state.name(), state.name());
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    private void sendingConnectionResponseMessage(){
        checkState(State.SENDING_CONNECTION_RESPONSE);
        try {
            clientChannel.write(outputBuffer);
            if (!outputBuffer.hasRemaining()) {
                clientKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                state = State.FORWADING;
                inputBuffer.clear();
                inputBuffer.flip();
                outputBuffer.clear();
                outputBuffer.flip();
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }

    public void setInputEvent(boolean isInput){
        if(isInput) {
            SelectionKeyUtils.turnOnReadOption(clientKey);
        }
        else{
            SelectionKeyUtils.turnOffReadOption(clientKey);
        }
    }

    public void setOutputEvent(boolean isOutput){
        if(isOutput) {
            SelectionKeyUtils.turnOnWriteOption(clientKey);
        }
        else{
            SelectionKeyUtils.turnOffWriteOption(clientKey);
        }
    }

    private void readToBuffer(){
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

    private void write2client(){

    }

    @Override
    public void read() {
        switch (state){
            case AWAITING_AUTH_REQUEST -> readAndHandleAuthRequest();
            case AWAITING_CONNECTION_REQUEST -> readAndHandleConnectionRequest();
            case FORWADING -> readToBuffer();
            default -> throw new IllegalStateException("");
        }
    }

    @Override
    public void write() {

    }

    @Override
    public void connect() {
        try {
            clientChannel.finishConnect();
        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }

    @Override
    public void accept() {
        throw new UnsupportedOperationException("");
    }

    @Override
    public void close() {
        try {
            clientChannel.close();
            if (serverHandler != null) {
                serverHandler.getServerChannel().close();
            }
        } catch (IOException e) {
            log.error(e);
        }
    }
}
