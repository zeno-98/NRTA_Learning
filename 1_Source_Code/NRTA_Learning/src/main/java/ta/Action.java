package ta;

import exception.ValueExceptions;
import lombok.Getter;
import tools.IdCounter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Action implements Comparable<Action>{
    private static final IdCounter id_counter = new IdCounter();
    // EPSILON action is a specially reserved action for describing staying at a location
    public static final Action EPSILON = new Action("epsilon", true);
    public static final Map<String,Action> GLOBAL_ACTION_MAP = new HashMap<>();

    @Getter
    private final String name;
    @Getter
    private final boolean isEpsilon;
    private final int id;

    protected Action(String name){
        this.id = id_counter.createId();
        this.name = name;
        this.isEpsilon = false;
    }

    protected Action(String name, boolean isEpsilon){
        this.id = id_counter.createId();
        this.name = name;
        this.isEpsilon = isEpsilon;
    }

    public static Action buildAction(String name){
        Action outcome = GLOBAL_ACTION_MAP.get(name);
        if(outcome == null){
            outcome = new Action(name);
            GLOBAL_ACTION_MAP.put(name,outcome);
        }
        return outcome;
    }

    public static Action getAction(String name){
        Action outcome = GLOBAL_ACTION_MAP.get(name);
        if(outcome == null){
            throw new RuntimeException(ValueExceptions.ACTION_HAVE_NOT_BEEN_FOUND);
        }
        return outcome;
    }

    public static Action buildEpsilonAction(String name){
        Action outcome = GLOBAL_ACTION_MAP.get(name);
        if(outcome == null){
            outcome = new Action(name, true);
            GLOBAL_ACTION_MAP.put(name,outcome);
        }
        return outcome;
    }

    public String toString(){
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Action action)) return false;
        return this.isEpsilon == action.isEpsilon && this.name.equals(action.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.isEpsilon);
    }

    @Override
    public int compareTo(Action o) {
        int cmp = this.name.compareTo(o.name);
        if (cmp != 0) {
            return cmp;
        }
        return Boolean.compare(this.isEpsilon, o.isEpsilon);
    }
}
