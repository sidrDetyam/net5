package ru.nsu.gemuev;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.nio.channels.SelectionKey;

@UtilityClass
public class SelectionKeyUtils {
    public static void turnOnWriteOption(@NonNull SelectionKey key) {
        key.interestOpsOr(SelectionKey.OP_WRITE);
    }

    public static void turnOffWriteOption(@NonNull SelectionKey key) {
        key.interestOpsAnd(~SelectionKey.OP_WRITE);
    }

    public static void turnOnReadOption(@NonNull SelectionKey key) {
        key.interestOpsOr(SelectionKey.OP_READ);
    }

    public static void turnOffReadOption(@NonNull SelectionKey key) {
        key.interestOpsAnd(~SelectionKey.OP_READ);
    }
}
