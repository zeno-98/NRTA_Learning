package ta;

import com.microsoft.z3.*;
import run.ClockValuation;

import java.util.*;

public class Constraint {
    public static final Constraint TRUE = new TrueConstraint();

    private final Set<AtomConstraint> atomConstraints;
    private final Set<Clock> relatedClocksSet; // Does not include zero clock

    public Set<AtomConstraint> getAtomConstraints() {
        return Collections.unmodifiableSet(this.atomConstraints);
    }

    public Set<Clock> getRelatedClocks() {
        return Collections.unmodifiableSet(this.relatedClocksSet);
    }

    private static final class TrueConstraint extends Constraint {
        private TrueConstraint() {
            super();
        }

        @Override
        public void addAll(Constraint o) {
            throw new UnsupportedOperationException("Constraint.TRUE is immutable");
        }

        @Override
        public Constraint addAtomConstraint(AtomConstraint atomConstraint) {
            throw new UnsupportedOperationException("Constraint.TRUE is immutable");
        }

        @Override
        public Constraint copy() {
            return this;
        }
    }

    /**
     * Does not involve a solver!
     * Combines multiple constraints together to get a larger constraint.
     * Computes the intersection of convex polyhedra.
     * @param c1 the first constraint
     * @param c2 the second constraint
     * @param cs additional constraints
     * @return the combined constraint
     */
    public static Constraint AND(Constraint c1, Constraint c2, Constraint... cs){
        Set<AtomConstraint> new_atomConstraints = new TreeSet<>();
        Set<Clock> new_related_clocks = new TreeSet<>();
        new_atomConstraints.addAll(c1.atomConstraints);
        new_atomConstraints.addAll(c2.atomConstraints);
        new_related_clocks.addAll(c1.relatedClocksSet);
        new_related_clocks.addAll(c2.relatedClocksSet);
        for(Constraint c : cs) {
            new_atomConstraints.addAll(c.atomConstraints);
            new_related_clocks.addAll(c.relatedClocksSet);
        }
        Constraint outcome = new Constraint();
        outcome.atomConstraints.addAll(new_atomConstraints);
        outcome.relatedClocksSet.addAll(new_related_clocks);
        return outcome;
    }

    public Constraint(){
        this.atomConstraints = new TreeSet<>();
        this.relatedClocksSet = new TreeSet<>();
    }

    public Constraint(Set<AtomConstraint> atomConstraints){
        this.atomConstraints = new TreeSet<>();
        this.relatedClocksSet = new TreeSet<>();
        for(AtomConstraint atomConstraint : atomConstraints){
            this.atomConstraints.add(atomConstraint);
            Clock clock1 = atomConstraint.getClock1();
            Clock clock2 = atomConstraint.getClock2();
            if(!clock1.isZeroClock())
                this.relatedClocksSet.add(clock1);
            if(!clock2.isZeroClock())
                this.relatedClocksSet.add(clock2);
        }
    }

    public Constraint(AtomConstraint... atomConstraints){
        this.atomConstraints = new TreeSet<>();
        this.relatedClocksSet = new TreeSet<>();
        for(AtomConstraint atomConstraint : atomConstraints){
            this.atomConstraints.add(atomConstraint);
            Clock clock1 = atomConstraint.getClock1();
            Clock clock2 = atomConstraint.getClock2();
            if(!clock1.isZeroClock())
                this.relatedClocksSet.add(clock1);
            if(!clock2.isZeroClock())
                this.relatedClocksSet.add(clock2);
        }
    }

    public boolean truthValue(ClockValuation clockValuation){
        for(AtomConstraint atomConstraint : atomConstraints){
            if(!atomConstraint.truthValue(clockValuation))
                return false;
        }
        return true;
    }

    public boolean isLogicalEmpty(Map<Clock, RealExpr> expr_map, Context ctx){
        if(this.atomConstraints.isEmpty())
            return false;
        Solver slv = ctx.mkSolver();
        for(AtomConstraint atomConstraint : this.atomConstraints){
            atomConstraint.generateExpr(expr_map, ctx, slv);
        }
        return slv.check() == Status.UNSATISFIABLE;
    }

    public void addAll(Constraint o){
        for(AtomConstraint atom : o.getAtomConstraints()){
            this.addAtomConstraint(atom);
        }
    }

    public Constraint addAtomConstraint(AtomConstraint atomConstraint){
        this.atomConstraints.add(atomConstraint);
        Clock clock1 = atomConstraint.getClock1();
        Clock clock2 = atomConstraint.getClock2();
        if(!clock1.isZeroClock())
            this.relatedClocksSet.add(clock1);
        if(!clock2.isZeroClock())
            this.relatedClocksSet.add(clock2);
        return this;
    }

