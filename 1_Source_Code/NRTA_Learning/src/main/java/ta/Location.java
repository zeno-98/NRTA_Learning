package ta;

import language.actions.LogicalTimedAction;
import language.actions.ResetLogicalTimedAction;
import lombok.Data;
import run.ClockValuation;
import tools.IdCounter;

import java.util.*;

@Data
public class Location implements Comparable<Location>{
    private static final IdCounter id_counter = new IdCounter();

    private final int id;
    private final String name;
    private final TA owner_ta;
    private final boolean is_accepting;
    private final Constraint invariant;
    // trans.id -> trans
    private final Map<Integer,Transition> edges_in_map;
    private final Map<Integer,Transition> edges_out_map;
    private final Map<Action,List<Transition>> edges_out_grouped; // Groups outgoing edges by action name, epsilon_trans should not be included
    private final Transition epsilon_trans;


    public static void resetIDCounter(){
        Location.id_counter.reset();
    }

    /********
     * Main constructor
     * @param name
     * @param is_accepting
     * @param invariant
     * @param owner_ta
     */
    public Location(String name, boolean is_accepting, Constraint invariant, TA owner_ta){
        this.id = id_counter.createId();
        this.name = name;
        this.owner_ta = owner_ta;
        this.is_accepting = is_accepting;
        this.invariant = invariant;
        this.edges_in_map = new HashMap<>();
        this.edges_out_map = new HashMap<>();
        this.edges_out_grouped = new HashMap<>();
        this.epsilon_trans = new Transition(this,Action.EPSILON,Constraint.TRUE,new HashSet<>(),this);
    }

    /********
     * Constructs a location with invariant = true
     * @param name
     * @param is_accepting
     * @param owner_ta
     */
    public Location(String name, boolean is_accepting, TA owner_ta){
        this(name, is_accepting, Constraint.TRUE, owner_ta);
    }

    public void addEdgeIn(Transition e){
        this.edges_in_map.put(e.getId(),e);
    }

    public void addEdgeOut(Transition e){
        this.edges_out_map.put(e.getId(),e);
        Action action = e.getAction();
        List<Transition> transition_group = edges_out_grouped.get(action);
        if(transition_group != null){
            transition_group.add(e);
        } else {
            transition_group = new LinkedList<>();
            transition_group.add(e);
            this.edges_out_grouped.put(action,transition_group);
        }
    }

    public void removeEdgeTo(Location location){
        Set<Action> changed_action = new HashSet<>();
        for(Transition trans : new HashSet<>(this.edges_out_map.values())){
            if(trans.getTarget().equals(location)) {
                this.edges_out_map.remove(trans.getId());
                changed_action.add(trans.getAction());
            }
        }
        for(Action action : changed_action){
            List<Transition> transList = this.edges_out_grouped.get(action);
            List<Transition> updatedList = new LinkedList<>();
            for(Transition trans: transList){
                if(this.edges_out_map.containsKey(trans.getId()))
                    updatedList.add(trans);
            }
            if(!updatedList.isEmpty())
                this.edges_out_grouped.put(action,updatedList);
            else
                this.edges_out_grouped.remove(action);
        }
    }

    public List<Transition> getActionTransitions(Action action){
        return this.edges_out_grouped.get(action);
    }

    public Transition getPassableTransition(Action action, ClockValuation clockValuation){
        for(Transition transition : this.getActionTransitions(action)){
            if(transition.ifPassable(clockValuation))
                return transition;
        }
        return null;
    }

    public String toString(){
        return this.name + " : " + this.is_accepting;
    }

    public TA getOwnerTa() {
        return this.owner_ta;
    }

    public int hashCode(){
        return Objects.hash(this.owner_ta.getName(), this.name);
    }

    @Override
    public int compareTo(Location o) {
        int taCmp = this.owner_ta.getName().compareTo(o.owner_ta.getName());
        if (taCmp != 0) {
            return taCmp;
        }
        return this.name.compareTo(o.name);
    }

    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof Location other)) return false;
        return this.owner_ta.getName().equals(other.owner_ta.getName())
                && this.name.equals(other.name);
    }

    public List<Transition> getOutgoingTransitions(Action sigma){
        if(this.edges_out_grouped.containsKey(sigma))
            return this.edges_out_grouped.get(sigma);
        else {
            List<Transition> outcome = new LinkedList<>();
            if(!this.owner_ta.containsAction(sigma))
                outcome.add(this.epsilon_trans);
            return outcome;
        }
    }

    // This seems to be replaceable by the one below
    public List<Transition> getOutgoingTransitions(ResetLogicalTimedAction arl){
        Action action = arl.getAction();
        if(!this.owner_ta.containsAction(action))
            return Collections.singletonList(this.epsilon_trans);
        List<Transition> edges_out = this.getOutgoingTransitions(action);
        List<Transition> outcome = new LinkedList<>();
        if(edges_out != null){
            Set<Clock> reset_clocks = new HashSet<>(arl.getReset_clocks());
            reset_clocks.retainAll(this.owner_ta.X.values());
            for(Transition edge : edges_out){
                if(edge.ifPassable(arl.getClockValuation(), reset_clocks))
                    outcome.add(edge);
            }
        }
        return outcome;
    }

    public List<Transition> getOutgoingTransitions(LogicalTimedAction la) {
        Action action = la.getAction();
        if (!this.owner_ta.containsAction(action))
            return Collections.singletonList(this.epsilon_trans);
        List<Transition> edges_out = this.getOutgoingTransitions(action);
        List<Transition> outcome = new LinkedList<>();
        if (edges_out != null) {
            for (Transition edge : edges_out) {
                if (edge.ifPassable(la.getClockValuation(), edge.getReset()))
                    outcome.add(edge);
            }
        }
        return outcome;
    }

    public Set<Location> nextLocations(){
        Set<Location> outcome = new HashSet<>();
        for(Map.Entry<Integer,Transition> entry : this.edges_out_map.entrySet()){
            outcome.add(entry.getValue().getTarget());
        }
        return outcome;
    }

    public Map<Action,Part> getOutPartition(Set<Action> actions){
        Map<Action,Part> outcome = new HashMap<>();
        for(Action action : actions){
            if(!this.edges_out_grouped.containsKey(action))
                outcome.put(action, new Part());
            else {
                List<Transition> labeled = this.edges_out_grouped.get(action);
                Part part = new Part();
                for(Transition transition : labeled){
                    part.simpleAdd(transition.getGuard());
                }
                outcome.put(action, part);
            }
        }
        return outcome;
    }

}
