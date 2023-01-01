package ru.nsu.gemuev;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.xbill.DNS.ResolverConfig;
import ru.nsu.gemuev.handlers.AcceptHandler;
import ru.nsu.gemuev.handlers.DnsHandler;
import ru.nsu.gemuev.handlers.Handler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Log4j2
public class Proxy implements Runnable{
    private final Selector selector;

    public Proxy(int port){
        try {
            selector = Selector.open();
            List<InetSocketAddress> dnsServers = ResolverConfig.getCurrentConfig().servers();
            DatagramChannel channel = DatagramChannel.open();
            channel.connect(dnsServers.get(0));
            channel.configureBlocking(false);
            SelectionKey dnsKey = channel.register(selector, SelectionKey.OP_READ);
            DnsHandler dnsHandler = new DnsHandler(channel);
            dnsKey.attach(dnsHandler);

            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress("localhost", port));
            serverSocketChannel.configureBlocking(false);
            SelectionKey key = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            key.attach(new AcceptHandler(serverSocketChannel, dnsHandler, selector));
        }
        catch (IOException e){
            log.error(e);
            throw new UncheckedIOException(e);
        }
    }

    private int counter = 0;

    @Override
    @SneakyThrows
    public void run() {
        while(!Thread.currentThread().isInterrupted()){
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while (iter.hasNext()) {
                ++counter;
                var key = iter.next();
                iter.remove();
                if(key.isValid()){
                    Handler handler = (Handler) key.attachment();
                    if(key.isAcceptable()){
                        handler.accept();
                        System.out.println(counter + " accept");
                    }
                    else if(key.isConnectable()){
                        handler.connect();
                        System.out.println(counter + " connect");
                    }
                    else if(key.isReadable()){
                        System.out.println(counter + " read!");
                        handler.read();
                    }
                    else if(key.isWritable()){
                        System.out.println(counter + "write!");
                        handler.write();
                    }
                }
            }
        }
    }
}
