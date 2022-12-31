package ru.nsu.gemuev;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Optional;

@Log4j2
public class ClientHandler implements Handler {

    private final SelectionKey clientKey;
    private final DnsHandler dnsHandler;
    private final SocketChannel clientChannel;
    private final Selector selector;
    private State state;
    private short port;
    private ServerHandler serverHandler = null;

    private final ByteBuffer clientInputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);
    private final ByteBuffer clientOutputBuffer = ByteBuffer.allocate(Constants.DEF_BUF_SIZE);


    public ClientHandler(@NonNull SelectionKey clientKey, @NonNull DnsHandler dnsHandler) {
        clientChannel = (SocketChannel) clientKey.channel();
        selector = clientKey.selector();
        this.clientKey = clientKey;
        this.dnsHandler = dnsHandler;
        state = State.AWAITING_AUTH_REQUEST;
        clientKey.interestOps(SelectionKey.OP_READ);
    }

    private void constructAuthResponse() {
        clientOutputBuffer.clear();
        clientOutputBuffer.put(Constants.SOCKS5);
        clientOutputBuffer.put(Constants.NO_AUTH);
        clientOutputBuffer.flip();
    }

    private void readAndHandleAuthRequest() {
        if (state != State.AWAITING_AUTH_REQUEST) {
            throw new IllegalStateException("");
        }

        clientInputBuffer.clear();
        int cnt;
        try {
            cnt = clientChannel.read(clientInputBuffer);
        } catch (IOException e) {
            log.error(e);
            cnt = -1;
        }
        if (cnt == -1) {
            close();
            return;
        }
        clientInputBuffer.flip();
        byte socksVersion = clientInputBuffer.get();
        byte methodAmount = clientInputBuffer.get();
        byte[] methods = new byte[methodAmount];
        clientInputBuffer.get(methods);
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
            clientChannel.write(clientOutputBuffer);
            if (!clientOutputBuffer.hasRemaining()) {
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
            clientInputBuffer.clear();
            if (clientChannel.read(clientInputBuffer) == -1) {
                throw new IOException("unexpected EOF");
            }
            clientInputBuffer.flip();
            byte socksVersion = clientInputBuffer.get();
            byte command = clientInputBuffer.get();
            byte reserved = clientInputBuffer.get();
            if (socksVersion != Constants.SOCKS5 || command != Constants.TCPIP || reserved != Constants.RESERVED) {
                throw new IOException("incorrect bruh");
            }

            byte addressType = clientInputBuffer.get();
            byte[] address;
            if (addressType == Constants.IPV4) {
                address = new byte[4];
                clientInputBuffer.get(address, 0, 4);
            } else if (addressType == Constants.DNS) {
                int nameLength = clientInputBuffer.get();
                address = new byte[nameLength];
                clientInputBuffer.get(address, 0, nameLength);
            } else {
                throw new IOException("dkjsnkjsd");
            }
            port = clientInputBuffer.getShort();

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
        clientOutputBuffer.clear();
        byte[] resultMessage = ArrayUtils.addAll(new byte[]{Constants.SOCKS5, Constants.OK, Constants.RESERVED, Constants.IPV4},
                Constants.LOCALHOST);
        resultMessage = ArrayUtils.addAll(resultMessage, (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF));
        clientOutputBuffer.put(resultMessage);
        clientOutputBuffer.flip();
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
            clientChannel.write(clientOutputBuffer);
            if (!clientOutputBuffer.hasRemaining()) {
                clientKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                state = State.FORWADING;
                clientInputBuffer.clear();
                clientOutputBuffer.clear();
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }

    private void readToBuffer(){
        try {
            int read_ = clientChannel.read(clientInputBuffer);
            if(!clientInputBuffer.hasRemaining()){
                SelectionKeyUtils.turnOffReadOption(clientKey);
                if(read_ > 0){

                }
            }
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
                serverHandler.close();
            }
        } catch (IOException e) {
            log.error(e);
        }
    }
}
