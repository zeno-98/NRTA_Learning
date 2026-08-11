package language.actions;

import lombok.Getter;
import run.ClockValuation;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Getter
public class LogicalTimedAction extends TimedAction{
    protected ClockValuation clockValuation;
    public LogicalTimedAction(Action action, ClockValuation clockValuation) {
        super(action);
        this.clockValuation = clockValuation;
    }

    public LogicalTimedAction(Action action, Set<Clock> clocks){
        super(action);
        this.clockValuation = new ClockValuation(clocks);
    }

    public String toString(){
        return '('+this.action.toString()+','+this.clockValuation.toString()+')';
    }

    public Value getClockValuation(Clock clock){
        return this.clockValuation.getValue(clock);
    }

    /***************
     * Compare only action and valuation, ignore reset information
     * @param logicalTimedAction
     * @return
     */
    public boolean regionEquals(LogicalTimedAction logicalTimedAction){
        if(this.action != logicalTimedAction.getAction()) return false;
        return this.clockValuation.regionEqual(logicalTimedAction.getClockValuation());
    }

    public boolean regionEquals(LogicalTimedAction logicalTimedAction, Map<Clock, Value> Kmap){
        if(Kmap == null)
            return regionEquals(logicalTimedAction);
        else {
            if (this.action != logicalTimedAction.getAction()) return false;
            return this.clockValuation.regionEqual(logicalTimedAction.getClockValuation(), Kmap);
        }
    }

    public ClockValuation getNextClockValuation(Set<Clock> reset_clocks){
        return this.clockValuation.reset(reset_clocks);
    }

    @Override
    public int hashCode(){
        return Objects.hash(new RegionTimedAction(this));
    }

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (o == null) return false;
        if (o.getClass() == LogicalTimedAction.class || o.getClass() == ResetLogicalTimedAction.class){
            LogicalTimedAction logicalTimedAction = (LogicalTimedAction) o;
            if(!this.action.equals(logicalTimedAction.getAction()))
                return false;
            return this.clockValuation.equals(logicalTimedAction.getClockValuation());
        }
        return false;
    }
}
