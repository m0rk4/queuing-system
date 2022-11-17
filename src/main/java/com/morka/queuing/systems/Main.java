package com.morka.queuing.systems;

import com.morka.queuing.systems.model.Appliance;
import com.morka.queuing.systems.model.Condition;
import com.morka.queuing.systems.model.State;
import com.morka.queuing.systems.model.StateTransition;
import com.morka.queuing.systems.service.StateTransitionsParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static com.morka.queuing.systems.util.Conditions.NEW_APP_CONDITION;
import static com.morka.queuing.systems.util.Conditions.PASS_1_CONDITION;
import static com.morka.queuing.systems.util.Conditions.PASS_2_CONDITION;
import static com.morka.queuing.systems.util.Conditions.PASS_3_CONDITION;
import static com.morka.queuing.systems.util.Conditions.SKIP_1_CONDITION;
import static com.morka.queuing.systems.util.Conditions.SKIP_2_CONDITION;
import static com.morka.queuing.systems.util.Conditions.SKIP_3_CONDITION;
import static java.util.Objects.nonNull;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static Appliance firstApp;
    private static Appliance secondApp;
    private static Appliance thirdApp;

    public static void main(String[] args) {
        var params = getParams(args);
        var totalTicks = params.ticks();
        var transitions = new StateTransitionsParser()
                .parse(Paths.get("transitions.txt").toFile());
        var statesDistribution = new HashMap<State, Integer>();

        var releasedAppsCount = 0;
        var processingAppsCountSum = 0;
        var totalLiveTimeSum = 0;
        var suppliedAppsCount = 0;
        var failedCount = 0;

        var prevState = State.fromString("000");
        for (var tickNumber = 0; tickNumber < totalTicks; tickNumber++) {
            var conditions = buildConditions(params);
            var nextState = getNextState(transitions, prevState, conditions);

            var stateTransitionStringified = new StringBuilder();
            stateTransitionStringified
                    .append("\n\t")
                    .append(stringifyStateTransition(prevState, conditions, nextState))
                    .append("\n\t[")
                    .append(formatApps());

            statesDistribution.merge(prevState, 1, Integer::sum);
            processingAppsCountSum += getProcessingApps(prevState);
            failedCount += getNumberOfFailedApps(prevState, conditions);
            if (conditions.contains(NEW_APP_CONDITION))
                suppliedAppsCount++;

            Appliance firstProbablyTransferredToNext = null;
            Appliance secondProbablyTransferredToNext = null;
            if (nextState.firstBusy()) {
                if (!prevState.firstBusy() && conditions.contains(NEW_APP_CONDITION)) {
                    // 0 -> 1
                    firstApp = new Appliance(tickNumber);
                } else if (prevState.firstBusy() && conditions.contains(SKIP_1_CONDITION)) {
                    // 1 -> 1
                } else if (prevState.firstBusy() && conditions.contains(PASS_1_CONDITION) && conditions.contains(NEW_APP_CONDITION)) {
                    // 1 -> 0 (+new)-> 1
                    firstProbablyTransferredToNext = firstApp;
                    firstApp = new Appliance(tickNumber);
                }
            } else {
                if (prevState.firstBusy() && conditions.contains(PASS_1_CONDITION)) {
                    // 1 -> 0
                    firstProbablyTransferredToNext = firstApp;
                }
                firstApp = null;
            }

            if (nextState.secondBusy()) {
                if (!prevState.secondBusy() && conditions.contains(NEW_APP_CONDITION)) {
                    // 0 -> 1
                    secondApp = new Appliance(tickNumber);
                } else if (prevState.secondBusy() && conditions.contains(SKIP_2_CONDITION)) {
                    // 1 -> 1
                } else if (prevState.secondBusy() && conditions.contains(PASS_2_CONDITION) && conditions.contains(NEW_APP_CONDITION)) {
                    // 1 -> 0 (+new)-> 1
                    secondProbablyTransferredToNext = secondApp;
                    secondApp = new Appliance(tickNumber);
                }
            } else {
                if (prevState.secondBusy() && conditions.contains(PASS_2_CONDITION)) {
                    // 1 -> 0
                    secondProbablyTransferredToNext = secondApp;
                }
                secondApp = null;
            }

            var releasedApp = (Appliance) null;
            var firstPickedForTransfer = ThreadLocalRandom.current().nextInt(0, 2) == 0;
            if (nextState.thirdBusy()) {
                if (!prevState.thirdBusy()) {
                    // 0 -> 1 (from 1 or 2)
                    thirdApp = getAppTransferredToThirdChannel(firstProbablyTransferredToNext, secondProbablyTransferredToNext, firstPickedForTransfer);
                } else if (conditions.contains(SKIP_3_CONDITION)) {
                    // 1 -> 1 (nothing)
                } else if (conditions.contains(PASS_3_CONDITION)) {
                    // 1 -> 0 (+(from 1 or 2 or both)) -> 1
                    releasedApp = thirdApp;
                    totalLiveTimeSum += tickNumber - releasedApp.acceptedTickNumber();
                    releasedAppsCount++;
                    thirdApp = getAppTransferredToThirdChannel(firstProbablyTransferredToNext, secondProbablyTransferredToNext, firstPickedForTransfer);
                }
            } else {
                if (prevState.thirdBusy() && conditions.contains(PASS_3_CONDITION)) {
                    // 1 -> 0
                    releasedApp = thirdApp;
                    totalLiveTimeSum += tickNumber - releasedApp.acceptedTickNumber();
                    releasedAppsCount++;
                }
                thirdApp = null;
            }

            stateTransitionStringified
                    .append("] -> [")
                    .append(formatApps())
                    .append(']');
            if (nonNull(releasedApp))
                stateTransitionStringified
                        .append("\n\tRELEASED: ")
                        .append(releasedApp.acceptedTickNumber());
            LOGGER.debug(stateTransitionStringified::toString);

            prevState = nextState;
        }

        var failProb = (double) failedCount / suppliedAppsCount;

        printDistribution(statesDistribution);
        formatAndPrintDouble("Pbl", 0);
        formatAndPrintDouble("Lq", 0);
        formatAndPrintDouble("Wq", 0);
        formatAndPrintDouble("A", (double) releasedAppsCount / totalTicks);
        formatAndPrintDouble("Ls", (double) processingAppsCountSum / totalTicks);
        formatAndPrintDouble("Pfail", failProb);
        formatAndPrintDouble("Q", 1.0 - failProb);
        formatAndPrintDouble("Ws", (double) totalLiveTimeSum / releasedAppsCount);
        formatAndPrintDouble("Kch1", getChannelBusiness(statesDistribution, 0, totalTicks));
        formatAndPrintDouble("Kch2", getChannelBusiness(statesDistribution, 1, totalTicks));
        formatAndPrintDouble("Kch3", getChannelBusiness(statesDistribution, 2, totalTicks));
    }

    private static String stringifyStateTransition(State prevState, Set<Condition> conditions, State nextState) {
        return prevState + " -> " + nextState + " " + conditions;
    }

    private static String formatApps() {
        var mapper = (Function<Appliance, String>) app -> app != null ? app.acceptedTickNumber() + " " : "null ";
        return mapper.apply(firstApp)
                + mapper.apply(secondApp)
                + mapper.apply(thirdApp);
    }

    private static int getProcessingApps(State prevState) {
        var busyConverter = (Function<Boolean, Integer>) value -> value ? 1 : 0;
        return busyConverter.apply(prevState.firstBusy())
                + busyConverter.apply(prevState.secondBusy())
                + busyConverter.apply(prevState.thirdBusy());
    }

    private static Appliance getAppTransferredToThirdChannel(Appliance transferred1,
                                                             Appliance transferred2,
                                                             boolean firstPicked) {
        assert (nonNull(transferred1) || nonNull(transferred2));

        if (nonNull(transferred1) && nonNull(transferred2))
            return firstPicked ? transferred1 : transferred2;
        else if (nonNull(transferred1))
            return transferred1;
        else
            return transferred2;
    }

    private static State getNextState(Map<State, List<StateTransition>> transitions,
                                      State current, Set<Condition> conditions) {
        return transitions.get(current).stream()
                .filter(stateTransition -> isConditionsSatisfyStateTransition(conditions, stateTransition))
                .findFirst()
                .map(StateTransition::target)
                .orElseThrow(() -> new IllegalStateException("Oops, seems you haven't covered some state transitions!"));
    }

    private static boolean isConditionsSatisfyStateTransition(Set<Condition> conditions,
                                                              StateTransition stateTransition) {
        return stateTransition.conditionGroups().stream().anyMatch(conditionGroup ->
                conditions.containsAll(conditionGroup.conditions()));
    }

    private static int getNumberOfFailedApps(State current, Set<Condition> conditions) {
        var lostAppsCount = 0;

        var newAppPushed = conditions.contains(NEW_APP_CONDITION);
        var twoChannelsBusy = current.firstBusy() && current.secondBusy();
        var twoChannelsWillNotBeFree = conditions.contains(SKIP_1_CONDITION) &&
                conditions.contains(SKIP_2_CONDITION);
        if (newAppPushed && twoChannelsBusy && twoChannelsWillNotBeFree)
            lostAppsCount++;

        var firstLevelNotPushing = (conditions.contains(SKIP_1_CONDITION) || !current.firstBusy())
                && (conditions.contains(SKIP_2_CONDITION) || !current.secondBusy());
        if (firstLevelNotPushing)
            return lostAppsCount;

        var isThirdBusy = current.thirdBusy() && conditions.contains(SKIP_3_CONDITION);
        var twoPushed = conditions.contains(PASS_1_CONDITION) && current.firstBusy()
                && conditions.contains(PASS_2_CONDITION) && current.secondBusy();
        if (isThirdBusy)
            lostAppsCount++;
        if (twoPushed)
            lostAppsCount++;

        return lostAppsCount;
    }

    private static double getChannelBusiness(Map<State, Integer> statesDistribution, int channel, int ticks) {
        return (double) statesDistribution.entrySet().stream()
                .filter(set -> set.getKey().toString().charAt(channel) == '1')
                .mapToInt(Map.Entry::getValue)
                .sum() / ticks;
    }

    private static Condition buildCondition(String id, double probability) {
        var random = ThreadLocalRandom.current().nextDouble();
        return new Condition(id, random > probability);
    }

    private static Set<Condition> buildConditions(InputParams params) {
        return new HashSet<>() {{
            add(buildCondition("1", params.pi1()));
            add(buildCondition("2", params.pi2()));
            add(buildCondition("3", params.pi3()));
            add(buildCondition("p", params.p()));
        }};
    }

    private static void formatAndPrintDouble(String name, double value) {
        System.out.printf("%s = %.5f%n", name, value);
    }

    private static void printDistribution(Map<State, Integer> distribution) {
        var total = distribution.values().stream().mapToInt(i -> i).sum();
        distribution.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey().toString(), (double) entry.getValue() / total))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> formatAndPrintDouble("P" + entry.getKey(), entry.getValue()));
    }

    private static InputParams getParams(String[] args) {
        if (args.length < 5) {
            LOGGER.error("5 Parameters expected: pi1, pi2, pi3, p and ticks");
            System.exit(1);
        }
        return new InputParams(
                Double.parseDouble(args[0]),
                Double.parseDouble(args[1]),
                Double.parseDouble(args[2]),
                Double.parseDouble(args[3]),
                Integer.parseInt(args[4])
        );
    }

    private record InputParams(double pi1, double pi2, double pi3, double p, int ticks) {
    }
}
