package language.actions;

import lombok.Getter;
import lombok.Setter;
import run.ClockValuation;
import ta.Action;
import ta.Clock;

import java.util.Objects;
import java.util.Set;

public class ResetLogicalTimedAction extends LogicalTimedAction{
    @Getter
    private Set<Clock> reset_clocks;
    @Setter
    public boolean ifKnowReset = true; // Temporary use for composition learning
    public ResetLogicalTimedAction(Action action, ClockValuation clockValuation, Set<Clock> reset_clocks) {
        super(action, clockValuation);
        this.reset_clocks = reset_clocks;
    }

    public ResetLogicalTimedAction(LogicalTimedAction la, Set<Clock> reset_clocks){
        super(la.action, la.clockValuation);
        this.reset_clocks = reset_clocks;
    }

    public boolean hasReset() {
        return !reset_clocks.isEmpty();
    }

//    public ResetLogicalTimedAction(Action action, Set<Clock> clocks, Set<Clock> reset_clocks) {
//        super(action, clocks);
//        this.reset_clocks = reset_clocks;
//    }

    public LogicalTimedAction toLogicalTimedAction(){
        return (LogicalTimedAction)this;
    }

    public LogicalTimedAction toLogicalTimedAction(boolean rebuild){
        if(rebuild)
            return new LogicalTimedAction(this.action, this.clockValuation);
        else
            return (LogicalTimedAction)this;
    }

    public ClockValuation getNextClockValuation(){
        return this.clockValuation.reset(this.reset_clocks);
    }


    public String toString(){
        if (ifKnowReset) {
            return '('+action.toString()+','+clockValuation+','+reset_clocks+')';
        } else {
            return '('+action.toString()+','+clockValuation+','+reset_clocks+"?)";
        }
    }

//    public boolean regionEqual(ResetLogicalTimedAction resetLogicalTimedAction){
//        boolean outcome = super.regionEqual(resetLogicalTimedAction);
//        if(outcome){
//            if(this.reset_clocks.size() != resetLogicalTimedAction.reset_clocks.size()) return false;
//            Set<Clock> reset_clocks = new HashSet<>();
//            reset_clocks.addAll(this.reset_clocks);
//            int size = reset_clocks.size();
//            reset_clocks.addAll(resetLogicalTimedAction.reset_clocks);
//            if(reset_clocks.size()!=size) return false;
//            else return true;
//        }else{
//            return false;
//        }
//    }

    public boolean equals(Object o){
        if(!(o instanceof ResetLogicalTimedAction)) return false;
        ResetLogicalTimedAction resetLogicalTimedAction = (ResetLogicalTimedAction) o;
        if(this.action != resetLogicalTimedAction.action) return false;
        if(!this.clockValuation.equals(resetLogicalTimedAction.clockValuation)) return false;
        return this.reset_clocks.equals(resetLogicalTimedAction.reset_clocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.action, this.clockValuation, this.reset_clocks);
    }
}
