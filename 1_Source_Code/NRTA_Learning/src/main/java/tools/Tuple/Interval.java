package tools.Tuple;

import lombok.Getter;
import ta.Value;

import java.util.LinkedList;
import java.util.List;

public class Interval extends Pair<Value,Value>{
    public static final Interval NON_NEGATIVE_REAL = new Interval(true,Value.ZERO,Value.INFINITE,false);
    @Getter
    private boolean left_closed;
    @Getter
    private boolean right_closed;
    public Interval(boolean left_closed, Value p0, Value p1, boolean right_closed) {
        super(p0, p1);
        this.left_closed = left_closed;
        this.right_closed = right_closed;
    }
    public Interval(boolean left_closed, int p0, int p1, boolean right_closed) {
        // to be done
        super(p0==0 ? Value.ZERO : new Value(p0), new Value(p1));
        this.left_closed = left_closed;
        this.right_closed = right_closed;
    }

    public Interval(boolean left_closed, int p0, Value p1, boolean right_closed) {
        // to be done
        super(p0==0 ? Value.ZERO : new Value(p0), p1);
        this.left_closed = left_closed;
        this.right_closed = right_closed;
    }

    // Remove an interval from another interval, i.e., compute the difference
    public Pair<Boolean,List<Interval>> cut(Interval interval){
        List<Interval> outcome = new LinkedList<>();
        if(Value.gt(this.p0, interval.p1)){
            outcome.add(this.copy());
            return new Pair<>(false,outcome);
        }else if(Value.eq(this.p0, interval.p1)) {
            if(interval.right_closed && this.left_closed) {
                outcome.add(new Interval(false, this.p0, this.p1, this.right_closed));
                return new Pair<>(true, outcome);
            } else {
                outcome.add(this.copy());
                return new Pair<>(false, outcome);
            }
        }else if(Value.lt(this.p1, interval.p0)){
            outcome.add(this.copy());
            return new Pair<>(false,outcome);
        }else if(Value.eq(this.p1, interval.p0)){
            if(interval.left_closed && this.right_closed){
                outcome.add(new Interval(this.left_closed,this.p0,this.p1,false));
                return new Pair<>(true,outcome);
            } else {
                outcome.add(this.copy());
                return new Pair<>(false, outcome);
            }
        }else{
            if(Value.gt(this.p1, interval.p1)){
                outcome.add(new Interval(!interval.right_closed,interval.p1,this.p1,this.right_closed));
            }
            if(Value.lt(this.p0, interval.p0)){
                outcome.add(new Interval(this.left_closed,this.p0,interval.p0,!interval.left_closed));
            }
            return new Pair<>(true,outcome);
        }
    }
    public Interval copy(){
        return new Interval(left_closed,p0,p1,right_closed);
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        if(this.left_closed)
            sb.append('[');
        else
            sb.append('(');
        sb.append(this.p0.toString());
        sb.append(',');
        sb.append(this.p1.toString());
        if(this.right_closed)
            sb.append(']');
        else
            sb.append(')');
        return sb.toString();
    }

    public boolean vailid(){
        if(Value.gt(this.p0, this.p1))
            return false;
        if(Value.eq(this.p0, this.p1))
            return this.right_closed && this.left_closed;
        return true;
    }

}
