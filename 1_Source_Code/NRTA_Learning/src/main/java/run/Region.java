package run;

import com.microsoft.z3.*;
import lombok.Getter;
import ta.AtomConstraint;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.Tuple.Pair;
import tools.Tuple.Reply;

import java.util.*;

@Getter
public class Region {
    public static final boolean EQ = true;
    public static final boolean LESS = false;
    private final Map<Clock, Value> K_map;
    private final Map<Clock, Constraint> region_map;
    private final List<Pair<Clock,Boolean>> frac_relation_list;

    // Used to assist the getResetAndAccept function of multi-clock TTT
    @Getter
    private final ClockValuation selfClockValuation;
    // constraint_type only records constraints of the fractional part


    /**
     * Constructor when the upper bound k is known.
     * @param clockValuation the clock valuation
     * @param K_map the map from clocks to their upper bounds
     */
    public Region(ClockValuation clockValuation, Map<Clock, Value> K_map){
        this.K_map = K_map;
        this.region_map = new HashMap<>();
        this.frac_relation_list = new LinkedList<>();
        this.selfClockValuation = clockValuation;
        List<Pair<Clock,Value>> frac_cache = new LinkedList<>();
        for(Map.Entry<Clock,Value> entry : clockValuation.getClock_valuation().entrySet()){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value k = K_map.get(clock);
            Constraint constraint;
            if(Value.gt(value, k))
                constraint = this.buildConstraintGtK(clock, k);
            else {
                constraint = this.buildConstraintLeK(clock, value);
                frac_cache.add(new Pair<>(clock, value.getFractionalPart()));
            }
            this.region_map.put(clock, constraint);
        }
        frac_cache.sort(Comparator.comparing(Pair::p1));
        for(int i=0;i<frac_cache.size();i++){
            if(i==frac_cache.size()-1)
                this.frac_relation_list.add(new Pair<>(frac_cache.get(i).p0(), null));
            else {
                Pair<Clock,Value> pair1 = frac_cache.get(i);
                Pair<Clock,Value> pair2 = frac_cache.get(i+1);
                Clock clock1 = pair1.p0();
                Clock clock2 = pair2.p0();
                Value upper = Value.minus(clockValuation.getValue(clock1), clockValuation.getValue(clock2));
                if(Value.eq(pair1.p1(),pair2.p1())) {
                    this.frac_relation_list.add(new Pair<>(pair1.p0(), Region.EQ));
                } else {
                    this.frac_relation_list.add(new Pair<>(pair1.p0(), Region.LESS));
                }
            }
        }
    }

    /**
     * Constructor when the upper bound k is unknown, treats k as infinity.
     * @param clockValuation the clock valuation
     */
    public Region(ClockValuation clockValuation){
        this.K_map = null;
        this.region_map = new HashMap<>();
        this.frac_relation_list = new LinkedList<>();
        this.selfClockValuation = clockValuation;
        List<Pair<Clock,Value>> frac_cache = new LinkedList<>();
        for(Map.Entry<Clock,Value> entry : clockValuation.getClock_valuation().entrySet()){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Constraint constraint = this.buildConstraintLeK(clock, value);
            frac_cache.add(new Pair<>(clock, value.getFractionalPart()));
            this.region_map.put(clock, constraint);
        }
        frac_cache.sort(Comparator.comparing(Pair::p1));
        for(int i=0;i<frac_cache.size();i++){
            if(i==frac_cache.size()-1)
                this.frac_relation_list.add(new Pair<>(frac_cache.get(i).p0(), null));
            else {
                Pair<Clock,Value> pair1 = frac_cache.get(i);
                Pair<Clock,Value> pair2 = frac_cache.get(i+1);
                Clock clock1 = pair1.p0();
                Clock clock2 = pair2.p0();
                Value upper = Value.minus(clockValuation.getValue(clock1), clockValuation.getValue(clock2));
                if(Value.eq(pair1.p1(),pair2.p1())) {
                    this.frac_relation_list.add(new Pair<>(pair1.p0(), Region.EQ));
                } else {
                    this.frac_relation_list.add(new Pair<>(pair1.p0(), Region.LESS));
                }
            }
        }
    }


