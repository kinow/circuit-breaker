package br.eti.kinoshita.circuit_breaker;

public interface CircuitBreaker<T> {

    boolean isOpen();
    
    boolean isClosed();
    
    boolean checkState() throws CircuitBreakingException;
    
    void close();
    
    void open();
    
    boolean incrementAndCheckState(T increment) throws CircuitBreakingException;
    
}
