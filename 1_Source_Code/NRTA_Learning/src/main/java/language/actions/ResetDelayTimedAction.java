package language.actions;

import lombok.Getter;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.Set;

@Getter
public class ResetDelayTimedAction extends DelayTimedAction{
    private final Set<Clock> reset_clocks;
    public ResetDelayTimedAction(Action action, Value delay_time, Set<Clock> reset_clocks) {
        super(action, delay_time);
        this.reset_clocks = reset_clocks;
    }

    public DelayTimedAction toDelayTimedAction(){
        return (DelayTimedAction)this;
    }

    public String toString(){
        return '('+action.toString()+','+delay_time+','+reset_clocks+')';
    }
}
