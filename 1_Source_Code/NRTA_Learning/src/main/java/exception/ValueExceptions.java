package exception;

public final class ValueExceptions {
    private ValueExceptions() {}

    public static final String INFINITE_MINUS_INFINITE = "Infinity minus infinity is undefined";
    public static final String CLOCK_VALUE_NOT_FOUND = "Clock value not found";
    public static final String ACTION_HAVE_NOT_BEEN_FOUND = "Action not defined";
    public static final String ZERO_DENOMINATOR = "Denominator cannot be zero";
    public static final String DIVIDED_BY_ZERO = "Division by zero error";
    public static final String OPERATE_ON_INFINITE = "Cannot extract from infinity";
    public static final String LIST_OUT_OF_BOUND = "List out of bounds";
    public static final String UNABLE_TO_PARTITION_NULL_VALUE = "Unable to partition empty value list";
}
