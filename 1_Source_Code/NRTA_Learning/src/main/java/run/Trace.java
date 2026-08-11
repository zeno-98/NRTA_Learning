package run;

import com.microsoft.z3.*;
import exception.CheckingExceptions;
import language.actions.ResetDelayTimedAction;
import language.words.ResetDelayTimedWord;
import lombok.Getter;
import run.parallel_elements.SynchronizedTransition;
import ta.*;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;
import tools.ExtendSolver;
import learning.teachers.TeacherSetting;

import java.util.*;

@Getter
public class Trace {
    private final NTA nta;
    private final List<SynchronizedTransition> trans_list;
    private int length;
    public Trace(NTA nta){
        this.nta = nta;
        this.trans_list = new LinkedList<>();
        this.length = 0;
    }

    public Trace(NTA nta, List<SynchronizedTransition> trans_list){
        this.nta = nta;
        this.trans_list = trans_list;
        this.length = trans_list.size();
    }

    public void add(SynchronizedTransition sync_trans){
        this.trans_list.add(sync_trans);
        this.length++;
    }

    public SynchronizedTransition getSyncTrans(int i){
        return this.trans_list.get(i);
    }

    public String toString(){
        if(this.length==0) return "epsilon";
        StringBuilder sb = new StringBuilder();
        sb.append(this.trans_list.get(0).getSourceVector());
        for(SynchronizedTransition synchronizedTransition : this.trans_list){
            sb.append("--");
            sb.append(synchronizedTransition.getAction());
            sb.append("-->");
            sb.append(synchronizedTransition.getTargetVector());
        }
        return sb.toString();
    }

    public void printDetail(){
        if(this.length==0) System.out.println("epsilon");
        System.out.println(ColoredFront.dealtString("************ Position 0 ************", Color.Blue, Type.Common));
        this.trans_list.get(0).getSourceVector().printDetail();
        int i = 0;
        for(SynchronizedTransition synchronizedTransition : this.trans_list){
            System.out.println(ColoredFront.dealtString("************ Transition "+ (i+1) +" ************", Color.Green, Type.Common));
            System.out.println("Action is "+ColoredFront.dealtString(synchronizedTransition.getAction().getName(), Color.Green, Type.Common));
            synchronizedTransition.printDetail();
            System.out.println(ColoredFront.dealtString("************ Position "+ (i+1) +" ************", Color.Blue, Type.Common));
            this.trans_list.get(i).getTargetVector().printDetail();
            i++;
        }
    }

