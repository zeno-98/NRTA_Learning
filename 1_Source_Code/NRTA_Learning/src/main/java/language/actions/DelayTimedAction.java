package language.actions;

import ta.Action;
import ta.Value;

public class DelayTimedAction extends TimedAction{
    protected Value delay_time;
    public DelayTimedAction(Action action, Value delay_time) {
        super(action);
        this.delay_time = delay_time;
    }

    public Value getDelayTime(){
        return this.delay_time;
    }

    public String toString(){
        return '('+this.action.toString()+','+this.delay_time+')';
    }
}
