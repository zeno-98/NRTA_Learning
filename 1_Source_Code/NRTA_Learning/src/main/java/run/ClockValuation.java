package run;

import exception.CheckingExceptions;
import exception.ValueExceptions;
import lombok.Getter;
import ta.*;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;

import java.util.*;

@Getter
public class ClockValuation implements Comparable<ClockValuation>, Iterable<Map.Entry<Clock, Value>>{
    private final Map<Clock,Value> clock_valuation;

    public ClockValuation(Map<Clock,Value> clock_valuation){
        this.clock_valuation = new TreeMap<>();
        this.clock_valuation.putAll(clock_valuation);
    }

    public ClockValuation(List<Clock> clocks, List<Value> values){
        assert clocks.size() == values.size();
        this.clock_valuation = new TreeMap<>();
        for(int i=0;i<clocks.size();i++){
            this.put(clocks.get(i), values.get(i));
        }
    }

    public ClockValuation(){
        this.clock_valuation = new TreeMap<>();
    }

    public ClockValuation put(Clock clock, Value value){
        this.clock_valuation.put(clock, value);
        return this;
    }

    public int size(){
        return this.clock_valuation.size();
    }

    public boolean contains(Clock c){
        return this.clock_valuation.containsKey(c);
    }

    /**
     * Clocks will be set to 0.
     * @param clocks the collection of clocks
     */
    public ClockValuation(Collection<Clock> clocks){
        this.clock_valuation = new TreeMap<>();
        for(Clock x : clocks){
            clock_valuation.put(x, Value.ZERO);
        }
    }

