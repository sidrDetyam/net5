package ru.nsu.gemuev;

public class SecondParseResult {
    private boolean correct;
    private boolean dns;
    private byte[] address;
    private int port;

    public SecondParseResult() {
        this.correct = false;
    }

    public SecondParseResult(boolean dns, boolean correct, byte[] address, int port) {
        this.correct = correct;
        this.dns = dns;
        this.address = address;
        this.port = port;
    }

    public boolean isDns() {
        return dns;
    }

    public void setDns(boolean dns) {
        this.dns = dns;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public byte[] getAddress() {
        return address;
    }

    public void setAddress(byte[] address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
