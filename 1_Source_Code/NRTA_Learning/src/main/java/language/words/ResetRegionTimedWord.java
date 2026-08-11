package language.words;

import language.actions.ResetLogicalTimedAction;
import language.actions.ResetRegionTimedAction;
import run.ClockValuation;
import run.Region;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.Objects;
import java.util.Map;
import java.util.Set;

public class ResetRegionTimedWord extends TimedWord<ResetRegionTimedAction, ResetRegionTimedWord>{
    public ResetRegionTimedWord(ResetRegionTimedAction... actions){
        super(actions);
    }

    public ResetRegionTimedWord(ResetLogicalTimedWord resetLogicalTimedWord, Map<Clock, Value> Kmap){
        for(ResetLogicalTimedAction rta : resetLogicalTimedWord.timedActions) {
            Action action = rta.getAction();
            ClockValuation clockValuation = rta.getClockValuation();
            Set<Clock> reset = rta.getReset_clocks();
            Region region = new Region(clockValuation, Kmap);
            this.timedActions.add(new ResetRegionTimedAction(action, region, reset));
        }
        this.length = this.timedActions.size();
    }

    public ResetRegionTimedWord(ResetLogicalTimedWord resetLogicalTimedWord){
        for(ResetLogicalTimedAction rta : resetLogicalTimedWord.timedActions) {
            Action action = rta.getAction();
            ClockValuation clockValuation = rta.getClockValuation();
            Set<Clock> reset = rta.getReset_clocks();
            Region region = new Region(clockValuation);
            this.timedActions.add(new ResetRegionTimedAction(action, region, reset));
        }
        this.length = this.timedActions.size();
    }

    @Override
    public ResetRegionTimedWord projection(Set<Action> target_alphabet) {
        ResetRegionTimedWord projectedWord = new ResetRegionTimedWord();
        for (int i = 0; i < this.getLength(); i++) {
            ResetRegionTimedAction rra = this.getTimedAction(i);
            if (target_alphabet.contains(rra.getAction())) {
                projectedWord.attend(rra);
            }
        }
        return projectedWord;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.timedActions);
    }
}
