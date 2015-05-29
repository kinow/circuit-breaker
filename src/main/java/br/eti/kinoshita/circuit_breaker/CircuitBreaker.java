package br.eti.kinoshita.circuit_breaker;

public interface CircuitBreaker<T> {

    boolean isOpen();
    
    boolean isClosed();
    
    boolean checkState();
    
    void close();
    
    void open();
    
    boolean incrementAndCheckState(T increment);
    
}
