package language.actions;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.Solver;
import lombok.Getter;
import run.Region;
import ta.Action;
import ta.Clock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;

@Getter
public class ResetRegionTimedAction extends RegionTimedAction {
    private final Set<Clock> reset_clocks;
    public ResetRegionTimedAction(Action action, Region region, Set<Clock> reset_clocks) {
        super(action, region);
        this.reset_clocks = reset_clocks;
    }

    public RegionTimedAction toRegionTimedAction(){
        return (RegionTimedAction) this;
    }

    @Override
    public boolean equals(Object o){
        if(!(o instanceof ResetRegionTimedAction))
            return false;
        ResetRegionTimedAction resetRegionTimedAction = (ResetRegionTimedAction) o;
        if(!this.action.equals(resetRegionTimedAction.action))
            return false;
        if(!this.reset_clocks.equals(resetRegionTimedAction.reset_clocks))
            return false;
        return this.region.equals(resetRegionTimedAction.region);
    }

    @Override
    public int hashCode() {
        List<String> orderedResetClockNames = new ArrayList<>();
        for (Clock clock : this.reset_clocks) {
            orderedResetClockNames.add(clock.getTA_name() + "." + clock.getName());
        }
        orderedResetClockNames.sort(Comparator.naturalOrder());
        return Objects.hash(this.action.getName(), this.region, orderedResetClockNames);
    }

    public void generateExpr(Map<Clock, RealExpr> delayed_expr_map, Map<Clock, RealExpr> next_expr_map, Solver slv, Context ctx){
        this.region.generateExpr(delayed_expr_map, slv, ctx);
    }
}
