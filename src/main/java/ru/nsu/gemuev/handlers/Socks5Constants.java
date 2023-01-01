package ru.nsu.gemuev.handlers;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Socks5Constants {
    public static final int DEF_BUF_SIZE = 8096;
    public static final byte OK = 0x00;
    public static final byte PROTOCOL_ERROR = 0x07;
    public static final byte NO_AUTH = 0x00;
    public static final byte SOCKS5 = 0x05;
    public static final byte TCP = 0x01;
    public static final byte IPV4 = 0x01;
    public static final byte DNS = 0x03;
    public static final byte RESERVED = 0x00;
}
