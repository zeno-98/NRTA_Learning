package ta;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.Solver;
import lombok.Getter;
import run.ClockValuation;

import java.util.Map;
import java.util.Objects;

@Getter
public class AtomConstraint implements Comparable<AtomConstraint>{
    public static final boolean LESS = false;
    public static final boolean LESS_EQ = true;


    // Constraint form: x-y<a, x-y<=b,
    // closed: < is false, <= is true
    private final Clock clock1;
    private final Clock clock2;
    private final boolean closed;
    private final Value upperbound;

    /**
     * Constructs an expression for clock > value.
     * @param clock the clock
     * @param value the value
     * @return an atom constraint representing clock > value
     */
    public static AtomConstraint generateGt(Clock clock, Value value){
        return new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS, Value.negative(value));
    }

    /**
     * Constructs an expression for clock >= value.
     * @param clock the clock
     * @param value the value
     * @return an atom constraint representing clock >= value
     */
    public static AtomConstraint generateGe(Clock clock, Value value){
        return new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS_EQ, Value.negative(value));
    }

    /**
     * Constructs an expression for clock < value.
     * @param clock the clock
     * @param value the value
     * @return an atom constraint representing clock < value
     */
    public static AtomConstraint generateLt(Clock clock, Value value){
        return new AtomConstraint(clock, Clock.getZeroClock(), AtomConstraint.LESS, value);
    }

    /**
     * Constructs an expression for clock <= value.
     * @param clock the clock
     * @param value the value
     * @return an atom constraint representing clock <= value
     */
    public static AtomConstraint generateLe(Clock clock, Value value){
        return new AtomConstraint(clock, Clock.getZeroClock(), AtomConstraint.LESS_EQ, value);
    }

    public AtomConstraint(Clock clock1, Clock clock2, boolean closed, Value upperbound){
        this.clock1 = clock1;
        this.clock2 = clock2;
        this.closed = closed;
        this.upperbound = upperbound;
    }

    public AtomConstraint(Clock clock1, Clock clock2, boolean closed, int upperbound){
        this.clock1 = clock1;
        this.clock2 = clock2;
        this.closed = closed;
        this.upperbound = new Value(upperbound);
    }

    public boolean truthValue(ClockValuation clockValuation){
        if(upperbound.isInfinite())
            return true;
        Value v1 = clockValuation.getValue(clock1);
        Value v2 = clockValuation.getValue(clock2);
        Value value = Value.minus(v1, v2);
        if(closed)
            return Value.le(value, upperbound);
        else
            return Value.lt(value, upperbound);
    }

    public String toString(){
        if(this.upperbound.isInfinite())
            return "";

        StringBuilder outcome = new StringBuilder();
        if(!clock1.isZeroClock())
            outcome.append(clock1.toString());
        if(!clock2.isZeroClock()) {
            outcome.append("-");
            outcome.append(clock2.toString());
        }
        if(closed)
            outcome.append("<=");
        else
            outcome.append("<");

        outcome.append(this.upperbound.getValue());

        return outcome.toString();
    }

    public String toStringNew(){
        if(this.upperbound.isInfinite())
            return "true";
        StringBuilder outcome = new StringBuilder();
        if(clock1.isZeroClock()){
            outcome.append(Value.negative(upperbound));
            outcome.append(closed? "<=" : "<");
            outcome.append(clock2);
        } else if(clock2.isZeroClock()){
            outcome.append(clock1);
            outcome.append(closed? "<=" : "<");
            outcome.append(upperbound);
        } else if(clock1.compareTo(clock2) > 0){
            outcome.append(Value.negative(upperbound));
            outcome.append(closed? "<=" : "<");
            outcome.append(clock2);
            outcome.append("-");
            outcome.append(clock1);
        } else {
            outcome.append(clock1);
            outcome.append("-");
            outcome.append(clock2);
            outcome.append(closed? "<=" : "<");
            outcome.append(upperbound);
        }
        return outcome.toString();
    }

    public BoolExpr toSmt(Context ctx){
        if(this.upperbound.isInfinite())
            return ctx.mkTrue();
        else if(this.upperbound.isMinus_infinite())
            return ctx.mkFalse();
        else {
            RealExpr clock1_exp = this.clock1.getSmt_exp();
            RealExpr clock2_exp = this.clock2.getSmt_exp();
            if(clock1.isZeroClock())
                clock1_exp = ctx.mkReal(0);
            if(clock2.isZeroClock())
                clock2_exp = ctx.mkReal(0);

            if(this.closed)
                return ctx.mkLe(ctx.mkSub(clock1_exp, clock2_exp),this.upperbound.toSmt(ctx));
            else
                return ctx.mkLt(ctx.mkSub(clock1_exp, clock2_exp),this.upperbound.toSmt(ctx));
        }
    }

    /**
     * Constructs constraints for each clock using the specified expressions.
     * The final results are added to the solver.
     * @param expr_map the expression map for clocks
     * @param ctx the Z3 context
     * @param slv the Z3 solver
     */
    public void generateExpr(Map<Clock, RealExpr> expr_map, Context ctx, Solver slv){
        if(this.upperbound.isInfinite())
            return;
        else if (this.upperbound.isMinus_infinite()) {
            slv.add(ctx.mkFalse());
        } else {
            RealExpr clock1_exp;
            RealExpr clock2_exp;
            if(clock1.isZeroClock())
                clock1_exp = ctx.mkReal(0);
            else
                clock1_exp = expr_map.get(clock1);
            if(clock2.isZeroClock())
                clock2_exp = ctx.mkReal(0);
            else
                clock2_exp = expr_map.get(clock2);

            if(this.closed) {
                try {
                    slv.add(ctx.mkLe(ctx.mkSub(clock1_exp, clock2_exp), this.upperbound.toSmt(ctx)));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else
                slv.add(ctx.mkLt(ctx.mkSub(clock1_exp, clock2_exp),this.upperbound.toSmt(ctx)));
        }
    }

    public BoolExpr generateExpr(Map<Clock, RealExpr> expr_map, Context ctx){
        if(this.upperbound.isInfinite())
            return ctx.mkTrue();
        else if (this.upperbound.isMinus_infinite()) {
            return ctx.mkFalse();
        } else {
            RealExpr clock1_exp;
            RealExpr clock2_exp;
            if(clock1.isZeroClock())
                clock1_exp = ctx.mkReal(0);
            else
                clock1_exp = expr_map.get(clock1);
            if(clock2.isZeroClock())
                clock2_exp = ctx.mkReal(0);
            else
                clock2_exp = expr_map.get(clock2);

            if(this.closed) {
                try {
                    return ctx.mkLe(ctx.mkSub(clock1_exp, clock2_exp), this.upperbound.toSmt(ctx));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else
                return ctx.mkLt(ctx.mkSub(clock1_exp, clock2_exp),this.upperbound.toSmt(ctx));
        }
    }


    public boolean equals(Object o){
        if (this == o) return true;
        if(!(o instanceof AtomConstraint atomConstraint))
            return false;
        return this.closed == atomConstraint.closed
                && this.clock1.equals(atomConstraint.clock1)
                && this.clock2.equals(atomConstraint.clock2)
                && this.upperbound.equals(atomConstraint.upperbound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.clock1, this.clock2, this.closed, this.upperbound);
    }

    /**
     * Used only for sorting AtomConstraints to achieve ordering within Constraint.
     * Sorting priority: clock1.id, clock2.id, closed, upperbound.
     * @param o the object to be compared
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(AtomConstraint o) {
        int cmpClock1 = this.clock1.compareTo(o.clock1);
        if(cmpClock1 != 0)
            return cmpClock1;

        int cmpClock2 = this.clock2.compareTo(o.clock2);
        if(cmpClock2 != 0)
            return cmpClock2;

        if(this.closed && !o.closed)
            return 1;
        else if (!this.closed && o.closed)
            return -1;

        return this.upperbound.compareTo(o.upperbound);
    }

    /**
     * Computes the negation of this atom constraint:
     * c1-c2 < x ===> c2-c1 <= -x
     * @return the negated atom constraint
     */
    public AtomConstraint not(){
        return new AtomConstraint(this.clock2, this.clock1, !this.closed, this.upperbound.negative());
    }

    /**
     * Replaces all clocks according to the clockMap.
     * @param clockMap the map from old clocks to new clocks
     * @return a new atom constraint with replaced clocks
     */
    public AtomConstraint replace(Map<Clock,Clock> clockMap){
        Clock new_clock1;
        Clock new_clock2;
        if(!this.clock1.isZeroClock())
            new_clock1 = clockMap.get(this.clock1);
        else
            new_clock1 = Clock.getZeroClock();

        if(!this.clock2.isZeroClock())
            new_clock2 = clockMap.get(this.clock2);
        else
            new_clock2 = Clock.getZeroClock();

        return new AtomConstraint(new_clock1,new_clock2,this.closed,this.upperbound);
    }
}
