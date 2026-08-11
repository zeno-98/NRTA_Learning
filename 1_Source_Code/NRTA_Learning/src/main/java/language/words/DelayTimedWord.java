package language.words;

import language.actions.DelayTimedAction;
import ta.Action;
import ta.Value;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DelayTimedWord extends TimedWord<DelayTimedAction,DelayTimedWord>{
    public DelayTimedWord(){
        super();
    }
    public DelayTimedWord(List<DelayTimedAction> list){
        super(list);
    }

    @Override
    public DelayTimedWord projection(Set<Action> target_alphabet) {
        DelayTimedWord outcome = new DelayTimedWord();
        Value delay = Value.ZERO;
        for(DelayTimedAction delayTimedAction : this.timedActions){
            delay = Value.add(delay, delayTimedAction.getDelayTime());
            Action action = delayTimedAction.getAction();
            if(target_alphabet.contains(action)){
                outcome.attend(new DelayTimedAction(action,delay));
                delay = Value.ZERO;
            }
        }
        return outcome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DelayTimedWord other)) return false;
        if (this.length != other.length) return false;
        for (int i = 0; i < this.length; i++) {
            DelayTimedAction a1 = this.getTimedAction(i);
            DelayTimedAction a2 = other.getTimedAction(i);
            if (!a1.getAction().equals(a2.getAction())) return false;
            if (!a1.getDelayTime().equals(a2.getDelayTime())) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (DelayTimedAction action : this.timedActions) {
            hash = 31 * hash + Objects.hash(action.getAction(), action.getDelayTime());
        }
        return hash;
    }
}
