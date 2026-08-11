package language.words;

import language.actions.DelayTimedAction;
import language.actions.RTARegionAction;
import ta.Action;
import ta.Clock;
import ta.Value;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RTARegionWord extends TimedWord<RTARegionAction, RTARegionWord> {
    private final Clock clock;

    public RTARegionWord(Clock clock) {
        super();
        this.clock = clock;
    }

    public RTARegionWord(Clock clock, List<RTARegionAction> list) {
        super(list);
        this.clock = clock;
    }

    public RTARegionWord(Clock clock, RTARegionAction... actions) {
        super(actions);
        this.clock = clock;
    }

    public Clock getClock() {
        return clock;
    }

    public RTARegionWord copy() {
        RTARegionWord copied = new RTARegionWord(this.clock);
        copied.timedActions.addAll(this.timedActions);
        copied.length = this.length;
        return copied;
    }

    public boolean regionEqual(RTARegionWord other, Map<Clock, Value> kmap) {
        return this.equals(other);
    }

    @Override
    public RTARegionWord projection(Set<Action> target_alphabet) {
        RTARegionWord outcome = new RTARegionWord(this.clock);
        for (RTARegionAction action : this.timedActions) {
            if (target_alphabet.contains(action.getAction())) {
                outcome.attend(action);
            }
        }
        return outcome;
    }

    /**
     * Build a representative delay-timed word from this zone word.
     * Rule:
     * - closed lower bound: choose lower
     * - open lower bound: choose lower + 0.5
     */
    public DelayTimedWord toRepresentativeDelayTimedWord() {
        List<DelayTimedAction> actions = new LinkedList<>();
        Value half = new Value(1, 2);
        for (RTARegionAction zoneAction : this.timedActions) {
            Value delay;
            if (zoneAction.isLowerClosed()) {
                delay = zoneAction.getLower();
            } else {
                delay = Value.add(zoneAction.getLower(), half);
            }
            actions.add(new DelayTimedAction(zoneAction.getAction(), delay));
        }
        return new DelayTimedWord(actions);
    }
}
