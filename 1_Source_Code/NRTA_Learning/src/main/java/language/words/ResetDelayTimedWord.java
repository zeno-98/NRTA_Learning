package language.words;

import language.actions.LogicalTimedAction;
import language.actions.ResetDelayTimedAction;
import language.actions.ResetLogicalTimedAction;
import run.ClockValuation;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResetDelayTimedWord extends TimedWord<ResetDelayTimedAction, ResetDelayTimedWord>{

    public ResetDelayTimedWord(){
        super();
    }
    public ResetDelayTimedWord(List<ResetDelayTimedAction> list){
        super(list);
    }

    @Override
    public ResetDelayTimedWord projection(Set<Action> target_alphabet) {
        return null;
    }

    public DelayTimedWord toDelayTimedWord(){
        DelayTimedWord outcome = new DelayTimedWord();
        for(ResetDelayTimedAction resetDelayTimedAction : this.timedActions)
            outcome.attend(resetDelayTimedAction);
        return outcome;
    }

    public LogicalTimedWord toLogicalTimedWord(Collection<Clock> clocks){
        LogicalTimedWord outcome = new LogicalTimedWord();

        ClockValuation clockValuation = new ClockValuation(clocks);
        LogicalTimedAction LogicalTimedAction;

        for(ResetDelayTimedAction resetDelayTimedAction: this.timedActions) {
            Action action = resetDelayTimedAction.getAction();
            Value delay_time = resetDelayTimedAction.getDelayTime();
            Set<Clock> reset_clocks = resetDelayTimedAction.getReset_clocks();

            clockValuation = clockValuation.resetDelay(reset_clocks, delay_time);
            LogicalTimedAction = new LogicalTimedAction(action, clockValuation);
            outcome.attend(LogicalTimedAction);
        }
        return outcome;
    }

    public ResetLogicalTimedWord toResetLogicalTimedWord(Set<Clock> clocks){
        ResetLogicalTimedWord outcome = new ResetLogicalTimedWord();

        ClockValuation clockValuation = new ClockValuation(clocks);
        ResetLogicalTimedAction resetLogicalTimedAction;
        Set<Clock> reset_clocks = new HashSet<>();
        for(ResetDelayTimedAction resetDelayTimedAction: this.timedActions) {
            Action action = resetDelayTimedAction.getAction();
            Value delay_time = resetDelayTimedAction.getDelayTime();

            clockValuation = clockValuation.resetDelay(reset_clocks, delay_time);

            reset_clocks = resetDelayTimedAction.getReset_clocks();
            // Intersection, we only need part
            reset_clocks.retainAll(clocks);

            resetLogicalTimedAction = new ResetLogicalTimedAction(action, clockValuation, reset_clocks);
            outcome.attend(resetLogicalTimedAction);

        }
        return outcome;
    }
}
