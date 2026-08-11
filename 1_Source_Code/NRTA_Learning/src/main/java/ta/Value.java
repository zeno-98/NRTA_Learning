package ta;

import com.microsoft.z3.*;
import exception.ValueExceptions;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public class Value implements Comparable<Value> {
    public static final int GREATER = 1;
    public static final int EQ = 0;
    public static final int LESS = -1;

    public static final Value INFINITE = new Value(new RationalNumber(),true,false);
    public static final Value MINUS_INFINITE = new Value(new RationalNumber(),false,true);
    public static final Value ZERO = new Value(new RationalNumber());
    public static final Value ONE = new Value(new RationalNumber(1,1));

    private final RationalNumber value;
    private final boolean infinite;
    private final boolean minus_infinite;


    /**
     * Creates a new Value object.
     * @param v1 the first value
     * @param v2 the second value
     * @return a new Value representing the sum
     */
    public static Value add(Value v1, Value v2){
        if (v2 == ZERO || v2.isZero()) return v1;
        if (v1 == ZERO || v1.isZero()) return v2;
        // There are 3*3=9 cases in total
        if(v1.infinite) {
            if (v2.infinite)
                return Value.INFINITE;
            else if (v2.minus_infinite)
                throw new RuntimeException(ValueExceptions.INFINITE_MINUS_INFINITE);
            else
                return Value.INFINITE;
        } else if(v1.minus_infinite){
            if (v2.infinite)
                throw new RuntimeException(ValueExceptions.INFINITE_MINUS_INFINITE);
            else if (v2.minus_infinite)
                return Value.MINUS_INFINITE;
            else
                return Value.MINUS_INFINITE;
        } else {
            if (v2.infinite)
                return Value.INFINITE;
            else if (v2.minus_infinite)
                return Value.MINUS_INFINITE;
            else
                return new Value(v1.value.add(v2.value));
        }
    }

    public static Value minus(Value v1, Value v2){
        if (v2 == ZERO || v2.isZero()) return v1;
        // There are 3*3=9 cases in total
        if(v1.infinite) {
            if (v2.infinite)
                throw new RuntimeException(ValueExceptions.INFINITE_MINUS_INFINITE);
            else if (v2.minus_infinite)
                return Value.INFINITE;
            else
                return Value.INFINITE;
        } else if(v1.minus_infinite){
            if (v2.infinite)
                return Value.MINUS_INFINITE;
            else if (v2.minus_infinite)
                return Value.INFINITE;
            else
                return Value.MINUS_INFINITE;
        } else {
            if (v2.infinite)
                return Value.MINUS_INFINITE;
            else if (v2.minus_infinite)
                return Value.INFINITE;
            else
                return new Value(v1.value.subtract(v2.value));
        }
    }

    public static Value negative(Value v1){
        if (v1 == ZERO || v1.isZero()) return ZERO;
        if(v1.infinite)
            return Value.MINUS_INFINITE;
        else if(v1.minus_infinite)
            return Value.INFINITE;
        else
            return new Value(v1.value.neg());
    }

    public Value negative(){
        return Value.negative(this);
    }

    public static Value times(Value v1, int num){
        if (num == 1) return v1;
        if (num == -1) return negative(v1);
        if(num==0)
            return Value.ZERO;
        else if(num>0){
            if(v1.infinite)
                return Value.INFINITE;
            else if(v1.minus_infinite)
                return Value.MINUS_INFINITE;
            else
                return new Value(v1.value.multiply(num));
        } else {
            if(v1.infinite)
                return Value.MINUS_INFINITE;
            else if(v1.minus_infinite)
                return Value.INFINITE;
            else
                return new Value(v1.value.multiply(num));
        }
    }

    private static int compareFinite(RationalNumber n1, RationalNumber n2, boolean rough) {
        if (rough) {
            if (n1.roughEquals(n2)) return Value.EQ;
        } else {
            if (n1.equals(n2)) return Value.EQ;
        }
        return n1.gt(n2) ? Value.GREATER : Value.LESS;
    }

    public static int compare(Value v1, Value v2){
        if(v1.infinite){
            if(v2.infinite) return Value.EQ;
            else return Value.GREATER;
        } else if(v1.minus_infinite) {
            if(v2.minus_infinite) return Value.EQ;
            else return Value.LESS;
        } else {
            if(v2.infinite) return Value.LESS;
            else if(v2.minus_infinite) return Value.GREATER;
            return compareFinite(v1.value, v2.value, false);
        }
    }

    public static int roughCompare(Value v1, Value v2){
        if(v1.infinite){
            if(v2.infinite) return Value.EQ;
            else return Value.GREATER;
        } else if(v1.minus_infinite) {
            if(v2.minus_infinite) return Value.EQ;
            else return Value.LESS;
        } else {
            if(v2.infinite) return Value.LESS;
            else if(v2.minus_infinite) return Value.GREATER;
            return compareFinite(v1.value, v2.value, true);
        }
    }

    public static Value min(Value v1, Value v2){
        if(Value.gt(v1, v2))
            return v2;
        else
            return v1;
    }

    public static boolean gt(Value v1, Value v2){
        return Value.compare(v1, v2)==Value.GREATER;
    }

    public boolean gt(Value other){
        return Value.gt(this, other);
    }

    public static boolean ge(Value v1, Value v2){
        int outcome = Value.compare(v1, v2);
        return outcome == Value.GREATER || outcome == Value.EQ;
    }

    public boolean ge(Value other){
        return Value.ge(this, other);
    }

    public static boolean lt(Value v1, Value v2){
        return Value.compare(v1, v2)==Value.LESS;
    }

    public boolean lt(Value other){
        return Value.lt(this, other);
    }

    public static boolean le(Value v1, Value v2){
        int outcome = Value.compare(v1, v2);
        return outcome == Value.LESS || outcome == Value.EQ;
    }

    public boolean le(Value other){
        return Value.le(this, other);
    }

    public static boolean eq(Value v1, Value v2){
        return Value.compare(v1, v2) == Value.EQ;
    }

    public boolean eq(Value other){
        return Value.eq(this, other);
    }

    public Value(RationalNumber value){
        this.value = value;
        this.infinite = false;
        this.minus_infinite = false;
    }

    public Value(int value){
        this.value = new RationalNumber(value);
        this.infinite = false;
        this.minus_infinite = false;
    }

    public Value(long value){
        this.value = new RationalNumber(value);
        this.infinite = false;
        this.minus_infinite = false;
    }

    public Value(long numerator, long denominator){
        this.value = new RationalNumber(numerator, denominator);
        this.infinite = false;
        this.minus_infinite = false;
    }

    public Value(double value){
        this.value = new RationalNumber(value);
        this.infinite = false;
        this.minus_infinite = false;
    }

    public Value(Expr<RealSort> realSortExpr){
        this.infinite = false;
        this.minus_infinite = false;
        RatNum ratNum = (RatNum) realSortExpr;
        if(ratNum.isInt())
            this.value = new RationalNumber(ratNum.getNumerator().getInt64());
        else
            this.value = new RationalNumber(ratNum.getNumerator().getInt64(), ratNum.getDenominator().getInt64());
    }

    public boolean isZero(){
        if(this.infinite || this.minus_infinite)
            return false;
        return this.value.isZero();
    }

    public boolean isInteger(){
        if(this.infinite || this.minus_infinite){
            throw new RuntimeException(ValueExceptions.OPERATE_ON_INFINITE);
        }
        return this.value.isInteger();
    }

    /**
     * Normalizes the value:
     * - If integer: keep as is
     * - If decimal: take integer part and add fracPart
     * The returned value might be the same object.
     * @param fracPart the fractional part to add
     * @return the normalized value
     */
    public Value normalization(Value fracPart){
        if(this.isInteger())
            return this;
        else
            return Value.add(this.getIntegerPart(), fracPart);
    }

    /**
     * Normalizes the value:
     * - If integer: keep as is
     * - If decimal: take integer part and add fracPart
     * The returned value might be the same object.
     * @param fracPart the fractional part to add
     * @param K the upper bound value
     * @return the normalized value
     */
    public Value normalization(Value fracPart, Value K){
        if (Value.gt(this, K))
            return Value.add(K, fracPart);
        if(this.isInteger())
            return this;
        else
            return Value.add(this.getIntegerPart(), fracPart);
    }

    public String toString(){
        if(this.infinite)
            return "∞";
        else if (this.minus_infinite)
            return "-∞";
        else
            return this.value.toString();
    }

    public Value getIntegerPart(){
        if(this.infinite || this.minus_infinite){
            throw new RuntimeException(ValueExceptions.OPERATE_ON_INFINITE);
        }
        return new Value(this.value.getIntegerPart());
    }

    public Value getFractionalPart(){
        if(this.infinite || this.minus_infinite){
            throw new RuntimeException(ValueExceptions.OPERATE_ON_INFINITE);
        }
        return new Value(this.value.getFractionalPart());
    }

    public boolean isNeg(){
        if(isMinus_infinite()) return true;
        if(isInfinite()) return false;
        return this.value.isNeg();
    }

    public RealExpr toSmt(Context ctx){
        if(this.infinite || this.minus_infinite)
            throw new RuntimeException(ValueExceptions.OPERATE_ON_INFINITE);
        else
            return this.value.getSmt(ctx);
    }

    @Override
    public int compareTo(Value o) {
        return Value.compare(this, o);
    }

    public int roughCompareTo(Value o){
        return Value.roughCompare(this, o);
    }

    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null) return false;
        if(o.getClass() != Value.class) return false;
        return Value.compare(this, (Value) o) == 0;
    }

    @Override
    public int hashCode() {
        if (this.infinite) return 31_415_926;
        if (this.minus_infinite) return -31_415_926;
        return Objects.hash(this.value, false, false);
    }

}
