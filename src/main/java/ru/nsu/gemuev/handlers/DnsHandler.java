package ru.nsu.gemuev.handlers;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.xbill.DNS.Record;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static java.nio.channels.SelectionKey.OP_READ;

@Log4j2
public class DnsHandler implements Handler {
    private final DatagramChannel dnsChannel;
    private final SelectionKey key;
    private final ByteBuffer buffer = ByteBuffer.allocate(Socks5Constants.DEF_BUF_SIZE);
    private final Map<Integer, ClientHandler> clients = new HashMap<>();
    private final Queue<ByteBuffer> requestsQueue = new ArrayDeque<>();

    public DnsHandler(@NonNull SelectionKey key) {
        this.key = key;
        dnsChannel = (DatagramChannel) key.channel();
        key.interestOps(OP_READ);
    }

    private void setUpWrite(boolean isEnable) {
        if (isEnable) {
            key.interestOpsOr(SelectionKey.OP_WRITE);
        } else {
            key.interestOpsAnd(~SelectionKey.OP_WRITE);
        }
    }

    @SneakyThrows
    public void sendRequest(@NonNull byte[] address, @NonNull ClientHandler client) {
        Name name = org.xbill.DNS.Name.fromString(new String(address), Name.root);
        Record rec = Record.newRecord(name, Type.A, DClass.IN);
        Message dns = Message.newQuery(rec);
        requestsQueue.add(ByteBuffer.wrap(dns.toWire()));
        setUpWrite(true);
        clients.put(dns.getHeader().getID(), client);
    }

    @SneakyThrows
    @Override
    public void read() {
        dnsChannel.read(buffer);
        Message msg = new Message(buffer.array());
        Record[] recs = msg.getSectionArray(1);
        for (Record rec : recs) {
            if (rec instanceof ARecord arec) {
                InetAddress adr = arec.getAddress();
                int id = msg.getHeader().getID();
                ClientHandler client = clients.get(id);
                clients.remove(id);
                client.connectToServer(adr);
                return;
            }
        }
    }

    @SneakyThrows
    @Override
    public void write() {
        if (requestsQueue.isEmpty()) {
            throw new IllegalStateException("Write option on, but queue is empty");
        }
        ByteBuffer request = requestsQueue.remove();
        dnsChannel.write(request);
        if (requestsQueue.isEmpty()) {
            setUpWrite(false);
        }
    }

    @Override
    public void close() {
        try {
            dnsChannel.close();
        } catch (IOException e) {
            log.error(e);
        }
    }

    @Override
    public void connect() {
        throw new UnsupportedOperationException("Dns handler doesnt support connect operation");
    }

    @Override
    public void accept() {
        throw new UnsupportedOperationException("Dns handler doesnt support accept operation");
    }
}