    public ClockValuation delay(Value delay_time){
        ClockValuation outcome = new ClockValuation();
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            outcome.put(entry.getKey(), Value.add(entry.getValue(),delay_time));
        }
        return outcome;
    }

    /**
     * Calculates next - this, i.e., how long this is delayed to reach next.
     * @param next the next clock valuation
     * @param reset the set of reset clocks
     * @return the delay time
     */
    public Value delayTime(ClockValuation next, Set<Clock> reset){
        assert !this.clock_valuation.isEmpty();
        Value outcome = null;
        boolean flag = true;
        for(Clock clock : this.getAllClocks()){
            if(reset.contains(clock)){
                if(flag) {
                    flag = false;
                    outcome = next.getValue(clock);
                } else if (!Value.eq(next.getValue(clock), outcome)) {
                    throw new RuntimeException("clock delay error");
                }
            } else {
                if(flag){
                    flag = false;
                    outcome = Value.minus(next.getValue(clock), this.getValue(clock));
                } else if (!Value.eq(outcome, Value.minus(next.getValue(clock), this.getValue(clock)))) {
                    throw new RuntimeException("clock delay error");
                }
            }
        }
        if (Value.lt(outcome, Value.ZERO)) {
            throw new RuntimeException("clock delay little than 0");
        }
        return outcome;
    }

    /**
     * Resets according to the reset clock set.
     * @param reset_clocks the set of clocks to reset
     * @return a new clock valuation after reset
     */
    public ClockValuation reset(Set<Clock> reset_clocks){
        Map<Clock,Value> clock_valuation = new HashMap<>();
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            if(reset_clocks.contains(entry.getKey()))
                clock_valuation.put(entry.getKey(),Value.ZERO);
            else
                clock_valuation.put(entry.getKey(),entry.getValue());
        }
        return new ClockValuation(clock_valuation);
    }

    public ClockValuation delayReset(Value delay_time, Set<Clock> reset_clocks){
        Map<Clock,Value> clock_valuation = new HashMap<>();
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            if(reset_clocks.contains(entry.getKey()))
                clock_valuation.put(entry.getKey(),Value.ZERO);
            else
                clock_valuation.put(entry.getKey(),Value.add(entry.getValue(), delay_time));
        }
        return new ClockValuation(clock_valuation);
    }

    public ClockValuation resetDelay(Set<Clock> reset_clocks, Value delay_time){
        Map<Clock,Value> clock_valuation = new HashMap<>();
        assert Value.ge(delay_time, Value.ZERO); // Ensure delay is greater than zero, invalid cases are handled outside
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            Value delayClockValue;
            if(reset_clocks.contains(entry.getKey())) {
                delayClockValue = delay_time;
            } else {
                delayClockValue = Value.add(entry.getValue(),delay_time);
            }
            clock_valuation.put(entry.getKey(),delayClockValue);
        }
        return new ClockValuation(clock_valuation);
    }

    public boolean equals(ClockValuation other){
        return this.equals((Object) other);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ClockValuation other)) return false;
        if(this.clock_valuation.size()!=other.clock_valuation.size())
            return false;
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            if(!other.clock_valuation.containsKey(entry.getKey()))
                return false;
            if(!Value.eq(entry.getValue(),other.clock_valuation.get(entry.getKey())))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        List<Map.Entry<Clock, Value>> orderedEntries = new ArrayList<>(this.clock_valuation.entrySet());
        orderedEntries.sort(Comparator
                .comparing((Map.Entry<Clock, Value> e) -> e.getKey().getTA_name())
                .thenComparing(e -> e.getKey().getName()));
        int hash = 1;
        for (Map.Entry<Clock, Value> entry : orderedEntries) {
            hash = 31 * hash + (entry.getKey().getTA_name() + "." + entry.getKey().getName()).hashCode();
            hash = 31 * hash + entry.getValue().hashCode();
        }
        return hash;
    }

    /**
     * Determines if other is obtained by delaying this for some time.
     * @param other the other clock valuation
     * @return true if other is a delay of this, false otherwise
     */
    public boolean delayEquals(ClockValuation other){
        if(this.clock_valuation.size()!=other.clock_valuation.size())
            return false;
        Value delay_time = null;
        boolean first = true;
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            if(!other.clock_valuation.containsKey(entry.getKey()))
                return false;
            if(first){
                delay_time = Value.minus(other.clock_valuation.get(entry.getKey()), entry.getValue());
                if(delay_time.isNeg()) return false;
                first = false;
            } else {
                Value new_delay_time = Value.minus(other.clock_valuation.get(entry.getKey()), entry.getValue());
                if (!Value.eq(delay_time, new_delay_time))
                    return false;
            }
        }
        return true;
    }


    public Set<Clock> getAllClocks(){
        return this.clock_valuation.keySet();
    }

    public Value getValue(Clock clock){
        if(clock.isZeroClock())
            return Value.ZERO;
        Value outcome = this.clock_valuation.get(clock);
        if(outcome==null){
            throw new RuntimeException(ValueExceptions.CLOCK_VALUE_NOT_FOUND);
        }
        return outcome;
    }

    public String toString(){
        String outcome = "<";
        int num = 0;
        for(Map.Entry<Clock,Value> entry : clock_valuation.entrySet()){
            if(num>0) outcome = outcome + ',';
            outcome = outcome + entry.getKey().toString() + ':' + entry.getValue();
            num++;
        }
        return outcome + '>';
    }

    public List<Pair<Clock,Boolean>> getFracList(Map<Clock,Value> K_map){
        List<Pair<Clock,Value>> frac_cache = new LinkedList<>();
        List<Pair<Clock,Boolean>> outcome = new LinkedList<>();
        if(K_map == null){
            for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
                Clock clock = entry.getKey();
                Value value = entry.getValue();
                frac_cache.add(new Pair<>(clock, value.getFractionalPart()));
            }
        } else {
            for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
                Clock clock = entry.getKey();
                Value value = entry.getValue();
                Value K = K_map.get(clock);
                if(Value.le(value,K))
                    frac_cache.add(new Pair<>(clock, value.getFractionalPart()));
            }
        }
        frac_cache.sort(Comparator.comparing(Pair::p1));
        for(int i=0;i<frac_cache.size();i++){
            if(i==frac_cache.size()-1)
                outcome.add(new Pair<>(frac_cache.get(i).p0(), null));
            else {
                Pair<Clock,Value> pair1 = frac_cache.get(i);
                Pair<Clock,Value> pair2 = frac_cache.get(i+1);
                if(Value.eq(pair1.p1(),pair2.p1()))
                    outcome.add(new Pair<>(pair1.p0(),Region.EQ));
                else
                    outcome.add(new Pair<>(pair1.p0(),Region.LESS));
            }
        }
        return outcome;
    }

    /**
     * Sorting is based on clock id, i.e., smaller clock id has higher priority.
     * Clock id = 0 is the zero clock, so id numbering starts from 1.
     * @param o the object to be compared
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(ClockValuation o) {
        if(this.clock_valuation.size() != o.clock_valuation.size())
            throw new RuntimeException(CheckingExceptions.UN_COMPARABLE_CLOCK_VALUATION);
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            Value this_value = entry.getValue();
            if(!o.contains(clock))
                throw new RuntimeException(CheckingExceptions.UN_COMPARABLE_CLOCK_VALUATION);
            Value other_value = o.getValue(clock);
            int outcome = this_value.compareTo(other_value);
            if(outcome!=0)
                return outcome;
        }
        return 0;
    }

    /**
     * Constructs the most basic constraint, i.e., only takes the lower bound.
     * Constructs U_i.
     * @return the base constraint
     */
    public Constraint toBaseConstraint(){
        Constraint outcome = new Constraint();
        Clock zero = Clock.getZeroClock();
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            boolean relation;
            if(value.isInteger())
                relation = AtomConstraint.LESS_EQ;
            else
                relation = AtomConstraint.LESS;
            AtomConstraint atomConstraint = new AtomConstraint(zero, clock, relation, value.getIntegerPart().negative());
            outcome.addAtomConstraint(atomConstraint);
        }
        return outcome;
    }


    public Constraint toRegionConstraint(Map<Clock,Value> K_map){
        Constraint outcome = new Constraint();
        List<Triplet<Value, Clock, Value>> fracList = new LinkedList<>();

        for(Map.Entry<Clock,Value> entry : this){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value k = K_map.get(clock);
            if(Value.gt(value, k)){
                outcome.addAtomConstraint(AtomConstraint.generateGt(clock, k));
            } else {
                if(value.isInteger()){
                    outcome.addAtomConstraint(AtomConstraint.generateGe(clock, value));
                    outcome.addAtomConstraint(AtomConstraint.generateLe(clock, value));
                } else {
                    Value intPart = value.getIntegerPart();
                    outcome.addAtomConstraint(AtomConstraint.generateGt(clock, intPart));
                    outcome.addAtomConstraint(AtomConstraint.generateLt(clock, Value.add(intPart, Value.ONE)));
                    fracList.add(new Triplet<>(value.getFractionalPart(), clock, intPart));
                }
            }
        }
        fracList.sort(Comparator.comparing(Triplet::p0));
        if(fracList.size()>1){
            Clock last_clock = fracList.get(0).getP1();
            Value last_int = fracList.get(0).getP2();
            Value last_frac = fracList.get(0).getP0();
            for(int i=1;i<fracList.size();i++){
                Clock current_clock = fracList.get(i).getP1();
                Value current_int = fracList.get(i).getP2();
                Value current_frac = fracList.get(i).getP0();
                if(!Value.eq(last_frac, current_frac)){ // Not equal, then it is less than
                    outcome.addAtomConstraint(
                            new AtomConstraint(
                                    last_clock,
                                    current_clock,
                                    AtomConstraint.LESS,
                                    Value.minus(last_int, current_int)));
                } else {
                    Value temp = Value.minus(last_int, current_int);
                    outcome.addAtomConstraint(
                            new AtomConstraint(
                                    last_clock,
                                    current_clock,
                                    AtomConstraint.LESS_EQ,
                                    temp));
                    outcome.addAtomConstraint(
                            new AtomConstraint(
                                    current_clock,
                                    last_clock,
                                    AtomConstraint.LESS_EQ,
                                    Value.negative(temp)));
                }
            }
        }
        return outcome;
    }


    /**
     * Constructs the most basic constraint, i.e., only takes the lower bound, given k.
     * Constructs U_i.
     * @param K_map the map from clocks to their upper bounds
     * @return the base constraint
     */
    public Constraint toBaseConstraint(Map<Clock,Value> K_map){
        Constraint outcome = new Constraint();
        Clock zero = Clock.getZeroClock();
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value K = K_map.get(clock);
            AtomConstraint atomConstraint;
            if(Value.gt(value,K)){
                atomConstraint = new AtomConstraint(zero, clock, AtomConstraint.LESS, K.negative());
            } else {
                boolean relation;
                if (value.isInteger())
                    relation = AtomConstraint.LESS_EQ;
                else
                    relation = AtomConstraint.LESS;
                atomConstraint = new AtomConstraint(zero, clock, relation, value.getIntegerPart().negative());
            }
            outcome.addAtomConstraint(atomConstraint);
        }
        return outcome;
    }

    /**
     * Constructs the universal set, i.e., all clocks are >= 0.
     * @return a constraint representing the universal set
     */
    public Constraint toTrueConstraint(){
        Constraint outcome = new Constraint();
        Clock zero = Clock.getZeroClock();
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            AtomConstraint atomConstraint = new AtomConstraint(zero, clock, AtomConstraint.LESS_EQ, Value.ZERO);
            outcome.addAtomConstraint(atomConstraint);
        }
        return outcome;
    }

    public boolean regionEqual(ClockValuation o, Map<Clock,Value> k_map){
        Region this_region = new Region(this, k_map);
        Region other_region = new Region(o, k_map);
        return this_region.equals(other_region);
    }


    public boolean regionEqual(ClockValuation o){
        Region this_region = new Region(this);
        Region other_region = new Region(o);
        return this_region.equals(other_region);
    }

    /**
     * Determines if any clock value exceeds its corresponding K.
     * @param K_map the map from clocks to their upper bounds
     * @return true if any clock value exceeds K, false otherwise
     */
    public boolean greaterThanK(Map<Clock,Value> K_map){
        for(Map.Entry<Clock,Value> entry : this.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value K = K_map.get(clock);
            if(Value.gt(value,K))
                return true;
        }
        return false;
    }

    public ClockValuation normalization(Value fracPart, Map<Clock, Value> Kmap){
        ClockValuation outcome = new ClockValuation();
        for(Map.Entry<Clock, Value> entry : this.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            outcome.put(clock, entry.getValue().normalization(fracPart, Kmap.get(clock)));
        }
        return outcome;
    }

    @Override
    public Iterator<Map.Entry<Clock, Value>> iterator() {
        return this.clock_valuation.entrySet().iterator();
    }

    public boolean isLegalDelay(ClockValuation other, Set<Clock> reset){
        Value delay = null;
        Value single_delay;
        for(Map.Entry<Clock, Value> entry : other.clock_valuation.entrySet()){
            Clock clock = entry.getKey();
            Value v_next = entry.getValue();
            if(reset.contains(clock)){
                single_delay = v_next;
            } else {
                Value v = this.clock_valuation.get(clock);
                single_delay = Value.minus(v_next, v);
            }
            if(delay == null)
                delay = single_delay;
            else{
                if(!Value.eq(delay, single_delay))
                    return false;
            }
        }
        return Value.ge(delay, Value.ZERO);
    }

    /**
     * Determines if all values are greater than or equal to 0.
     * @return true if all values are >= 0, false otherwise
     */
    public boolean geZero(){
        for(Map.Entry<Clock, Value> entry : this.clock_valuation.entrySet()){
            if(!Value.ge(entry.getValue(), Value.ZERO))
                return false;
        }
        return true;
    }

    public ClockValuation replace(Map<Clock, Clock> clockMap){
        ClockValuation outcome = new ClockValuation();
        for(Map.Entry<Clock, Value> entry : this){
            outcome.put(clockMap.get(entry.getKey()), entry.getValue());
        }
        return outcome;
    }

    private static Constraint generateA(ClockValuation v, Map<Clock,Value> K_map){
        for(Map.Entry<Clock, Value> entry : v){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value k = K_map.get(clock);
            if(Value.gt(value, k))
                return v.toRegionConstraint(K_map);
        }
        return null;
    }

}
