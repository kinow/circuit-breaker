package br.eti.kinoshita.circuit_breaker;

import static br.eti.kinoshita.circuit_breaker.AbstractCircuitBreaker.isOpen;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.concurrent.CircuitBreaker.CheckIntervalData;
import org.apache.commons.lang3.concurrent.CircuitBreaker.State;

public class TimedCircuitBreaker extends AbstractCircuitBreaker<Integer> {

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
    public void checkState() throws CircuitBreakingException {
        performStateCheck(0);
    }

    @Override
    public void incrementAndCheckState(Integer increment)
            throws CircuitBreakingException {
        performStateCheck(1);
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
        if (currentState.isStateTransition(this, currentData, nextData)) {
            currentState = currentState.oppositeState();
            changeStateAndStartNewCheckInterval(currentState);
        }
        return !isOpen(currentState);
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
        if (currentState.isCheckIntervalFinished(this, currentData, time)) {
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

}
