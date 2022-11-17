package com.morka.queuing.systems.service;

import com.morka.queuing.systems.model.Condition;
import com.morka.queuing.systems.model.ConditionGroup;
import com.morka.queuing.systems.model.State;
import com.morka.queuing.systems.model.StateTransition;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.copyOfRange;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.groupingBy;

public final class StateTransitionsParser {

    private static final String WHITESPACES_REGEX = "\\s+";

    private static final String CONDITIONS_SEPARATOR = "_";

    private static final String NEGATIVE_CONDITION_PREFIX = "!";

    public Map<State, List<StateTransition>> parse(File file) {
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(this::mapLineToTransition)
                    .collect(groupingBy(StateTransition::initial));
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    private StateTransition mapLineToTransition(String line) {
        final String[] tokens = line.split(WHITESPACES_REGEX);
        return new StateTransition(
                State.fromString(tokens[0]),
                State.fromString(tokens[1]),
                stream(copyOfRange(tokens, 2, tokens.length)).map(this::mapTokenToConditionGroup).toList()
        );
    }

    private ConditionGroup mapTokenToConditionGroup(String token) {
        final String[] tokens = token.substring(1, token.length() - 1).split(CONDITIONS_SEPARATOR);
        return new ConditionGroup(stream(tokens).map(this::mapTokenToCondition).toList());
    }

    private Condition mapTokenToCondition(String token) {
        boolean isNegative = token.startsWith(NEGATIVE_CONDITION_PREFIX);
        return new Condition(token.substring(isNegative ? 1 : 0), isNegative);
    }
}
