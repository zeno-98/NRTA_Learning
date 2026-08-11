package language.actions;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.Solver;
import lombok.Getter;
import run.ClockValuation;
import run.Region;
import ta.Action;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.Tuple.Reply;

import java.util.Objects;
import java.util.Map;

@Getter
public class RegionTimedAction extends TimedAction{
    protected final Region region;
    public RegionTimedAction(Action action, Region region) {
        super(action);
        this.region = region;
    }

    public RegionTimedAction(LogicalTimedAction la, Map<Clock, Value> K_map){
        super(la.action);
        this.region = new Region(la.clockValuation, K_map);
    }

    public RegionTimedAction(LogicalTimedAction la){
        super(la.action);
        this.region = new Region(la.clockValuation);
    }

    /*****
     * Optimized version
     * @param ctx
     * @param init_v
     * @param invariant
     * @return
     */

    public Reply<LogicalTimedAction> buildLogicalTimedAction(Context ctx, ClockValuation init_v, Constraint invariant){
        Reply<ClockValuation> reply = this.region.buildClockValuation(ctx, init_v, invariant);
        if(reply.p0())
            return new Reply<>(true, new LogicalTimedAction(this.action, reply.p1()));
        else
            return new Reply<>(false, null);
    }

    /****
     * Standard version, returns true if buildable, false otherwise
     * @param ctx
     * @param init_v
     * @return
     */
    public Reply<LogicalTimedAction> buildLogicalTimedAction(Context ctx, ClockValuation init_v){
        Reply<ClockValuation> reply = this.region.buildClockValuation(ctx, init_v, null);
        if(reply.valid())
            return new Reply<>(true, new LogicalTimedAction(this.action, reply.evidence()));
        else
            return new Reply<>(false, null);
    }


    /***
     * Use value from region construction directly, regardless of valid delay
     * @return
     */
    public LogicalTimedAction buildLogicalTimedAction() {
        return new LogicalTimedAction(this.action, this.region.getSelfClockValuation());
    }

    @Override
    public String toString() {
        return '('+this.action.toString()+","+this.region.abstractString()+')';
    }

    public boolean equals(Object o){
        if(!(o instanceof RegionTimedAction))
            return false;
        RegionTimedAction regionTimedAction = (RegionTimedAction) o;
        if(!this.action.equals(regionTimedAction.action))
            return false;
        return this.region.equals(regionTimedAction.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.action.getName(), this.region);
    }

    /****
     * Check if la is a case of this
     * @param la
     * @return
     */
    public boolean isCase(LogicalTimedAction la){
        if(!this.action.equals(la.action))
            return false;
        return this.region.contains(la.clockValuation);
    }


    public void generateExpr(Map<Clock, RealExpr> delayedExprMap, Map<Clock, RealExpr> nextExprMap, Solver slv, Context ctx){
        this.region.generateExpr(delayedExprMap, slv, ctx);
    }
}
