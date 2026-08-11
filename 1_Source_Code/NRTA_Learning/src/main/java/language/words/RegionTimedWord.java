package language.words;

import language.actions.LogicalTimedAction;
import language.actions.RegionTimedAction;
import language.actions.ResetLogicalTimedAction;
import run.ClockValuation;
import run.parallel_elements.SynchronizedTransition;
import run.state.ConcreteState;
import ta.Action;
import ta.Clock;
import ta.Value;
import run.Region;

import java.util.Set;
import java.util.Map;
import com.microsoft.z3.*;
import tools.Tuple.Reply;

public class RegionTimedWord extends TimedWord<RegionTimedAction,RegionTimedWord> {
    @Override
    public RegionTimedWord projection(Set<Action> target_alphabet) {
        return null;
    }

    public RegionTimedWord(){
        
    }

    public RegionTimedWord(LogicalTimedWord logicalTimedWord){
        for(LogicalTimedAction lta : logicalTimedWord.timedActions) {
            Action action = lta.getAction();
            ClockValuation clockValuation = lta.getClockValuation();
            Region region = new Region(clockValuation);
            this.timedActions.add(new RegionTimedAction(action, region));
        }
        this.length = this.timedActions.size();
    }

    public RegionTimedWord(LogicalTimedWord logicalTimedWord, Map<Clock, Value> Kmap){
        for(LogicalTimedAction lta : logicalTimedWord.timedActions) {
            Action action = lta.getAction();
            ClockValuation clockValuation = lta.getClockValuation();
            Region region = new Region(clockValuation, Kmap);
            this.timedActions.add(new RegionTimedAction(action, region));
        }
        this.length = this.timedActions.size();
    }

    // Build reset logical timed word from this Region word
    // Note: only call in powerful teacher scenario, as it simplifies reset information query
    public Reply<ResetLogicalTimedWord> buildResetLogicalTimedWord(Context ctx, ConcreteState init_state){
        ResetLogicalTimedWord outcome = new ResetLogicalTimedWord();
        ClockValuation init_v = init_state.getCurrent_clock_valuation();
        for(RegionTimedAction regionTimedAction : this.timedActions){
            Reply<LogicalTimedAction> reply = regionTimedAction.buildLogicalTimedAction(ctx, init_v, init_state.getLocation_vec().getInvariant());
            if(reply.p0()) {
                LogicalTimedAction la = reply.p1();
                ConcreteState next_state = init_state.oneStep(la);
                SynchronizedTransition last_trans = next_state.getLast_transitions();
                Set<Clock> reset = last_trans.getReset();
                ResetLogicalTimedAction rla = new ResetLogicalTimedAction(la, reset);
                outcome.attend(rla);
                init_state = next_state;
            } else {
                return new Reply<>(false, outcome);
            }
        }
        return new Reply<>(true, outcome);
    }

    public LogicalTimedWord buildLogicalTimedWord() {
        LogicalTimedWord outcome = new LogicalTimedWord();
        for (RegionTimedAction regionTimedAction : this.timedActions) {
            outcome.attend(regionTimedAction.buildLogicalTimedAction());
        }
        return outcome;
    }


    // May be redundant after definition in TimedWord
    public boolean equals(Object o){
        if(!(o instanceof RegionTimedWord))
            return false;
        RegionTimedWord regionTimedWord = (RegionTimedWord) o;
        if(this.length != regionTimedWord.length)
            return false;
        for(int i=0;i<this.length;i++){
            if(!this.timedActions.get(i).equals(regionTimedWord.timedActions.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
}