    /**
     * Constructs constraints for each clock using the specified expressions.
     * The final results are added to the solver.
     * @param expr_map the expression map for clocks
     * @param ctx the Z3 context
     * @param slv the Z3 solver
     */
    public void generateExpr(Map<Clock, RealExpr> expr_map, Context ctx, Solver slv){
        if(!this.isEmpty()) {
            for(AtomConstraint atomConstraint : this.atomConstraints){
                atomConstraint.generateExpr(expr_map, ctx, slv);
            }
        }
    }

    public BoolExpr generateExpr(Map<Clock, RealExpr> expr_map, Context ctx){
        if(this.isEmpty()) {
            return ctx.mkTrue();
        } else if (this.atomConstraints.size() == 1){
            for(AtomConstraint atomConstraint : this.atomConstraints){
                return atomConstraint.generateExpr(expr_map, ctx);
            }
            assert false;
            return null;
        } else {
            BoolExpr[] outcome = new BoolExpr[this.atomConstraints.size()];
            int i = 0;
            for(AtomConstraint atomConstraint : this.atomConstraints){
                outcome[i] = atomConstraint.generateExpr(expr_map, ctx);
                i++;
            }
            return ctx.mkAnd(outcome);
        }
    }

    /**
     * Returns a simplified string representation by default.
     * @return the string representation
     */
    public String toString(){
        return toStringSimply();
    }

    public String toStringPlant(){
        StringBuilder outcome = new StringBuilder();
        int num = 0;
        for(AtomConstraint atomConstraint : atomConstraints){
            if(num>0)
                outcome.append(",");
            outcome.append(atomConstraint.toString());
            num++;
        }
        if(num == 0)
            return "true";
        return '{' + outcome.toString() + '}';
    }

    /**
     * Returns a simplified string representation.
     * Requires the constraint to be in simplified form.
     * @return the simplified string representation
     */
    public String toStringSimply(){
        if(this.atomConstraints.isEmpty())
            return "true";

        List<Clock> clockList = new LinkedList<>();
        clockList.add(Clock.getZeroClock());
        List<Clock> nonZeroClocks = new ArrayList<>(this.relatedClocksSet);
        Collections.sort(nonZeroClocks);
        clockList.addAll(nonZeroClocks);
        return this.toStringSimplyWithClockOrder(clockList);
    }

    public String toStringDisplay(){
        String intervalDisplay = this.tryBuildSingleClockIntervalDisplay();
        if (intervalDisplay != null) {
            return intervalDisplay;
        }

        if(this.atomConstraints.isEmpty())
            return "true";

        List<Clock> clockList = new LinkedList<>();
        clockList.add(Clock.getZeroClock());
        List<Clock> nonZeroClocks = new ArrayList<>(this.relatedClocksSet);
        Collections.sort(nonZeroClocks);
        clockList.addAll(nonZeroClocks);
        return this.toStringSimplyWithClockOrder(clockList);
    }

    private String tryBuildSingleClockIntervalDisplay() {
        if (this.atomConstraints.isEmpty()) {
            return "[0,+)";
        }

        Clock clock = null;
        Value lower = null;
        boolean lowerClosed = false;
        Value upper = null;
        boolean upperClosed = false;

        for (AtomConstraint atom : this.atomConstraints) {
            Clock c1 = atom.getClock1();
            Clock c2 = atom.getClock2();

            if (c1.isZeroClock() && !c2.isZeroClock()) {
                Clock currentClock = c2;
                Value candidateLower = Value.negative(atom.getUpperbound());
                boolean candidateLowerClosed = atom.isClosed();
                if (clock == null) {
                    clock = currentClock;
                } else if (!clock.equals(currentClock)) {
                    return null;
                }
                if (lower == null || Value.gt(candidateLower, lower)) {
                    lower = candidateLower;
                    lowerClosed = candidateLowerClosed;
                } else if (Value.eq(candidateLower, lower)) {
                    lowerClosed = lowerClosed && candidateLowerClosed;
                }
            } else if (!c1.isZeroClock() && c2.isZeroClock()) {
                Clock currentClock = c1;
                Value candidateUpper = atom.getUpperbound();
                boolean candidateUpperClosed = atom.isClosed();
                if (clock == null) {
                    clock = currentClock;
                } else if (!clock.equals(currentClock)) {
                    return null;
                }
                if (upper == null || Value.lt(candidateUpper, upper)) {
                    upper = candidateUpper;
                    upperClosed = candidateUpperClosed;
                } else if (Value.eq(candidateUpper, upper)) {
                    upperClosed = upperClosed && candidateUpperClosed;
                }
            } else {
                // Not a unary bound on a single clock.
                return null;
            }
        }

        if (clock == null) {
            return null;
        }

        String leftBracket = lower == null ? "(" : (lowerClosed ? "[" : "(");
        String rightBracket = upper == null ? ")" : (upperClosed ? "]" : ")");
        String lowerText = lower == null ? "-∞" : this.formatGuardValue(lower);
        String upperText = upper == null ? "+" : this.formatGuardValue(upper);
        return leftBracket + lowerText + "," + upperText + rightBracket;
    }

