package exception;

public final class CheckingExceptions {
    private CheckingExceptions() {}

    public static final String INCOMPARABLE_LOCATION_VECTOR = "Location vectors are incomparable";
    public static final String ILLEGAL_LOCATION_VECTOR = "Invalid location vector";
    public static final String MINUS_INFINITE_CONSTRAINT = "Constraint with <-∞/<=-∞ detected";
    public static final String NON_PASSABLE_TRACE = "No corresponding word for this trace";
    public static final String UN_COMPARABLE_CLOCK_VALUATION = "Clock valuations are incomparable";
    public static final String UNSUPPORTED_EQUIVALENCE_CHECKING_METHOD = "Unsupported equivalence checking method";
}
