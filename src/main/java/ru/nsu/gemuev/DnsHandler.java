package ru.nsu.gemuev;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.xbill.DNS.Record;
import org.xbill.DNS.*;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class DnsHandler {

    private final DatagramChannel dnsChannel;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private final Map<Integer, ClientHandler> clients = new HashMap<>();

    @SneakyThrows
    public void sendRequest(byte[] address, ClientHandler client){
        Name name = org.xbill.DNS.Name.fromString(new String(address), Name.root);
        Record rec = Record.newRecord(name, Type.A, DClass.IN);
        Message dns = Message.newQuery(rec);
        dnsChannel.write(ByteBuffer.wrap(dns.toWire()));
        clients.put(dns.getHeader().getID(), client);
    }

    @SneakyThrows
    public void read(){
        int len = dnsChannel.read(buffer);
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
}
