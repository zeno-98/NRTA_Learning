package language.actions;

import lombok.Getter;
import run.ClockValuation;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.Map;
import java.util.Objects;

@Getter
public class RTARegionAction extends TimedAction {
    private final Clock clock;
    private final Value upper;
    private final Value lower;
    private final boolean lowerClosed;
    private final boolean upperClosed;

    public RTARegionAction(Action action, Clock clock, Value value) {
        super(action);
        this.clock = clock;
        if (value.isInteger()) {
            this.upper = value;
            this.lower = value;
            this.lowerClosed = true;
            this.upperClosed = true;
        } else {
            Value integerPart = value.getIntegerPart();
            if (value.isNeg()) {
                this.upper = integerPart;
                this.lower = Value.minus(integerPart, Value.ONE);
            } else {
                this.upper = Value.add(integerPart, Value.ONE);
                this.lower = integerPart;
            }
            this.lowerClosed = false;
            this.upperClosed = false;
        }
    }

    public RTARegionAction(Action action, Clock clock, Value lower, boolean lowerClosed, Value upper, boolean upperClosed) {
        super(action);
        if (Value.compare(lower, upper) > 0) {
            throw new IllegalArgumentException("lower must not be greater than upper");
        }
        this.clock = clock;
        this.lower = lower;
        this.lowerClosed = lowerClosed;
        this.upper = upper;
        this.upperClosed = upperClosed;
    }

    @Override
    public String toString() {
        String leftBracket = this.lowerClosed ? "[" : "(";
        String rightBracket = this.upperClosed ? "]" : ")";
        String interval = leftBracket + this.formatDisplayValue(this.lower) + "," + this.formatDisplayValue(this.upper) + rightBracket;
        return '(' + this.action.toString() + ',' + interval + ')';
    }

    private String formatDisplayValue(Value value) {
        String text = value.toString();
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    public boolean regionEquals(RTARegionAction other) {
        return this.equals(other);
    }

    public boolean regionEquals(RTARegionAction other, Map<Clock, Value> Kmap) {
        return this.equals(other);
    }

    public ClockValuation getClockValuation() {
        ClockValuation valuation = new ClockValuation();
        Value representative = this.lowerClosed ? this.lower : Value.add(this.lower, new Value(1, 2));
        valuation.put(this.clock, representative);
        return valuation;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RTARegionAction)) {
            return false;
        }
        RTARegionAction that = (RTARegionAction) o;
        return this.action.equals(that.action)
                && this.clock.equals(that.clock)
                && this.upper.equals(that.upper)
                && this.lower.equals(that.lower)
                && this.lowerClosed == that.lowerClosed
                && this.upperClosed == that.upperClosed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.action.getName(), this.clock, this.upper, this.lower, this.lowerClosed, this.upperClosed);
    }
}
