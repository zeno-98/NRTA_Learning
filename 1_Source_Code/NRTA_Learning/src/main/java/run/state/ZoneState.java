package run.state;

import lombok.Getter;
import run.zone.Zone;
import run.parallel_elements.LocationVector;
import run.parallel_elements.SynchronizedTransition;
import ta.*;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.util.*;

@Getter
public class ZoneState extends State {
    protected final Zone zone;

    public ZoneState(LocationVector location_vec,
                     SynchronizedTransition last_transitions,
                     State last_state) {
        super(location_vec, last_transitions, last_state);
        this.zone = TeacherSetting.createZone();
    }

    private ZoneState(LocationVector location_vec,
                      SynchronizedTransition last_transitions,
                      State last_state,
                      Zone zone) {
        super(location_vec, last_transitions, last_state);
        this.zone = zone;
    }

    public void up(){
        this.zone.up();
    }

    public void applyInvariants(){
        for(Map.Entry<TA, Location> entry : this.getOrderedLocationEntries()){
            Location location = entry.getValue();
            this.zone.and(location.getInvariant());
        }
        this.zone.canonical();
    }

    // Get all successors
    public List<ZoneState> getAllNext(){
        List<ZoneState> outcome = new LinkedList<>();
        Set<Action> actionSet = new LinkedHashSet<>();
        for (Map.Entry<TA, Location> entry : this.getOrderedLocationEntries()) {
            List<Action> orderedActions = new ArrayList<>(entry.getValue().getEdges_out_grouped().keySet());
            orderedActions.sort(Comparator.comparing(Action::getName));
            actionSet.addAll(orderedActions);
        }
        for (Action action : actionSet) {
            outcome.addAll(this.getAfterAction(action));
        }
        return outcome;
    }

    // Get all successors after a specific action occurs
    public List<ZoneState> getAfterAction(Action action){
        List<ZoneState> outcome = new LinkedList<>();

        Map<TA, Location> locationMap = this.location_vec.getLocationMap();
        int TA_num = locationMap.size();

        List<Transition>[] trans_table = new LinkedList[TA_num];
        int[] trans_pointers = new int[TA_num];// Java default value is 0
        TA[] TAs = new TA[TA_num];
        int TA_pointer = 0;
        // Extract all edges corresponding to this action
        for(Map.Entry<TA,Location> entry : this.getOrderedLocationEntries()){
            TA ta = entry.getKey();
            TAs[TA_pointer] = ta;
            Location location = entry.getValue();
            if(ta.containsAction(action)){
                List<Transition> action_trans = location.getActionTransitions(action);
                // According to the definition of parallel composition, if some TA has this action,
                // but this location has no corresponding edge, then the parallel automaton has no such edge
                if(action_trans == null)
                    return outcome;
                else {
                    action_trans.sort(Comparator
                            .comparing((Transition t) -> t.getSource().getName())
                            .thenComparing(t -> t.getTarget().getName())
                            .thenComparing(t -> t.getGuard().toString())
                            .thenComparing(t -> {
                                List<String> resetNames = new ArrayList<>();
                                for (Clock clock : t.getReset()) {
                                    resetNames.add(clock.getTA_name() + "." + clock.getName());
                                }
                                Collections.sort(resetNames);
                                return String.join(",", resetNames);
                            }));
                    trans_table[TA_pointer] = action_trans;
                }
            } else {
                trans_table[TA_pointer] = new LinkedList<>();
                trans_table[TA_pointer].add(location.getEpsilon_trans()); // If no corresponding action, add epsilon transition
            }
            TA_pointer++;
        }
        // Generate all edges and states
        while(true) {
            int num = 0;
            SynchronizedTransition Sync_trans = new SynchronizedTransition(action);
            for (TA_pointer = 0; TA_pointer < TA_num; TA_pointer++) {
                int current_index = trans_pointers[TA_pointer];
                Transition transition = trans_table[TA_pointer].get(current_index);
                Sync_trans.addTransition(transition);
                if(transition.getAction() != Action.EPSILON)
                    num++;
            }

            ZoneState new_dbm_state = getAfterTransition(Sync_trans);
            if (new_dbm_state.isLegal()) // Only keep feasible DBMState
                outcome.add(new_dbm_state);

            int i;
            for (i = 0; i < TA_num; i++) {
                trans_pointers[i]++;
                if (trans_pointers[i] < trans_table[i].size())
                    break;
                else
                    trans_pointers[i] = 0;
            }
            if(i==TA_num)
                return outcome;
        }
    }

    // Calculate the new state reached by DBMState after a SynchronizedTransition
    protected ZoneState getAfterTransition(SynchronizedTransition Sync_trans){ // SyncTrans refers to one action but any number of state transitions
        LocationVector target_location = new LocationVector();
        Zone next_dbm = this.zone.copy();

        List<Constraint> invariant_list = new LinkedList<>();
        for(Map.Entry<TA,Transition> entry : Sync_trans.getTransitions().entrySet()){
            TA ta = entry.getKey();
            Transition trans = entry.getValue();
            Constraint guard = trans.getGuard();
            next_dbm.and(guard); // guard
            Location target = trans.getTarget();
            invariant_list.add(target.getInvariant());
            target_location.put(ta, target);
        }
        next_dbm.canonical();
        for(Clock x : Sync_trans.getReset())
            next_dbm.reset(x); // Clock reset

        for(Constraint invariant : invariant_list)
            next_dbm.and(invariant); // Add invariant
        next_dbm.canonical();

        next_dbm.up();
        for(Constraint invariant : invariant_list)
            next_dbm.and(invariant); // Add invariant
        next_dbm.canonical();
        if (TeacherSetting.VERIFY_STORE_TRACE) {
            return new ZoneState(target_location, Sync_trans, this, next_dbm);
        }
        return new ZoneState(target_location, null, null, next_dbm);
    }

    public boolean isLegal(){
        return this.zone.isConsistent();
    }

    private List<Map.Entry<TA, Location>> getOrderedLocationEntries() {
        List<Map.Entry<TA, Location>> ordered = new ArrayList<>(this.location_vec.getLocationMap().entrySet());
        ordered.sort(Comparator.comparing((Map.Entry<TA, Location> entry) -> entry.getKey().getName()));
        return ordered;
    }

}
