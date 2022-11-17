package com.morka.queuing.systems.util;

import com.morka.queuing.systems.model.Condition;

public final class Conditions {

    public static final Condition SKIP_1_CONDITION = new Condition("1", false);
    public static final Condition PASS_1_CONDITION = new Condition("1", true);
    public static final Condition SKIP_2_CONDITION = new Condition("2", false);
    public static final Condition PASS_2_CONDITION = new Condition("2", true);
    public static final Condition SKIP_3_CONDITION = new Condition("3", false);
    public static final Condition PASS_3_CONDITION = new Condition("3", true);
    public static final Condition NEW_APP_CONDITION = new Condition("p", true);
    private Conditions() {
        throw new IllegalAccessError();
    }
}
