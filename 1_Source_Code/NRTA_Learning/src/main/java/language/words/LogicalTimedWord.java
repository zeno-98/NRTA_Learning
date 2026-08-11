package language.words;

import language.actions.LogicalTimedAction;
import run.ClockValuation;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicalTimedWord extends TimedWord<LogicalTimedAction,LogicalTimedWord>{

    public LogicalTimedWord(){
        super();
    }

    public LogicalTimedWord(List<LogicalTimedAction> list){
        super(list);
    }

    public LogicalTimedWord(LogicalTimedAction... logicalTimedActions){
        super(logicalTimedActions);
    }

    @Override
    public LogicalTimedWord projection(Set<Action> target_alphabet) {
        LogicalTimedWord outcome = new LogicalTimedWord();
        for(LogicalTimedAction logicalTimedAction : this.timedActions){
            Action action = logicalTimedAction.getAction();
            if(target_alphabet.contains(action)){
                outcome.attend(new LogicalTimedAction(action, logicalTimedAction.getClockValuation()));
            }
        }
        return outcome;
    }

    public boolean regionEqual(LogicalTimedWord logicalTimedWord){
        if(this.length != logicalTimedWord.length)
            return false;
        for(int i=0;i<this.length;i++){
            LogicalTimedAction action1 = this.getTimedAction(i);
            LogicalTimedAction action2 = logicalTimedWord.getTimedAction(i);
            if(!action1.regionEquals(action2))
                return false;
        }
        return true;
    }

    public boolean regionEqual(LogicalTimedWord logicalTimedWord, Map<Clock, Value> Kmap){
        if(this.length != logicalTimedWord.length)
            return false;
        for(int i=0;i<this.length;i++){
            LogicalTimedAction action1 = this.getTimedAction(i);
            LogicalTimedAction action2 = logicalTimedWord.getTimedAction(i);
            if(!action1.regionEquals(action2, Kmap))
                return false;
        }
        return true;
    }

    // Equality uses region equality
    public boolean equals(Object o){
        if(o == null)
            return false;
        if(!(o instanceof LogicalTimedWord))
            return false;
        return this.regionEqual((LogicalTimedWord) o);
    }

    public LogicalTimedWord copy(){
        LogicalTimedWord logicalTimedWord = new LogicalTimedWord();
        logicalTimedWord.timedActions.addAll(this.timedActions);
        logicalTimedWord.length = this.length;
        return logicalTimedWord;
    }

    public LogicalTimedWord replaceClock(Map<Clock, Clock> clockMap){
        LogicalTimedWord outcome = new LogicalTimedWord();
        for(LogicalTimedAction la : this){
            Action action = la.getAction();
            ClockValuation cv = la.getClockValuation().replace(clockMap);
            outcome.attend(new LogicalTimedAction(action, cv));
        }
        return outcome;
    }


    public ResetLogicalTimedWord toResetLogicalTimedWord(List<Set<Clock>> resetSetList) {
        assert resetSetList.size() == this.length;
        ResetLogicalTimedWord newWord = new ResetLogicalTimedWord();
        for (int i = 0; i < this.length; i++) {
            newWord.attend(this.timedActions.get(i), resetSetList.get(i));
        }
        return newWord;
    }
}
