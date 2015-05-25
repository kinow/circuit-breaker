package br.eti.kinoshita.circuit_breaker;

import java.util.concurrent.atomic.AtomicLong;

public class MemoryCircuitBreaker extends AbstractCircuitBreaker<Long> {

    private long bytesThreshold = 0L;
    private AtomicLong used;
    
    public MemoryCircuitBreaker(long bytesThreshold) {
        super();
        this.used = new AtomicLong(0);
        this.bytesThreshold = bytesThreshold;
    }
    
    public long getBytes() {
        return bytesThreshold;
    }
    
    @Override
    public void checkState() {
        if (bytesThreshold == 0) {
            open();
        }
        
        long used = this.used.incrementAndGet();
        if (bytesThreshold > used) {
            open();
        }
    }

    @Override
    public void incrementAndCheckState(Long increment) throws CircuitBreakingException {
        if (bytesThreshold == 0) {
            open();
        }
        
        long used = this.used.incrementAndGet();
        used = used + increment;
        if (bytesThreshold > used) {
            open();
        }
    }

}
