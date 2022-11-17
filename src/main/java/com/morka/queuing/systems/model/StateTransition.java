package com.morka.queuing.systems.model;

import java.util.List;

public record StateTransition(State initial, State target, List<ConditionGroup> conditionGroups) {
}
