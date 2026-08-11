package run.state;

import exception.ModelExceptions;
import language.actions.LogicalTimedAction;
import lombok.Getter;
import run.ClockValuation;
import run.parallel_elements.LocationVector;
import run.parallel_elements.SynchronizedTransition;
import ta.NTA;

import java.util.*;


// This state is used to describe the state of the entire NRTA
@Getter
public class ConcreteState extends State {
    private final NTA nta;
    private final ClockValuation current_clock_valuation;

    public ConcreteState(NTA nta,
                         LocationVector locations,
                         SynchronizedTransition last_transitions,
                         State last_state,
                         ClockValuation current_clock_valuation) {
        super(locations, last_transitions, last_state);
        this.nta = nta;
        this.current_clock_valuation = current_clock_valuation;
    }

    // Currently only handles complete deterministic cases
    public ConcreteState oneStep(LogicalTimedAction la){
        List<SynchronizedTransition> out_trans = this.location_vec.outTransitions(la.getAction());
        for(SynchronizedTransition trans : out_trans){
            ClockValuation current = la.getClockValuation();
            if(trans.canPass(current)){
                ClockValuation next = current.reset(trans.getReset());
                LocationVector target = trans.getTargetVector();
                if(target.ifSatisfyInvariant(next))
                    return new ConcreteState(this.nta,target,trans,this,next);
            }
        }
        throw new RuntimeException(ModelExceptions.NOT_WELL_DEFINED_MODEL);
    }

    public String toString(){
        return '<' + this.location_vec.toString() + this.current_clock_valuation + '>';
    }

}
