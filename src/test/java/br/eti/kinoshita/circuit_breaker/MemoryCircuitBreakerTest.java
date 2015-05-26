package br.eti.kinoshita.circuit_breaker;

import org.junit.Test;

public class MemoryCircuitBreakerTest {

    @Test
    public void testThreshold() {
        long threshold = 10L;
        MemoryCircuitBreaker circuit = new MemoryCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        circuit.incrementAndCheckState(1L);
    }
    
    @Test(expected=CircuitBreakingException.class)
    public void testThresholdCircuitBreakingException() {
        long threshold = 10L;
        MemoryCircuitBreaker circuit = new MemoryCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        circuit.incrementAndCheckState(2L);
    }
    
}