    private Constraint buildConstraintLeK(Clock clock, Value value){
        Value integer_part = value.getIntegerPart();
        if (Value.eq(integer_part, value)) {
            AtomConstraint atomConstraint1 = new AtomConstraint(clock, Clock.getZeroClock(), AtomConstraint.LESS_EQ, value);
            AtomConstraint atomConstraint2 = new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS_EQ, value.negative());
            return new Constraint(atomConstraint1, atomConstraint2);
        } else {
            AtomConstraint atomConstraint1 = new AtomConstraint(clock, Clock.getZeroClock(), AtomConstraint.LESS, Value.add(integer_part, Value.ONE));
            AtomConstraint atomConstraint2 = new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS, integer_part.negative());
            return new Constraint(atomConstraint1, atomConstraint2);
        }
    }

    private Constraint buildConstraintGtK(Clock clock, Value k){
        AtomConstraint atomConstraint = new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS, k.negative());
//
        return new Constraint(atomConstraint);
    }

    // Note that in our scenario there is actually no invariant
    public Reply<ClockValuation> buildClockValuation(Context ctx, ClockValuation init_v, Constraint init_invariant){
        RealExpr d = ctx.mkRealConst("delay_time");
        Solver slv = ctx.mkSolver();
        slv.add(ctx.mkGe(d,ctx.mkReal(0)));

        if(init_invariant != null)
            if(!init_invariant.isEmpty())
                slv.add(init_invariant.toSmt(ctx));

        for(Map.Entry<Clock,Value> entry : init_v.getClock_valuation().entrySet()){
            Clock clock = entry.getKey();
            Value init_cv = entry.getValue();
            BoolExpr time_delay = ctx.mkEq(ctx.mkAdd(init_cv.toSmt(ctx),d),clock.getSmt_exp());
            slv.add(time_delay);
            Constraint constraint = this.region_map.get(clock);
            slv.add(constraint.toSmt(ctx));
        }
        if(this.frac_relation_list.size()>1) {
            List<ArithExpr<ArithSort>> frac_parts_list = new ArrayList<>();
            Pair<Clock,Boolean> pair = this.getFrac_relation_list().get(0);
            RealExpr clock_exp = pair.p0().getSmt_exp();
            Boolean if_eq = pair.p1();
            frac_parts_list.add(ctx.mkSub(clock_exp,ctx.mkReal2Int(clock_exp)));
            for(int i=1;i<this.frac_relation_list.size();i++){
                pair = this.getFrac_relation_list().get(i);
                clock_exp = pair.p0().getSmt_exp();
                frac_parts_list.add(ctx.mkSub(clock_exp,ctx.mkReal2Int(clock_exp)));
                if(if_eq)
                    slv.add(ctx.mkEq(frac_parts_list.get(i-1),frac_parts_list.get(i)));
                else
                    slv.add(ctx.mkLt(frac_parts_list.get(i-1),frac_parts_list.get(i)));
                if_eq = pair.p1();
            }
        }
        if(slv.check()==Status.SATISFIABLE){
            Model model = slv.getModel();
            Value delay_time_value = new Value(model.eval(d,true));
            return new Reply<>(true, init_v.delay(delay_time_value));
        } else {
            return new Reply<>(false, null);
        }
    }

    public String abstractString(){
        StringBuilder sb = new StringBuilder();
        List<Clock> orderedClocks = this.getOrderedClocks();
        Set<Clock> fracClocks = new LinkedHashSet<>();
        sb.append("(B:[");
        int shown = 0;
        for (Clock clock : orderedClocks) {
            if (shown > 0) {
                sb.append("; ");
            }
            Constraint constraint = this.region_map.get(clock);
            sb.append(clock.getName()).append(":").append(constraint.toStringDisplay());
            shown++;
        }
        sb.append("], F:[");
        for (int i = 0; i < this.frac_relation_list.size(); i++) {
            Pair<Clock, Boolean> pair = this.frac_relation_list.get(i);
            Clock clock = pair.p0();
            fracClocks.add(clock);
            sb.append(clock.getName());
            if (pair.p1() == null) {
                break;
            }
            sb.append(pair.p1() ? "=" : "<");
        }
        sb.append("]");

        List<String> gtKClocks = new ArrayList<>();
        for (Clock clock : orderedClocks) {
            if (!fracClocks.contains(clock)) {
                gtKClocks.add(clock.getName());
            }
        }
        if (!gtKClocks.isEmpty()) {
            sb.append(", K:").append(gtKClocks);
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int num = 0;
        sb.append('[');
        for(Clock clock : this.getOrderedClocks()){
            if(num > 0)
                sb.append("\n ");
            sb.append(clock.getName());
            sb.append(" : ");
            sb.append(this.region_map.get(clock));
            num++;
        }
        sb.append(']');
        sb.append('\n');
        sb.append("frac: ");
        for(Pair<Clock,Boolean> pair : this.frac_relation_list){
            sb.append(pair.p0().getName());
            if(pair.p1()==null)
                break;
            if(pair.p1())
                sb.append(" = ");
            else
                sb.append(" < ");
        }
        sb.append('\n');
        return sb.toString();
    }

    public boolean equals(Object o){
        if(!(o instanceof Region))
            return false;
        Region region = (Region) o;
        if(this == region)
            return true;
        if(this.frac_relation_list.size() != region.frac_relation_list.size())
            return false;
        if(this.region_map.size() != region.region_map.size())
            return false;
        for(int i=0;i<this.frac_relation_list.size();i++)
            if(!this.frac_relation_list.get(i).equals(region.frac_relation_list.get(i)))
                return false;
        for(Clock clock : this.getOrderedClocks()){
            if(!region.region_map.containsKey(clock))
                return false;
            if(!this.region_map.get(clock).verbatimEquals(region.region_map.get(clock)))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (Clock clock : this.getOrderedClocks()) {
            hash = 31 * hash + (clock.getTA_name() + "." + clock.getName()).hashCode();
            hash = 31 * hash + this.region_map.get(clock).toString().hashCode();
        }
        hash = 31 * hash + this.frac_relation_list.hashCode();
        return hash;
    }

    /**
     * Determines if v belongs to this region.
     * @param v the clock valuation
     * @return true if v belongs to this region, false otherwise
     */
    public boolean contains(ClockValuation v){
        if(this.region_map.size() != v.size()) return false;
        for(Clock clock : this.getOrderedClocks()){
            if(!this.region_map.get(clock).truthValue(v))
                return false;
        }
        List<Pair<Clock,Boolean>> frac_list = v.getFracList(this.K_map);
        int i = 0;
        for(Pair<Clock,Boolean> pair : this.frac_relation_list){
            boolean can_found = false;
            for(;i<frac_list.size();i++){
                Pair<Clock,Boolean> current = frac_list.get(i);
                if(pair.equals(current)) {
                    can_found = true;
                    break;
                }
            }
            if(!can_found)
                return false;
        }
        return true;
    }

    public Constraint getAllConstraint(){
        Constraint outcome = getFracConstraint();
        for(Clock clock : this.getOrderedClocks()){
            outcome.addAll(this.region_map.get(clock));
        }
        return outcome;
    }

    public Constraint getFracConstraint(){
        Constraint outcome = new Constraint();
        if(this.frac_relation_list.isEmpty())
            return outcome;
        Pair<Clock,Boolean> current_pair = this.frac_relation_list.get(0);
        for(int i=0;i<this.frac_relation_list.size()-1;i++){
            Clock clock1 = current_pair.p0();
            boolean op = current_pair.p1();
            Pair<Clock,Boolean> next_pair = this.frac_relation_list.get(i+1);
            Clock clock2 = next_pair.p0();
            if(op == Region.EQ){
                AtomConstraint atomConstraint1 = new AtomConstraint(clock1, clock2, AtomConstraint.LESS_EQ, Value.ZERO);
                AtomConstraint atomConstraint2 = new AtomConstraint(clock2, clock1, AtomConstraint.LESS_EQ, Value.ZERO);
                outcome.addAtomConstraint(atomConstraint1);
                outcome.addAtomConstraint(atomConstraint2);
            } else {
                AtomConstraint atomConstraint = new AtomConstraint(clock1, clock2, AtomConstraint.LESS, Value.ZERO);
                outcome.addAtomConstraint(atomConstraint);
            }
            current_pair = next_pair;
        }
        return outcome;
    }

    /**
     * Constructs constraints for each clock using the specified expressions.
     * @param expr_map the expression map for clocks
     * @param slv the Z3 solver
     * @param ctx the Z3 context
     */
    public void generateExpr(Map<Clock, RealExpr> expr_map, Solver slv, Context ctx){
        for(Clock clock : this.getOrderedClocks()){
            this.region_map.get(clock).generateExpr(expr_map, ctx, slv);
        }
    }

    private List<Clock> getOrderedClocks() {
        List<Clock> ordered = new ArrayList<>(this.region_map.keySet());
        ordered.sort(Comparator
                .comparing(Clock::getTA_name)
                .thenComparing(Clock::getName));
        return ordered;
    }

}