    private String formatGuardValue(Value value) {
        String text = value.toString();
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private String toStringSimplyWithClockOrder(List<Clock> clockList){
        int size = clockList.size();

        Map<Clock, Integer> indexMap = new HashMap<>();
        for(int i=0;i<size;i++){
            indexMap.put(clockList.get(i), i);
        }

        boolean[][] matrix_B = new boolean[size][size];     // Constraint matrix
        Value[][] matrix_U = new Value[size][size];         // Upper bound matrix

        // Initialize with empty values
        for(int i=0;i<size;i++){
            for(int j=0;j<size;j++){
                if(i==j){
                    matrix_B[i][j] = AtomConstraint.LESS_EQ;
                    matrix_U[i][j] = Value.ZERO;
                } else {
                    matrix_B[i][j] = AtomConstraint.LESS;
                    matrix_U[i][j] = Value.INFINITE;
                }
            }
        }
        // Put existing constraints into the matrix
        for(AtomConstraint atom : this.atomConstraints){
            int i = indexMap.get(atom.getClock1());
            int j = indexMap.get(atom.getClock2());
            boolean closed = atom.isClosed();
            Value upperbound = atom.getUpperbound();
            int compare = upperbound.compareTo(matrix_U[i][j]);
            if(compare == Value.LESS){
                matrix_B[i][j] = closed;
                matrix_U[i][j] = upperbound;
            }else if(compare == Value.EQ) {
                matrix_B[i][j] = matrix_B[i][j] && closed;
            }
        }

        StringBuilder outcome = new StringBuilder();
        boolean flag = false;
        for(int i=1;i<size;i++){
            boolean left = false;
            if(!matrix_U[0][i].isInfinite() && !(matrix_U[0][i].isZero() && matrix_B[0][i])){
                left = true;
                if(flag)
                    outcome.append(",");
                flag = true;
                outcome.append(Value.negative(matrix_U[0][i]));
                outcome.append(matrix_B[0][i] ? "<=" : "<");
                outcome.append(clockList.get(i));
            }
            if(!matrix_U[i][0].isInfinite()){
                if(flag && !left)
                    outcome.append(",");
                flag = true;
                if(!left)
                    outcome.append(clockList.get(i));
                outcome.append(matrix_B[i][0]? "<=" : "<");
                outcome.append(matrix_U[i][0]);
            }
        }

        for(int i=1;i<size;i++){
            for(int j=i+1;j<size;j++){
                boolean left = false;
                if(!matrix_U[j][i].isInfinite()){
                    left = true;
                    if(flag)
                        outcome.append(",");
                    flag = true;
                    outcome.append(Value.negative(matrix_U[j][i]));
                    outcome.append(matrix_B[j][i]? "<=" : "<");
                    outcome.append(clockList.get(i)).append("-").append(clockList.get(j));
                }
                if(!matrix_U[i][j].isInfinite()){
                    if(flag && !left)
                        outcome.append(",");
                    flag = true;
                    if(!left)
                        outcome.append(clockList.get(i)).append("-").append(clockList.get(j));
                    outcome.append(matrix_B[i][j]? "<=" : "<");
                    outcome.append(matrix_U[i][j]);
                }
            }
        }
        String exp = outcome.toString();
        if(exp.isEmpty())
            return "true";
        else
            return "{" + exp.replaceAll("\\.0+", "") + "}";
    }

    public boolean isEmpty(){
        return this.atomConstraints.isEmpty();
    }

    public BoolExpr toSmt(Context ctx){
        if(this.isEmpty())
            return ctx.mkTrue();
        else if (this.atomConstraints.size()==1) {
            for(AtomConstraint atomConstraint : this.atomConstraints)
                return atomConstraint.toSmt(ctx);
        } else {
            BoolExpr[] temp = new BoolExpr[this.atomConstraints.size()];
            int i = 0;
            for(AtomConstraint atomConstraint : this.atomConstraints){
                temp[i] = atomConstraint.toSmt(ctx);
                i++;
            }
            return ctx.mkAnd(temp);
        }
        // unreachable here!
        return null;
    }

    /**
     * Checks logical equivalence.
     * @param ctx the Z3 context
     * @param o the other constraint
     * @return true if logically equivalent, false otherwise
     */
    public boolean logicalEquals(Context ctx, Constraint o){
        return false;
    }

    /**
     * Performs verbatim comparison for equivalence, which is more strict.
     * This seems appropriate for region equivalence, as constraints in regions have strict forms.
     * @param o the other constraint
     * @return true if verbatim equal, false otherwise
     */
    public boolean verbatimEquals(Constraint o){
        if(this.isEmpty() && o.isEmpty())
            return true;
        if(this.atomConstraints.size() != o.atomConstraints.size())
            return false;
        for(AtomConstraint atomConstraint : this.atomConstraints)
            if(!o.atomConstraints.contains(atomConstraint))
                return false;
        return true;
    }

    /**
     * Creates a copy. The atom constraint set is new, but each atom constraint object is the same.
     * @return a copy of this constraint
     */
    public Constraint copy(){
        return new Constraint(this.atomConstraints);
    }

    /**
     * Determines if the constraint is satisfiable (not empty).
     * @param ctx the Z3 context
     * @return true if satisfiable, false otherwise
     */
    public boolean satViaSMT(Context ctx){
        if(this.atomConstraints.isEmpty())
            return true;

        BoolExpr[] boolExprs = new BoolExpr[this.atomConstraints.size()];
        int i = 0;
        for(AtomConstraint atomConstraint : this.atomConstraints){
            BoolExpr expr = atomConstraint.toSmt(ctx);
            boolExprs[i] = expr;
            i++;
        }
        Solver slv = ctx.mkSolver();
        if(boolExprs.length == 1)
            slv.add(boolExprs[0]);
        else
            slv.add(ctx.mkAnd(boolExprs));

        BoolExpr[] clockExprs = new BoolExpr[this.relatedClocksSet.size()];
        i = 0;
        for(Clock clock : this.relatedClocksSet){
            clockExprs[i] = ctx.mkGe(clock.getSmt_exp(), ctx.mkReal(0));
            i++;
        }
        if(clockExprs.length == 1)
            slv.add(clockExprs[0]);
        else
            slv.add(ctx.mkAnd(clockExprs));

        return slv.check()==Status.SATISFIABLE;
    }

    /**
     * Requires a solver!
     * Computes this - constraint. Since constraint is necessarily convex, the result after subtraction may be non-convex,
     * so it needs to be composed of multiple constraints.
     * Each constraint in the result is not necessarily simplified, and may need simplification later,
     * though this does not affect correctness.
     * @param constraint the constraint to subtract
     * @param ctx the Z3 context
     * @return a Part representing the difference
     */
    public Part minusViaSMT(Constraint constraint, Context ctx){
        Part outcome = new Part();
        // We treat empty constraint as true, while empty Part is false
        if(constraint.isEmpty())
            return outcome; // Directly return false
        Constraint left_part = this.copy();
        for(AtomConstraint atomConstraint : constraint.getAtomConstraints()){
            Constraint pick_part = left_part.copy();
            pick_part.addAtomConstraint(atomConstraint.not());

            if(pick_part.satViaSMT(ctx))
                outcome.simpleAdd(pick_part);
            left_part.addAtomConstraint(atomConstraint);

            // Adding the if-break below would actually reduce solving efficiency,
            // which means calling the solver has a high cost
//            if(!left_part.sat(ctx))
//                break;
        }
        return outcome;
    }

    /**
     * The output Part is simplified.
     * @param constraint the constraint to subtract
     * @return a simplified Part representing the difference
     */
    public Part minusViaFloyd(Constraint constraint){
        Part outcome = new Part();
        // We treat empty constraint as true, while empty Part is false
        if(constraint.isEmpty())
            return outcome; // Directly return false
        Constraint left_part = this.copy();
        for(AtomConstraint atomConstraint : constraint.getAtomConstraints()){
            Constraint pick_part = left_part.copy();
            pick_part.addAtomConstraint(atomConstraint.not());
            if(pick_part.simplify())
                outcome.simpleAdd(pick_part);
            left_part.addAtomConstraint(atomConstraint);
            if(!left_part.simplify())
                break;
        }
        return outcome;
    }

    /**
     * Replaces all clocks according to the clockMap.
     * @param clockMap the map from old clocks to new clocks
     * @return a new constraint with replaced clocks
     */
    public Constraint replace(Map<Clock,Clock> clockMap){
        Constraint outcome = new Constraint();
        for(AtomConstraint atomConstraint : this.atomConstraints){
            AtomConstraint new_atomConstraint = atomConstraint.replace(clockMap);
            outcome.addAtomConstraint(new_atomConstraint);
        }
        return outcome;
    }

    /**
     * New simplification function. Considering that the number of clocks in the current TA is not large,
     * creating a new class may be costly, so a simplification function is used instead.
     * This function modifies the current object!
     * @return false if the constraint is unsatisfiable, true otherwise
     */
    public boolean simplify(){
        List<Clock> clockList = new LinkedList<>();
        Map<Clock, Integer> indexMap = new HashMap<>();
        clockList.add(Clock.getZeroClock());
        indexMap.put(Clock.getZeroClock(), 0);
        int size = 1;
        for(Clock clock : this.relatedClocksSet){
            clockList.add(clock);
            indexMap.put(clock, size);
            size++;
        }

        boolean[][] matrix_B = new boolean[size][size];     // Constraint matrix
        Value[][] matrix_U = new Value[size][size];         // Upper bound matrix

        // Initialize with empty values
        for(int i=0;i<size;i++){
            for(int j=0;j<size;j++){
                if(i==j){
                    matrix_B[i][j] = AtomConstraint.LESS_EQ;
                    matrix_U[i][j] = Value.ZERO;
                } else {
                    matrix_B[i][j] = AtomConstraint.LESS;
                    matrix_U[i][j] = Value.INFINITE;
                }
            }
        }
        // Put existing constraints into the matrix
        for(AtomConstraint atom : this.atomConstraints){
            int i = indexMap.get(atom.getClock1());
            int j = indexMap.get(atom.getClock2());
            boolean closed = atom.isClosed();
            Value upperbound = atom.getUpperbound();
            int compare = upperbound.compareTo(matrix_U[i][j]);
            if(compare == Value.LESS){
                matrix_B[i][j] = closed;
                matrix_U[i][j] = upperbound;
            } else if (compare == Value.EQ) {
                matrix_B[i][j] = matrix_B[i][j] && closed;
            }
        }
        // Floyd-Warshall algorithm
        for(int k=0;k<size;k++){
            for(int i=0;i<size;i++){
                for(int j=0;j<size;j++){
                    if(!matrix_U[i][k].isInfinite() && !matrix_U[k][j].isInfinite()){
                        Value v = Value.add(matrix_U[i][k], matrix_U[k][j]);
                        int compare = v.compareTo(matrix_U[i][j]);
                        if(compare == Value.LESS){
                            matrix_U[i][j] = v;
                            matrix_B[i][j] = matrix_B[i][k] && matrix_B[i][k];
                        } else if(compare == Value.EQ){
                            matrix_B[i][j] = matrix_B[i][j] && matrix_B[i][k] && matrix_B[i][k];
                        }
                    }
                }
            }
        }

        for(int i=0;i<size;i++){
            if(!matrix_U[i][i].isZero())
                return false;
            if(matrix_B[i][i]!=AtomConstraint.LESS_EQ)
                return false;
        }

        // Generate simplified constraints
        this.atomConstraints.clear();
        for(int i=0;i<size;i++){
            for(int j=0;j<size;j++){
                if(i!=j && !matrix_U[i][j].isInfinite()){
                    AtomConstraint atomConstraint = new AtomConstraint(clockList.get(i), clockList.get(j), matrix_B[i][j], matrix_U[i][j]);
                    this.atomConstraints.add(atomConstraint);
                }
            }
        }
        return true;
    }


    /**
     * Constructs the universal set, i.e., all clocks are >= 0.
     * @param clockSet the set of clocks
     * @return a constraint representing the universal set
     */
    public static Constraint trueConstraint(Collection<Clock> clockSet){
        Constraint outcome = new Constraint();
        Clock zero = Clock.getZeroClock();
        for(Clock clock : clockSet){
            AtomConstraint atomConstraint = new AtomConstraint(zero, clock, AtomConstraint.LESS_EQ, Value.ZERO);
            outcome.addAtomConstraint(atomConstraint);
        }
        return outcome;
    }


}
