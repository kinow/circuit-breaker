package br.eti.kinoshita.circuit_breaker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MemoryCircuitBreakerTest {

    @Test
    public void testThreshold() {
        long threshold = 10L;
        MemoryCircuitBreaker circuit = new MemoryCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        circuit.incrementAndCheckState(1L);
    }
    
    @Test
    public void testThresholdCircuitBreakingException() {
        long threshold = 10L;
        MemoryCircuitBreaker circuit = new MemoryCircuitBreaker(threshold);
        assertFalse(circuit.incrementAndCheckState(9L));
        assertTrue(circuit.incrementAndCheckState(2L));
    }
    
}
