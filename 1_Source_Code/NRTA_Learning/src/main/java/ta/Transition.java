package ta;

import lombok.Getter;
import run.ClockValuation;
import tools.IdCounter;

import java.util.Set;

@Getter
public class Transition {
    private static final IdCounter id_counter = new IdCounter();
    private final Location source;
    private final Constraint guard;
    private final Action action;
    private final Set<Clock> reset;
    private final Location target;
    private final int id;

    private String assignment;

    public Transition(Location source,
                      Action action,
                      Constraint guard,
                      Set<Clock> reset,
                      Location target){
        this.id = id_counter.createId();
        this.source = source;
        this.action = action;
        this.guard = guard;
        this.reset = reset;
        this.target = target;
        if(action != Action.EPSILON) {
            source.addEdgeOut(this);
            target.addEdgeIn(this);
        }
    }

    public String toString(){
        if(this.action == Action.EPSILON)
            return "keep in " + source;
        else
            return source.getName() + " -- " + action + " -- " + guard + " -- " + reset + " -> " + target.getName();
    }

    public boolean isEpsilon(){
        return this.action == Action.EPSILON;
    }

    public boolean ifPassable(ClockValuation clockValuation, Set<Clock> resetClocks){
        if(!this.reset.equals(resetClocks))
            return false;
        else
            return this.guard.truthValue(clockValuation);
    }

    public boolean ifPassable(ClockValuation clockValuation){
        return this.guard.truthValue(clockValuation);
    }

    public TA getOwnerTa(){
        return this.source.getOwnerTa();
    }

}
