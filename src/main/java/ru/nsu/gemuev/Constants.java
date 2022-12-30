package ru.nsu.gemuev;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {
    public static final int DEF_BUF_SIZE = 8096;

    public static final byte OK = 0x00;
    public static final byte ERROR = 0x01;
    public static final byte NO_AUTH = 0x00;
    public static final byte SOCKS5 = 0x05;
    public static final byte TCPIP = 0x01;
    public static final byte IPV4 = 0x01;
    public static final byte DNS = 0x03;
    public static final byte RESERVED = 0x00;
    public static byte[] LOCALHOST = new byte[]{0x7F, 0x00, 0x00, 0x01};
}
