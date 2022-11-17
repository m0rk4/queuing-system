package com.morka.queuing.systems.model;

public record State(boolean firstBusy, boolean secondBusy, boolean thirdBusy) {

    public static State fromString(String state) {
        return new State(
                toBoolean(state.charAt(0)),
                toBoolean(state.charAt(1)),
                toBoolean(state.charAt(2))
        );
    }

    private static String toString(boolean busy) {
        return busy ? "1" : "0";
    }

    private static boolean toBoolean(char c) {
        return c == '1';
    }

    @Override
    public String toString() {
        return toString(firstBusy) + toString(secondBusy) + toString(thirdBusy);
    }
}
