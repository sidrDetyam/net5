package ru.nsu.gemuev;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

@Log4j2
public class ClientHandler extends AbstractCouplingHandler{

    private final DnsHandler dnsHandler;
    private final Selector selector;
    private State state;
    private short port;
    private boolean isClosed = false;

    public ClientHandler(@NonNull SelectionKey clientKey, @NonNull DnsHandler dnsHandler) {
        super(clientKey);
        selector = clientKey.selector();
        this.dnsHandler = dnsHandler;
        state = State.AWAITING_AUTH_REQUEST;
        clientKey.interestOps(SelectionKey.OP_READ);
    }

    private void constructAuthResponse() {
        buffer.clear();
        buffer.put(Constants.SOCKS5);
        buffer.put(Constants.NO_AUTH);
        buffer.flip();
    }

    private void readAndHandleAuthRequest() {
        if (state != State.AWAITING_AUTH_REQUEST) {
            throw new IllegalStateException("");
        }

        buffer.clear();
        int cnt;
        try {
            cnt = channel.read(buffer);
        } catch (IOException e) {
            log.error(e);
            cnt = -1;
        }
        if (cnt == -1) {
            close();
            return;
        }
        buffer.flip();
        byte socksVersion = buffer.get();
        byte methodAmount = buffer.get();
        byte[] methods = new byte[methodAmount];
        buffer.get(methods);
        if (socksVersion != Constants.SOCKS5 || !ArrayUtils.contains(methods, Constants.NO_AUTH)) {
            close();
            return;
        }

        constructAuthResponse();
        state = State.SENDING_AUTH_RESPONSE;
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void sendingAuthResponse() {
        if (state != State.SENDING_AUTH_RESPONSE) {
            throw new IllegalStateException("");
        }
        try {
            channel.write(buffer);
            if (!buffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
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
            buffer.clear();
            if (channel.read(buffer) == -1) {
                throw new IOException("unexpected EOF");
            }
            buffer.flip();
            byte socksVersion = buffer.get();
            byte command = buffer.get();
            byte reserved = buffer.get();
            if (socksVersion != Constants.SOCKS5 || command != Constants.TCPIP || reserved != Constants.RESERVED) {
                throw new IOException("incorrect bruh");
            }

            byte addressType = buffer.get();
            byte[] address;
            if (addressType == Constants.IPV4) {
                address = new byte[4];
                buffer.get(address, 0, 4);
            } else if (addressType == Constants.DNS) {
                int nameLength = buffer.get();
                address = new byte[nameLength];
                buffer.get(address, 0, nameLength);
            } else {
                throw new IOException("dkjsnkjsd");
            }
            port = buffer.getShort();

            state = State.RESOLVING;
            key.interestOps(0);
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
            partner = new ServerHandler(serverKey, this);
            state = State.SENDING_CONNECTION_RESPONSE;
            constructConnectionResponseMessage();
            key.interestOps(SelectionKey.OP_WRITE);
        }
        catch (IOException e){
            log.error(e);
            close();
        }
    }

    public void constructConnectionResponseMessage(){
        checkState(State.SENDING_CONNECTION_RESPONSE);
        buffer.clear();
        byte[] resultMessage = ArrayUtils.addAll(new byte[]{Constants.SOCKS5, Constants.OK, Constants.RESERVED, Constants.IPV4},
                Constants.LOCALHOST);
        resultMessage = ArrayUtils.addAll(resultMessage, (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF));
        buffer.put(resultMessage);
        buffer.flip();
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
            channel.write(buffer);
            if (!buffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                state = State.FORWADING;
                buffer.clear();
                buffer.flip();
                buffer.clear();
                buffer.flip();
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
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
        switch (state){
            case SENDING_AUTH_RESPONSE -> sendingAuthResponse();
            case SENDING_CONNECTION_RESPONSE -> sendingConnectionResponseMessage();
            case FORWADING -> write2Channel();
            default -> throw new IllegalStateException("");
        }
    }

    @Override
    public void close() {
        try {
            if(!isClosed) {
                isClosed = true;
                channel.close();
                if (partner != null) {
                    partner.getChannel().close();
                }
            }
        } catch (IOException e) {
            log.error(e);
        }
    }
}
