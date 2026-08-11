package ta;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import exception.ValueExceptions;

public class RationalNumber implements Comparable<RationalNumber> {
    private final long numerator;
    private final long denominator;

    public long getNumerator() {
        return this.numerator;
    }

    public long getDenominator() {
        return this.denominator;
    }

    /** Exact reduced representation; unlike toString(), this never rounds. */
    public String toFractionString() {
        if (this.denominator == 1) {
            return Long.toString(this.numerator);
        }
        return this.numerator + "/" + this.denominator;
    }
    public RationalNumber(){
        this.numerator = 0;
        this.denominator = 1;
    }

    public RationalNumber(long num){
        this.numerator = num;
        this.denominator = 1;
    }
    // Constructor - accepts a double precision floating point parameter
    public RationalNumber(double value) {
        String valueStr = Double.toString(value);
        long decimalPlaces = valueStr.length() - valueStr.indexOf('.') - 1;
        long denominator = (long) Math.pow(10, decimalPlaces);
        long numerator = (long) (value * denominator);
        // Use existing constructor to simplify the fraction
        long gcd = gcd(numerator, denominator);
        this.numerator = numerator / gcd;
        this.denominator = denominator / gcd;
    }

    // Calculate greatest common divisor
    private long gcd(long a, long b) {
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }

    // Constructor
    public RationalNumber(long numerator, long denominator) {
        if (denominator == 0) {
            throw new RuntimeException(ValueExceptions.ZERO_DENOMINATOR);
        }
        // Simplify fraction
        long gcd = gcd(numerator, denominator);
        numerator = numerator / gcd;
        denominator = denominator / gcd;

        // Ensure denominator is positive
        if (denominator < 0) {
            this.numerator = -numerator;
            this.denominator = -denominator;
        } else {
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }

    // Get integer part
    public RationalNumber getIntegerPart() {
        return new RationalNumber(this.numerator / this.denominator);
    }

    // Get fractional part
    public RationalNumber getFractionalPart() {
        long integerPart = this.numerator / this.denominator;
        long fractionalNumerator = this.numerator - (integerPart * this.denominator);
        return new RationalNumber(fractionalNumerator, this.denominator);
    }

    // Addition
    public RationalNumber add(RationalNumber other) {
        long newNumerator = this.numerator * other.denominator + other.numerator * this.denominator;
        long newDenominator = this.denominator * other.denominator;
        return new RationalNumber(newNumerator, newDenominator);
    }

    // Subtraction
    public RationalNumber subtract(RationalNumber other) {
        long newNumerator = this.numerator * other.denominator - other.numerator * this.denominator;
        long newDenominator = this.denominator * other.denominator;
        return new RationalNumber(newNumerator, newDenominator);
    }

    // Multiplication
    public RationalNumber multiply(RationalNumber other) {
        long newNumerator = this.numerator * other.numerator;
        long newDenominator = this.denominator * other.denominator;
        return new RationalNumber(newNumerator, newDenominator);
    }

    // Multiplication
    public RationalNumber multiply(int num) {
        long newNumerator = this.numerator * num;
        long newDenominator = this.denominator;
        return new RationalNumber(newNumerator, newDenominator);
    }

    // Division
    public RationalNumber divide(RationalNumber other) {
        if (other.numerator == 0) {
            throw new RuntimeException(ValueExceptions.DIVIDED_BY_ZERO);
        }
        long newNumerator = this.numerator * other.denominator;
        long newDenominator = this.denominator * other.numerator;
        return new RationalNumber(newNumerator, newDenominator);
    }

    // Negation
    public RationalNumber neg(){
        long newNumerator = -this.numerator;
        long newDenominator = this.denominator;
        return new RationalNumber(newNumerator, newDenominator);
    }

    public boolean isZero(){
        return this.numerator == 0;
    }

    public boolean isNeg(){
        return this.numerator < 0;
    }

    public boolean isInteger(){
        return (this.numerator % this.denominator) == 0;
    }

    // Override compareTo method
    @Override
    public int compareTo(RationalNumber other) {
        // Compare by cross multiplication
        long lhs = this.numerator * other.denominator;
        long rhs = this.denominator * other.numerator;
        return Long.compare(lhs, rhs);
    }

    public int compareTo(int num) {
        // Compare by cross multiplication
        long rhs = this.denominator * num;
        return Long.compare(this.numerator, rhs);
    }

    // Override equals method
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RationalNumber that = (RationalNumber) obj;
        return this.compareTo(that) == 0;
    }

    public boolean roughEquals(RationalNumber value) {
        return this.getIntegerPart().equals(value.getIntegerPart());
    }

    public boolean equals(int num){
        return this.compareTo(num) == 0;
    }

    // gt method
    public boolean gt(RationalNumber other) {
        return this.compareTo(other) > 0;
    }

    public boolean gt(int num) {
        return this.compareTo(num) > 0;
    }

    // ge method
    public boolean ge(RationalNumber other) {
        return this.compareTo(other) >= 0;
    }

    public boolean ge(int num) {
        return this.compareTo(num) >= 0;
    }

    // lt method
    public boolean lt(RationalNumber other) {
        return this.compareTo(other) < 0;
    }

    public boolean lt(int num) {
        return this.compareTo(num) < 0;
    }


    // le method
    public boolean le(RationalNumber other) {
        return this.compareTo(other) <= 0;
    }

    public boolean le(int num) {
        return this.compareTo(num) <= 0;
    }

    public RealExpr getSmt(Context ctx){
        return (RealExpr) ctx.mkDiv(ctx.mkReal(this.numerator), ctx.mkReal(this.denominator));
    }


    // Override hashCode method
    @Override
    public int hashCode() {
        // Normalized hash code calculation
        return Long.hashCode(numerator) * 31 + Long.hashCode(denominator);
    }

    // Override toString method
    @Override
    public String toString() {
        return String.format("%.1f", (double) numerator / denominator);
//        return numerator + "/" + denominator;
    }

}
