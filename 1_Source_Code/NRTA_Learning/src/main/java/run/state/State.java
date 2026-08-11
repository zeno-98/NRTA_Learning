package run.state;

import lombok.AllArgsConstructor;
import lombok.Getter;
import run.parallel_elements.LocationVector;
import run.parallel_elements.SynchronizedTransition;
import run.Trace;
import ta.NTA;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


@AllArgsConstructor
@Getter
public abstract class State{
    protected final LocationVector location_vec;
    protected final SynchronizedTransition last_transitions;
    protected final State last_state;

    // Returns the sequence of transitions from the initial state to the current state
    public Trace getTrace(NTA nta){
        List<SynchronizedTransition> trans_list = new LinkedList<>();
        SynchronizedTransition last_transition = this.last_transitions;
        State last_state = this.last_state;
        State current_state;
        while(last_state!=null){
            trans_list.add(last_transition);
            current_state = last_state;
            last_state = current_state.last_state;
            last_transition = current_state.last_transitions;
        }
        Collections.reverse(trans_list);
        return new Trace(nta, trans_list);
    }

}
