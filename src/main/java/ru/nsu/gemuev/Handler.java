package ru.nsu.gemuev;

import java.io.Closeable;

public interface Handler extends Closeable {
    void read();

    void write();

    void connect();

    void accept();
}