    /********
     * Make delay as small as possible
     * @param ctx
     * @return
     */
    public ResetDelayTimedWord getResetDelayTimedWord(Context ctx){
        // Solver solver = ctx.mkSolver();
        Optimize solver = ctx.mkOptimize();
        ExtendSolver.applyRandomSeed(ctx, solver);

        int length = this.length;
        List<Clock> clock_list = new ArrayList<>();
        clock_list.add(Clock.getZeroClock());
        clock_list.addAll(nta.getClockList());
        int clock_num = clock_list.size();
        Map<Clock, Integer> index_map = new HashMap<>();
        index_map.put(Clock.getZeroClock(), 0);
        for(int i=1;i<clock_num;i++){
            index_map.put(clock_list.get(i), i);
        }
        RealExpr[] delay_times = new RealExpr[length+2];
        RealExpr[][] clock_exprs = new RealExpr[length+1][clock_num];
        RealExpr[][] delayed_clock_exprs = new RealExpr[length+1][clock_num];
        SynchronizedTransition sync_trans = null;
        RealExpr zero = ctx.mkReal(0);

        ArithExpr totalDelay  = ctx.mkReal(0);

        // Construct delay_time variables
        for(int i=1;i<length+2;i++){
            delay_times[i] = ctx.mkRealConst("delay"+i);
            solver.Add(ctx.mkGe(delay_times[i], zero)); // All delay_time should be greater than or equal to 0
            totalDelay = ctx.mkAdd(totalDelay, delay_times[i]);
        }

        solver.MkMinimize(totalDelay);
        // Canonicalize among traces with the same minimum total delay.
        // Z3 may otherwise choose any model for slack delays, producing different ctx values.
        for (int i = 1; i < length + 2; i++) {
            solver.MkMinimize(delay_times[i]);
        }

        // Construct clock_value variables
        for(int i=0;i<length+1;i++){
            clock_exprs[i][0] = zero;
            for(int j=1;j<clock_num;j++){
                Clock current_clock = clock_list.get(j);
                clock_exprs[i][j] = ctx.mkRealConst(current_clock.toString()+"_"+i);
            }
        }
        // Construct clock_value+delay_time variables
        for(int i=0;i<length+1;i++){
            delayed_clock_exprs[i][0] = zero;
            for(int j=1;j<clock_num;j++)
                delayed_clock_exprs[i][j] = (RealExpr) ctx.mkAdd(clock_exprs[i][j],delay_times[i+1]);
        }
        // Construct clock constraint relationships between each step, i.e., handle reset
        for(int i=0;i<length;i++){
            Set<Clock> reset_clocks = this.trans_list.get(i).getReset();
            for(int j=1;j<clock_num;j++){
                if(reset_clocks.contains(clock_list.get(j))) // This clock is reset
                    solver.Add(ctx.mkEq(clock_exprs[i+1][j],zero));
                else
                    solver.Add(ctx.mkEq(clock_exprs[i+1][j],delayed_clock_exprs[i][j]));
            }
        }

        // Set initial values, all clocks are initialized to 0
        for(int j=1;j<clock_num;j++){
            solver.Add(ctx.mkEq(clock_exprs[0][j],zero));
        }

        // Set guard, invariant and other constraints
        for(int i=0;i<length;i++){
            sync_trans = this.trans_list.get(i);
            for(Map.Entry<TA, Transition> entry : sync_trans.getTransitions().entrySet()){
                Transition trans = entry.getValue();
                Constraint source_invariant = trans.getSource().getInvariant();
                addConstraint(clock_exprs[i],index_map,source_invariant,ctx,solver);

                Constraint guard = trans.getGuard();
                addConstraint(delayed_clock_exprs[i],index_map,guard,ctx,solver);

                Constraint target_invariant = trans.getTarget().getInvariant();
                addConstraint(clock_exprs[i+1],index_map,target_invariant,ctx,solver);
            }
        }

        if(solver.Check() == Status.UNSATISFIABLE) {// In theory, if there is a trace, there must be a corresponding word
            if (TeacherSetting.VERIFY_SHOW_UNSAT_TRACE_DETAIL) {
                this.printDetail();
            }
            throw new RuntimeException(CheckingExceptions.NON_PASSABLE_TRACE);
        } else {
            Model model = solver.getModel();
            ResetDelayTimedWord resetDelayTimedWord = new ResetDelayTimedWord();
            for(int i=0;i<length;i++){
                Value delay_time = new Value(model.eval(delay_times[i+1], true));
                sync_trans = this.getSyncTrans(i);
                ResetDelayTimedAction resetDelayTimedAction =
                        new ResetDelayTimedAction(
                                sync_trans.getAction(),
                                delay_time,
                                sync_trans.getReset()
                        );
                resetDelayTimedWord.attend(resetDelayTimedAction);
            }
            return resetDelayTimedWord;
        }
    }

    private void addConstraint(RealExpr[] clock_exprs,
                               Map<Clock, Integer> index_map,
                               Constraint Constraint,
                               Context ctx,
                               Optimize solver){
        for(AtomConstraint atomConstraint : Constraint.getAtomConstraints())
            addAtomConstraint(clock_exprs,index_map, atomConstraint,ctx,solver);
    }

    private void addAtomConstraint(RealExpr[] clock_exprs,
                                   Map<Clock, Integer> index_map,
                                   AtomConstraint atomConstraint,
                                   Context ctx,
                                   Optimize solver){
        Clock clock1 = atomConstraint.getClock1();
        Clock clock2 = atomConstraint.getClock2();
        boolean closed = atomConstraint.isClosed();
        Value upperbound = atomConstraint.getUpperbound();
        if(upperbound.isInfinite())
            return; // Less than infinity is directly true, skip
        else if(upperbound.isMinus_infinite())
            throw new RuntimeException(CheckingExceptions.MINUS_INFINITE_CONSTRAINT); // This type of constraint should not appear, throw exception
        else {
            RealExpr clock1_expr = clock_exprs[index_map.get(clock1)];
            RealExpr clock2_expr = clock_exprs[index_map.get(clock2)];
            RealExpr upperbound_expr = upperbound.getValue().getSmt(ctx);
            if(closed) {
                solver.Add(ctx.mkGe(upperbound_expr,ctx.mkSub(clock1_expr,clock2_expr)));
            } else {
                solver.Add(ctx.mkLt(ctx.mkSub(clock1_expr,clock2_expr), upperbound_expr));
            }
        }
    }

    /*******
     * Sometimes only transitions of a subset of TAs are needed, use this method for projection
     * @param nta
     * @return
     */
    public Trace projection(NTA nta){
        return null;
    }
}
