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
        var inetSocketAddress = new InetSocketAddress(address, port);
        try {
            SocketChannel serverChannel = SocketChannel.open(inetSocketAddress);
            serverHandler = new ServerHandler(serverChannel);
            sendSecondConfirmationMessage();
            serverChannel.configureBlocking(false);
            var serverKey = serverChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
            proxyConnection.put(channel, serverChannel);
            proxyConnection.put(serverChannel, channel);
            connectionStage.put(serverChannel, Stage.THIRD);
        }
        catch (IOException e){
            log.error(e);
            close();
        }
        return serverChannel.isConnected();
    }

    @SneakyThrows
    public void handleSecond() {
        buffer.clear();
        SecondParseResult secondMessage = getSecondMessage().orElseThrow();
        port = secondMessage.getPort();
        dnsHandler.sendRequest(secondMessage.getAddress(), this);
        state = State.RESOLVING;
    }

    public void sendSecondConfirmationMessage() throws IOException {
        //ByteBuffer message = ByteBuffer.allocate(10);
        byte[] resultMessage = null;
        resultMessage = ArrayUtils.addAll(new byte[]{Constants.SOCKS5, Constants.OK, Constants.RESERVED, Constants.IPV4},
                Constants.LOCALHOST);
        resultMessage = ArrayUtils.addAll(resultMessage, (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF));
        //message.put(resultMessage);
        serverHandler.getChannel().write(ByteBuffer.wrap(resultMessage, 0, 10));
    }


    @SneakyThrows
    public void read() {
        if (state == State.AWAITING_AUTH_REQUEST) {
            readAndHandleAuthRequest();
            return;
        }
        if (state == State.AWAITING_CONNECTION_REQUEST) {
            handleSecond();
            return;
        }
    }

    @Override
    public void write() {

    }

    @Override
    public void connect() {
        throw new UnsupportedOperationException("");
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

            }
        } catch (IOException e) {
            log.error(e);
        }
    }
}
