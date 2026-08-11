package run.parallel_elements;

import lombok.Getter;
import run.ClockValuation;
import ta.Action;
import ta.Clock;
import ta.TA;
import ta.Transition;

import java.util.*;

@Getter
public class SynchronizedTransition {
    private final Action action;
    private final Map<TA, Transition> transitions;
    private final Set<Clock> reset;

    public static List<SynchronizedTransition> generate(List<List<Transition>> raw, Action action){
        List<SynchronizedTransition> waiting = new LinkedList<>();
        waiting.add(new SynchronizedTransition(action, new LinkedHashMap<>()));
        for(List<Transition> item : raw){
            if(item.isEmpty())
                return new LinkedList<>();
            else if (item.size() == 1) {
                for (SynchronizedTransition w : waiting)
                    w.addTransition(item.get(0));
            } else {
                List<SynchronizedTransition> next = new LinkedList<>();
                for (Transition trans : item) {
                    for (SynchronizedTransition w : waiting) {
                        SynchronizedTransition w_ = w.copy();
                        w_.addTransition(trans);
                        next.add(w_);
                    }
                }
                waiting = next;
            }
        }
        if(waiting.size()==1){
            SynchronizedTransition sync = waiting.get(0);
            for(Transition trans : sync.getTransitions().values())
                if(!trans.getAction().isEpsilon())
                    return waiting;
            return new LinkedList<>();
        }
        return waiting;
    }

    public SynchronizedTransition(Action action, Map<TA, Transition> transitions){
        this.action = action;
        this.transitions = transitions;
        this.reset = new LinkedHashSet<>();
        for(Map.Entry<TA,Transition> entry : transitions.entrySet()){
            reset.addAll(entry.getValue().getReset());
        }
    }

    public SynchronizedTransition(Action action, Map<TA, Transition> transitions, Set<Clock> reset){
        this.action = action;
        this.transitions = transitions;
        this.reset = reset;
    }

    public SynchronizedTransition(Action action){
        this.action = action;
        this.transitions = new LinkedHashMap<>();
        this.reset = new LinkedHashSet<>();
    }

    private SynchronizedTransition copy(){
        return new SynchronizedTransition(this.action, new LinkedHashMap<>(this.transitions), new LinkedHashSet<>(this.reset));
    }

    public void addTransition(Transition transition){
        this.transitions.put(transition.getOwnerTa(),transition);
        this.reset.addAll(transition.getReset());
    }

    // Only add transition without updating reset
    private void simpleAddTransition(Transition transition){
        this.transitions.put(transition.getOwnerTa(), transition);
    }

    public LocationVector getSourceVector(){
        LocationVector locationVector = new LocationVector();
        for(Map.Entry<TA,Transition> entry : this.transitions.entrySet()){
            locationVector.put(entry.getKey(), entry.getValue().getSource());
        }
        return locationVector;
    }

    public LocationVector getTargetVector(){
        LocationVector locationVector = new LocationVector();
        for(Map.Entry<TA,Transition> entry : this.transitions.entrySet()){
            locationVector.put(entry.getKey(), entry.getValue().getTarget());
        }
        return locationVector;
    }

    public void printDetail(){
        for(Map.Entry<TA, Transition> entry : this.transitions.entrySet()){
            TA ta = entry.getKey();
            Transition transition = entry.getValue();
            System.out.println(ta.getName() + ":" + transition);
        }
    }

    public boolean canPass(ClockValuation clockValuation){
        for(Transition trans : this.transitions.values()){
            if(!trans.getGuard().truthValue(clockValuation))
                return false;
        }
        return true;
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for(Map.Entry<TA,Transition> entry : this.transitions.entrySet()){
            sb.append("\n");
            sb.append(entry.getKey().getName());
            sb.append(" : ");
            sb.append(entry.getValue());
        }
        sb.append("\n]");
        return sb.toString();
    }
}
