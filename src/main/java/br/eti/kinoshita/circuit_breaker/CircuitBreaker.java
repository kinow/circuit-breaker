package br.eti.kinoshita.circuit_breaker;

public interface CircuitBreaker<T> {

    boolean isOpen();
    
    boolean isClose();
    
    void checkState();
    
    void close();
    
    void open();
    
    void incrementAndCheckState(T increment) throws CircuitBreakingException;
    
}
