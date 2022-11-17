package com.morka.queuing.systems.model;

public record Condition(String id, boolean isNegative) {

    @Override
    public String toString() {
        var prefix = isNegative() ? "!" : "";
        return prefix + id();
    }
}
