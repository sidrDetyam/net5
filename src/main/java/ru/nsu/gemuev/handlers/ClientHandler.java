package ru.nsu.gemuev.handlers;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

@Log4j2
public class ClientHandler extends AbstractCouplingHandler {

    private final DnsHandler dnsHandler;
    private final Selector selector;
    private ClientHandlerState state;
    private short port;


    public ClientHandler(@NonNull SelectionKey clientKey, @NonNull DnsHandler dnsHandler) {
        super(clientKey);
        selector = clientKey.selector();
        this.dnsHandler = dnsHandler;
        setStateAndOps(ClientHandlerState.AWAITING_AUTH_REQUEST, OP_READ);
    }


    @Override
    public void read() {
        switch (state) {
            case AWAITING_AUTH_REQUEST -> readAndHandleAuthRequest();
            case AWAITING_CONNECTION_REQUEST -> readAndHandleConnectionRequest();
            case FORWARDING -> readToBuffer();
            default -> throw new IllegalStateException("Incorrect state on read operation");
        }
    }


    @Override
    public void write() {
        switch (state) {
            case SENDING_AUTH_RESPONSE -> sendingSocksResponse(() ->
                    setStateAndOps(ClientHandlerState.AWAITING_CONNECTION_REQUEST, OP_READ));
            case SENDING_CONNECTION_RESPONSE -> sendingSocksResponse(() -> {
                setStateAndOps(ClientHandlerState.FORWARDING, OP_READ);
                buffer.clear();
                buffer.flip();
            });
            case SENDING_SOCKS_ERROR -> sendingSocksResponse(this::close);
            case FORWARDING -> write2Channel();
            default -> throw new IllegalStateException("Incorrect state on write operation");
        }
    }


    @Override
    public void close() {
        try {
            if (state != ClientHandlerState.CLOSED) {
                state = ClientHandlerState.CLOSED;
                channel.close();
                if (partner != null) {
                    partner.getChannel().close();
                }
            }
        } catch (IOException e) {
            log.error(e);
        }
    }


    private void constructAuthResponse() {
        buffer.clear();
        buffer.put(Socks5Constants.SOCKS5);
        buffer.put(Socks5Constants.NO_AUTH);
        buffer.flip();
    }


    private void setStateAndOps(@NonNull ClientHandlerState newState, int ops) {
        state = newState;
        key.interestOps(ops);
    }


    private void readAndHandleAuthRequest() {
        checkState(ClientHandlerState.AWAITING_AUTH_REQUEST);
        try {
            buffer.clear();
            int cnt = channel.read(buffer);
            buffer.flip();
            if (cnt == -1) {
                throw new IOException("Unexpected EOF");
            }
            byte version = buffer.get();
            byte authMethodsCnt = buffer.get();
            byte[] methods = new byte[authMethodsCnt];
            buffer.get(methods);
            if (version != Socks5Constants.SOCKS5 || !ArrayUtils.contains(methods, Socks5Constants.NO_AUTH)) {
                throw new IOException("Illegal auth request");
            }
            constructAuthResponse();
            setStateAndOps(ClientHandlerState.SENDING_AUTH_RESPONSE, OP_WRITE);
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }


    private void sendingSocksResponse(@NonNull Runnable onEnd) {
        try {
            channel.write(buffer);
            if (!buffer.hasRemaining()) {
                onEnd.run();
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }


    public void readAndHandleConnectionRequest() {
        checkState(ClientHandlerState.AWAITING_CONNECTION_REQUEST);
        try {
            buffer.clear();
            if (channel.read(buffer) == -1) {
                throw new IOException("Unexpected EOF");
            }
            buffer.flip();
            byte version = buffer.get();
            byte command = buffer.get();
            byte reserved = buffer.get();
            if (version != Socks5Constants.SOCKS5 || command != Socks5Constants.TCP
                    || reserved != Socks5Constants.RESERVED) {
                constructConnectionResponseMessage(true);
                setStateAndOps(ClientHandlerState.SENDING_SOCKS_ERROR, OP_WRITE);
            }

            byte addressType = buffer.get();
            if (addressType == Socks5Constants.IPV4) {
                byte[] address = new byte[4];
                buffer.get(address);
                port = buffer.getShort();
                setStateAndOps(ClientHandlerState.RESOLVING, 0);
                InetAddress inetAddress = InetAddress.getByAddress(address);
                connectToServer(inetAddress);
            } else if (addressType == Socks5Constants.DNS) {
                int length = buffer.get();
                byte[] address = new byte[length];
                buffer.get(address);
                port = buffer.getShort();
                setStateAndOps(ClientHandlerState.RESOLVING, 0);
                dnsHandler.sendRequest(address, this);
            } else {
                constructConnectionResponseMessage(true);
                setStateAndOps(ClientHandlerState.SENDING_SOCKS_ERROR, OP_WRITE);
            }
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }


    public void connectToServer(@NonNull InetAddress address) {
        if (state == ClientHandlerState.CLOSED) {
            return;
        }
        checkState(ClientHandlerState.RESOLVING);
        var inetSocketAddress = new InetSocketAddress(address, port);
        try {
            SocketChannel serverChannel = SocketChannel.open(inetSocketAddress);
            serverChannel.configureBlocking(false);
            var serverKey = serverChannel.register(selector, 0);
            partner = new ServerHandler(serverKey, this);
            setStateAndOps(ClientHandlerState.SENDING_CONNECTION_RESPONSE, OP_WRITE);
            constructConnectionResponseMessage(false);
            serverKey.attach(partner);
        } catch (IOException e) {
            log.error(e);
            close();
        }
    }


    public void constructConnectionResponseMessage(boolean isError) {
        buffer.clear();
        buffer.put(Socks5Constants.SOCKS5);
        buffer.put(isError ? Socks5Constants.PROTOCOL_ERROR : Socks5Constants.OK);
        buffer.put(Socks5Constants.RESERVED);
        buffer.put(Socks5Constants.IPV4);
        for (int i = 0; i < 6; ++i) {
            buffer.put(Socks5Constants.RESERVED);
        }
        buffer.flip();
    }


    private void checkState(@NonNull ClientHandlerState state) {
        if (this.state != state) {
            String errorMsg = "Illegal state: expected %s, but found %s"
                    .formatted(this.state.name(), state.name());
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }
}
