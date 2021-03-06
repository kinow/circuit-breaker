package br.eti.kinoshita.circuit_breaker;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class TimedCircuitBreaker extends AbstractCircuitBreaker<Integer> {

    /** A map for accessing the strategy objects for the different states. */
    private static final Map<State, StateStrategy> STRATEGY_MAP = createStrategyMap();

    /** Stores information about the current check interval. */
    private final AtomicReference<CheckIntervalData> checkIntervalData;

    /** The threshold for opening the circuit breaker. */
    private final int openingThreshold;

    /** The time interval for opening the circuit breaker. */
    private final long openingInterval;

    /** The threshold for closing the circuit breaker. */
    private final int closingThreshold;

    /** The time interval for closing the circuit breaker. */
    private final long closingInterval;

    /**
     * Creates a new instance of {@code CircuitBreaker} and initializes all properties for
     * opening and closing it based on threshold values for events occurring in specific
     * intervals.
     *
     * @param openingThreshold the threshold for opening the circuit breaker; if this
     * number of events is received in the time span determined by the opening interval,
     * the circuit breaker is opened
     * @param openingInterval the interval for opening the circuit breaker
     * @param openingUnit the {@code TimeUnit} defining the opening interval
     * @param closingThreshold the threshold for closing the circuit breaker; if the
     * number of events received in the time span determined by the closing interval goes
     * below this threshold, the circuit breaker is closed again
     * @param closingInterval the interval for closing the circuit breaker
     * @param closingUnit the {@code TimeUnit} defining the closing interval
     */
    public TimedCircuitBreaker(int openingThreshold, long openingInterval,
            TimeUnit openingUnit, int closingThreshold, long closingInterval,
            TimeUnit closingUnit) {
        super();
        checkIntervalData = new AtomicReference<CheckIntervalData>(new CheckIntervalData(0, 0));
        this.openingThreshold = openingThreshold;
        this.openingInterval = openingUnit.toNanos(openingInterval);
        this.closingThreshold = closingThreshold;
        this.closingInterval = closingUnit.toNanos(closingInterval);
    }

    /**
     * Creates a new instance of {@code CircuitBreaker} with the same interval for opening
     * and closing checks.
     *
     * @param openingThreshold the threshold for opening the circuit breaker; if this
     * number of events is received in the time span determined by the check interval, the
     * circuit breaker is opened
     * @param checkInterval the check interval for opening or closing the circuit breaker
     * @param checkUnit the {@code TimeUnit} defining the check interval
     * @param closingThreshold the threshold for closing the circuit breaker; if the
     * number of events received in the time span determined by the check interval goes
     * below this threshold, the circuit breaker is closed again
     */
    public TimedCircuitBreaker(int openingThreshold, long checkInterval, TimeUnit checkUnit,
            int closingThreshold) {
        this(openingThreshold, checkInterval, checkUnit, closingThreshold, checkInterval,
                checkUnit);
    }

    /**
     * Creates a new instance of {@code CircuitBreaker} which uses the same parameters for
     * opening and closing checks.
     *
     * @param threshold the threshold for changing the status of the circuit breaker; if
     * the number of events received in a check interval is greater than this value, the
     * circuit breaker is opened; if it is lower than this value, it is closed again
     * @param checkInterval the check interval for opening or closing the circuit breaker
     * @param checkUnit the {@code TimeUnit} defining the check interval
     */
    public TimedCircuitBreaker(int threshold, long checkInterval, TimeUnit checkUnit) {
        this(threshold, checkInterval, checkUnit, threshold);
    }

    /**
     * Returns the threshold value for opening the circuit breaker. If this number of
     * events is received in the time span determined by the opening interval, the circuit
     * breaker is opened.
     *
     * @return the opening threshold
     */
    public int getOpeningThreshold() {
        return openingThreshold;
    }

    /**
     * Returns the interval (in nanoseconds) for checking for the opening threshold.
     *
     * @return the opening check interval
     */
    public long getOpeningInterval() {
        return openingInterval;
    }

    /**
     * Returns the threshold value for closing the circuit breaker. If the number of
     * events received in the time span determined by the closing interval goes below this
     * threshold, the circuit breaker is closed again.
     *
     * @return the closing threshold
     */
    public int getClosingThreshold() {
        return closingThreshold;
    }

    /**
     * Returns the interval (in nanoseconds) for checking for the closing threshold.
     *
     * @return the opening check interval
     */
    public long getClosingInterval() {
        return closingInterval;
    }

    @Override
    public boolean checkState() {
        return performStateCheck(0);
    }

    @Override
    public boolean incrementAndCheckState(Integer increment)
            throws CircuitBreakingException {
        return performStateCheck(1);
    }

    public boolean incrementAndCheckState() {
        return incrementAndCheckState(1);
    }

    @Override
    public void open() {
        super.open();
        checkIntervalData.set(new CheckIntervalData(0, now()));
    }

    @Override
    public void close() {
        super.close();
        checkIntervalData.set(new CheckIntervalData(0, now()));
    }

    /**
     * Actually checks the state of this circuit breaker and executes a state transition
     * if necessary.
     *
     * @param increment the increment for the internal counter
     * @return a flag whether the circuit breaker is now closed
     */
    private boolean performStateCheck(int increment) {
        CheckIntervalData currentData;
        CheckIntervalData nextData;
        State currentState;

        do {
            long time = now();
            currentState = state.get();
            currentData = checkIntervalData.get();
            nextData = nextCheckIntervalData(increment, currentData, currentState, time);
        } while (!updateCheckIntervalData(currentData, nextData));

        // This might cause a race condition if other changes happen in between!
        // Refer to the header comment!
        if (stateStrategy(currentState).isStateTransition(this, currentData, nextData)) {
            currentState = currentState.oppositeState();
            changeStateAndStartNewCheckInterval(currentState);
        }
        return !isOpen(currentState);
    }

    /**
     * Updates the {@code CheckIntervalData} object. The current data object is replaced
     * by the one modified by the last check. The return value indicates whether this was
     * successful. If it is <strong>false</strong>, another thread interfered, and the
     * whole operation has to be redone.
     *
     * @param currentData the current check data object
     * @param nextData the replacing check data object
     * @return a flag whether the update was successful
     */
    private boolean updateCheckIntervalData(CheckIntervalData currentData,
            CheckIntervalData nextData) {
        return (currentData == nextData)
                || checkIntervalData.compareAndSet(currentData, nextData);
    }

    /**
     * Changes the state of this circuit breaker and also initializes a new
     * {@code CheckIntervalData} object.
     *
     * @param newState the new state to be set
     */
    private void changeStateAndStartNewCheckInterval(State newState) {
        changeState(newState);
        checkIntervalData.set(new CheckIntervalData(0, now()));
    }

    /**
     * Calculates the next {@code CheckIntervalData} object based on the current data and
     * the current state. The next data object takes the counter increment and the current
     * time into account.
     *
     * @param increment the increment for the internal counter
     * @param currentData the current check data object
     * @param currentState the current state of the circuit breaker
     * @param time the current time
     * @return the updated {@code CheckIntervalData} object
     */
    private CheckIntervalData nextCheckIntervalData(int increment,
            CheckIntervalData currentData, State currentState, long time) {
        CheckIntervalData nextData;
        if (stateStrategy(currentState).isCheckIntervalFinished(this, currentData, time)) {
            nextData = new CheckIntervalData(increment, time);
        } else {
            nextData = currentData.increment(increment);
        }
        return nextData;
    }

    /**
     * Returns the current time in nanoseconds. This method is used to obtain the current
     * time. This is needed to calculate the check intervals correctly.
     *
     * @return the current time in nanoseconds
     */
    long now() {
        return System.nanoTime();
    }

    /**
     * Returns the {@code StateStrategy} object responsible for the given state.
     *
     * @param state the state
     * @return the corresponding {@code StateStrategy}
     * @throws CircuitBreakingException if the strategy cannot be resolved
     */
    private static StateStrategy stateStrategy(State state) {
        StateStrategy strategy = STRATEGY_MAP.get(state);
        if (strategy == null) {
            throw new CircuitBreakingException("Invalid state: " + state);
        }
        return strategy;
    }

    /**
     * Creates the map with strategy objects. It allows access for a strategy for a given
     * state.
     *
     * @return the strategy map
     */
    private static Map<State, StateStrategy> createStrategyMap() {
        Map<State, StateStrategy> map = new EnumMap<State, StateStrategy>(State.class);
        map.put(State.CLOSED, new StateStrategyClosed());
        map.put(State.OPEN, new StateStrategyOpen());
        return map;
    }

    /**
     * An internally used data class holding information about the checks performed by
     * this class. Basically, the number of received events and the start time of the
     * current check interval are stored.
     */
    private static class CheckIntervalData {
        /** The counter for events. */
        private final int eventCount;

        /** The start time of the current check interval. */
        private final long checkIntervalStart;

        /**
         * Creates a new instance of {@code CheckIntervalData}.
         *
         * @param count the current count value
         * @param intervalStart the start time of the check interval
         */
        public CheckIntervalData(int count, long intervalStart) {
            eventCount = count;
            checkIntervalStart = intervalStart;
        }

        /**
         * Returns the event counter.
         *
         * @return the number of received events
         */
        public int getEventCount() {
            return eventCount;
        }

        /**
         * Returns the start time of the current check interval.
         *
         * @return the check interval start time
         */
        public long getCheckIntervalStart() {
            return checkIntervalStart;
        }

        /**
         * Returns a new instance of {@code CheckIntervalData} with the event counter
         * incremented by the given delta. If the delta is 0, this object is returned.
         *
         * @param delta the delta
         * @return the updated instance
         */
        public CheckIntervalData increment(int delta) {
            return (delta != 0) ? new CheckIntervalData(getEventCount() + delta,
                    getCheckIntervalStart()) : this;
        }
    }

    /**
     * Internally used class for executing check logic based on the current state of the
     * circuit breaker. Having this logic extracted into special classes avoids complex
     * if-then-else cascades.
     */
    private abstract static class StateStrategy {
        /**
         * Returns a flag whether the end of the current check interval is reached.
         *
         * @param breaker the {@code CircuitBreaker}
         * @param currentData the current state object
         * @param now the current time
         * @return a flag whether the end of the current check interval is reached
         */
        public boolean isCheckIntervalFinished(TimedCircuitBreaker breaker,
                CheckIntervalData currentData, long now) {
            return now - currentData.getCheckIntervalStart() > fetchCheckInterval(breaker);
        }

        /**
         * Checks whether the specified {@code CheckIntervalData} objects indicate that a
         * state transition should occur. Here the logic which checks for thresholds
         * depending on the current state is implemented.
         *
         * @param breaker the {@code CircuitBreaker}
         * @param currentData the current {@code CheckIntervalData} object
         * @param nextData the updated {@code CheckIntervalData} object
         * @return a flag whether a state transition should be performed
         */
        public abstract boolean isStateTransition(TimedCircuitBreaker breaker,
                CheckIntervalData currentData, CheckIntervalData nextData);

        /**
         * Obtains the check interval to applied for the represented state from the given
         * {@code CircuitBreaker}.
         *
         * @param breaker the {@code CircuitBreaker}
         * @return the check interval to be applied
         */
        protected abstract long fetchCheckInterval(TimedCircuitBreaker breaker);
    }

    /**
     * A specialized {@code StateStrategy} implementation for the state closed.
     */
    private static class StateStrategyClosed extends StateStrategy {

        @Override
        public boolean isStateTransition(TimedCircuitBreaker breaker,
                CheckIntervalData currentData, CheckIntervalData nextData) {
            return nextData.getEventCount() > breaker.getOpeningThreshold();
        }

        @Override
        protected long fetchCheckInterval(TimedCircuitBreaker breaker) {
            return breaker.getOpeningInterval();
        }
    }

    /**
     * A specialized {@code StateStrategy} implementation for the state open.
     */
    private static class StateStrategyOpen extends StateStrategy {
        @Override
        public boolean isStateTransition(TimedCircuitBreaker breaker,
                CheckIntervalData currentData, CheckIntervalData nextData) {
            return nextData.getCheckIntervalStart() != currentData
                    .getCheckIntervalStart()
                    && currentData.getEventCount() < breaker.getClosingThreshold();
        }

        @Override
        protected long fetchCheckInterval(TimedCircuitBreaker breaker) {
            return breaker.getClosingInterval();
        }
    }

}
