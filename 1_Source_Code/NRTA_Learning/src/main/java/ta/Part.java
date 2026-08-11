package ta;

import com.microsoft.z3.*;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Part class represents a union of constraints. Since it is non-convex, the underlying structure is a union of constraints.
 */
@Getter
public class Part {
    private final List<Constraint> convex;
    public Part(){
        this.convex = new LinkedList<>();
    }

    public Part(List<Constraint> constraints){
        this.convex = new LinkedList<>();
        this.convex.addAll(constraints);
    }

    /**
     * Gets a complete partition of the universe.
     * @param clockSet the set of clocks
     * @param ctx the Z3 context
     * @return a complete partition
     */
    public static Part getAll(Set<Clock> clockSet, Context ctx){
        Constraint constraint = new Constraint();
        Clock zero = Clock.getZeroClock();
        for(Clock clock : clockSet){
            AtomConstraint atomConstraint = new AtomConstraint(zero, clock, AtomConstraint.LESS_EQ, Value.ZERO);
            constraint.addAtomConstraint(atomConstraint);
        }
        Part outcome = new Part();
        outcome.simpleAdd(constraint);
        return outcome;
    }

    public static Part getFalse(){
        return new Part();
    }

    public static boolean logicalEqual(Part A, Part B, Map<Clock, RealExpr> expr_map, Context ctx){
        BoolExpr exp_A = A.generateExpr(expr_map, ctx);
        BoolExpr exp_B = B.generateExpr(expr_map, ctx);

        Solver slv = ctx.mkSolver();
        slv.add(ctx.mkNot(ctx.mkEq(exp_A, exp_B)));

        return slv.check() == Status.UNSATISFIABLE;
    }

    public BoolExpr generateExpr(Map<Clock, RealExpr> expr_map, Context ctx){
        if(this.isEmpty()){
            return ctx.mkFalse();
        } else if (this.convex.size() == 1) {
            for(Constraint constraint : this.convex){
                return constraint.generateExpr(expr_map, ctx);
            }
            assert false;
            return null;
        } else {
            BoolExpr[] outcome = new BoolExpr[this.convex.size()];
            int i = 0;
            for(Constraint constraint : this.convex){
                outcome[i] = constraint.generateExpr(expr_map, ctx);
                i++;
            }
            return ctx.mkAnd(outcome);
        }
    }

    public Part copy(){
        Part outcome = new Part();
        outcome.convex.addAll(this.convex);
        return outcome;
    }

    public Part simpleAdd(Constraint constraint){
        this.convex.add(constraint);
        return this;
    }


    public Part simpleAdd(Part part){
        this.convex.addAll(part.convex);
        return this;
    }

    /**
     * Requires a solver!
     * Note: this minus operation modifies the current object.
     * @param constraint the constraint to subtract
     * @param ctx the Z3 context
     * @return this Part after modification
     */
    public Part simpleMinusViaSmt(Constraint constraint, Context ctx){
        int size = this.convex.size();
        for(int i=0;i<size;i++){
            // This is correct, it's 0, because it's like a POP operation, always removing from the top
            Constraint first = this.convex.remove(0);
            this.simpleAdd(first.minusViaSMT(constraint, ctx));
        }
        return this;
    }

    /**
     * Simplifies each constraint inside.
     * This operation modifies the current object.
     */
    public void simplify(){
        List<Constraint> temp = new LinkedList<>();
        for(Constraint constraint : this.convex){
            if(constraint.simplify())
                temp.add(constraint);
        }
        this.convex.clear();
        this.convex.addAll(temp);
    }

    /**
     * Computes using Floyd-Warshall algorithm.
     * Does not require the original to be simplified.
     * The result is simplified.
     * This operation modifies the current object.
     * @param constraint the constraint to subtract
     * @return this Part after modification
     */
    public Part simpleMinusViaFloyd(Constraint constraint){
        int size = this.convex.size();
        for(int i=0;i<size;i++){
            Constraint first = this.convex.remove(0);
            this.simpleAdd(first.minusViaFloyd(constraint));
        }
        return this;
    }


    /**
     * Note: this minus operation modifies the current object.
     * @param part the part to subtract
     * @param ctx the Z3 context
     * @return this Part after modification
     */
    public Part simpleMinusViaSmt(Part part, Context ctx){
        if(this == part) {
            this.convex.clear();
            return this;
        }
        for(Constraint constraint : part.convex){
            this.simpleMinusViaSmt(constraint, ctx);
        }
        return this;
    }

    /**
     * Note: this minus operation modifies the current object.
     * @param part the part to subtract
     * @return this Part after modification
     */
    public Part simpleMinusViaFloyd(Part part){
        if(this == part) {
            this.convex.clear();
            return this;
        }
        for(Constraint constraint : part.convex){
            this.simpleMinusViaFloyd(constraint);
        }
        return this;
    }


    /**
     * Performs AND operation with a convex polyhedron. This modifies the current object.
     * @param constraint the constraint to AND with
     * @param ctx the Z3 context
     * @return this Part after modification
     */
    public Part and(Constraint constraint, Context ctx){
        List<Constraint> new_convex = new LinkedList<>();
        for(Constraint cons : this.convex){
            Constraint new_cons = Constraint.AND(cons, constraint);
        }
        return this;
    }

    /**
     * Determines if this partition is complete, i.e., the union of all sub-items equals true.
     * @param clockSet the set of clocks
     * @param ctx the Z3 context
     * @return true if the partition is complete, false otherwise
     */
    public boolean isComplete(Set<Clock> clockSet, Context ctx){
        if(this.convex.isEmpty())
            return false;
        Solver slv = ctx.mkSolver();
        BoolExpr[] init = new BoolExpr[clockSet.size()];
        int i = 0;
        for(Clock clock : clockSet){
            init[i] = ctx.mkGe(clock.getSmt_exp(),ctx.mkReal(0));
            i++;
        }
        if(init.length==1)
            slv.add(init[0]);
        else
            slv.add(ctx.mkAnd(init));
        for(Constraint constraint : this.convex){
            BoolExpr not_constraint = ctx.mkNot(constraint.toSmt(ctx));
            slv.add(not_constraint);
        }
        return slv.check() != Status.SATISFIABLE;
    }

    /**
     * Determines if the intersection between sub-items is empty.
     * @param ctx the Z3 context
     * @return true if the partition is deterministic (no overlapping sub-items), false otherwise
     */
    public boolean isDeterministic(Context ctx){
        if(this.convex.size()<2)
            return true;
        BoolExpr[] expr_array = new BoolExpr[this.convex.size()];
        int i = 0;
        for(Constraint constraint : this.convex) {
            expr_array[i] = constraint.toSmt(ctx);
            i++;
        }
        for(i=0;i<this.convex.size()-1;i++){
            for(int j=i+1;j<this.convex.size();j++){
                Solver slv = ctx.mkSolver();
                slv.add(expr_array[i]);
                slv.add(expr_array[j]);
                if(slv.check() == Status.SATISFIABLE)
                    return false;
            }
        }
        return true;
    }

    public boolean isEmpty(){
        return this.convex.isEmpty();
    }

    public boolean isLogicalEmpty(Map<Clock, RealExpr> expr_map, Context ctx){
        for(Constraint constraint : this.convex){
            if(!constraint.isLogicalEmpty(expr_map, ctx))
                return false;
        }
        return true;
    }

    public boolean isLogicalEmpty(){
        for(Constraint constraint : this.convex){
            if(!constraint.simplify())
                return false;
        }
        return true;
    }

    public String toString(){
        return this.convex.toString();
    }

}
