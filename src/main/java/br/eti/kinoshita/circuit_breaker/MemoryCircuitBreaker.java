package br.eti.kinoshita.circuit_breaker;

import java.util.concurrent.atomic.AtomicLong;

public class MemoryCircuitBreaker extends AbstractCircuitBreaker<Long> {

    private final long bytesThreshold;
    private final AtomicLong used;
    
    public MemoryCircuitBreaker(long bytesThreshold) {
        super();
        this.used = new AtomicLong(0);
        this.bytesThreshold = bytesThreshold;
    }
    
    public long getBytes() {
        return bytesThreshold;
    }
    
    @Override
    public boolean checkState() throws CircuitBreakingException {
        return isOpen();
    }

    @Override
    public boolean incrementAndCheckState(Long increment) throws CircuitBreakingException {
        if (bytesThreshold == 0) {
            open();
        }
        
        long used = this.used.addAndGet(increment);
        if (used > bytesThreshold) {
            open();
        }
        
        return checkState();
    }

}
