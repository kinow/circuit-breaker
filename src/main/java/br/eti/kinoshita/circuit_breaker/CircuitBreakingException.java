package br.eti.kinoshita.circuit_breaker;

public class CircuitBreakingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CircuitBreakingException() {
        super();
        // TODO Auto-generated constructor stub
    }

    public CircuitBreakingException(String message, Throwable cause,
            boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        // TODO Auto-generated constructor stub
    }

    public CircuitBreakingException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    public CircuitBreakingException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public CircuitBreakingException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

}
