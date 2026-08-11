package ta;

import com.microsoft.z3.RealExpr;
import lombok.Getter;
import tools.IdCounter;

import java.util.*;

@Getter
public class Clock implements Comparable<Clock>{
    private static final IdCounter id_counter = new IdCounter();
    private static final String ZERO_CLOCK_NAME = "0";
    private static final Clock ZERO_CLOCK = new Clock();

    private final int id;
    private final String TA_name;
    private final boolean zeroClock;
    private final String name;
    private final RealExpr smt_exp;

    public static Clock getZeroClock(){
        return Clock.ZERO_CLOCK;
    }

    public Clock(String name, String TA_name, RealExpr smt_exp){
        this.id = id_counter.createId();
        this.TA_name = TA_name;
        this.name = name;
        this.zeroClock = false;
        this.smt_exp = smt_exp;
    }

    private Clock(){
        this.id = id_counter.createId();
        this.TA_name = "zero";
        this.name = Clock.ZERO_CLOCK_NAME;
        this.zeroClock = true;
        // Note: we no longer treat zero clock as a variable, but as constant 0
        this.smt_exp = null;
    }

    public int hashCode(){
        return Objects.hash(this.zeroClock, this.TA_name, this.name);
    }

    public String toString(){
        if(zeroClock)
            return "zero";
        //return TA_name + "_" + name;
        return name;
    }

    public boolean isZeroClock() {
        return this.zeroClock;
    }

    @Override
    public int compareTo(Clock o) {
        int zeroCmp = Boolean.compare(this.zeroClock, o.zeroClock);
        if (zeroCmp != 0) {
            return zeroCmp;
        }
        int taCmp = this.TA_name.compareTo(o.TA_name);
        if (taCmp != 0) {
            return taCmp;
        }
        return this.name.compareTo(o.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Clock clock)) return false;
        return this.zeroClock == clock.zeroClock
                && this.TA_name.equals(clock.TA_name)
                && this.name.equals(clock.name);
    }

    public List<Set<Clock>> getResetSetList(boolean[] resetList) {
        List<Set<Clock>> resetSetList = new ArrayList<>();
        Set<Clock> resetSet = Set.of(this);
        Set<Clock> notResetSet = Set.of();
        for (boolean b : resetList) {
            resetSetList.add(b ? resetSet : notResetSet);
        }
        return resetSetList;
    }
}
