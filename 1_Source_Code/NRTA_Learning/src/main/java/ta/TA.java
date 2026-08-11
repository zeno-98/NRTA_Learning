package ta;

import com.microsoft.z3.Context;
import language.actions.DelayTimedAction;
import language.actions.LogicalTimedAction;
import language.actions.ResetLogicalTimedAction;
import language.words.DelayTimedWord;
import language.words.LogicalTimedWord;
import language.words.ResetLogicalTimedWord;
import lombok.Data;
import run.ClockValuation;
import tools.FileIO.TAUtil;
import tools.IdCounter;
import tools.Tuple.Pair;
import verify.GlobalSetting;

import java.util.*;

@Data
public class TA implements Comparable<TA>{
    private static IdCounter id_counter = new IdCounter();
    protected int id;
    protected String name;
    protected Map<String,Location> Q;                   //location set
    protected Location q0;                              //init location
    protected Map<String,Action> Sigma;                 //alphabet
    protected Map<String,Location> F;                   //accepting location set
    protected Map<String,Clock> X;                      //clock set
    protected Map<Integer,Transition> Delta;            //transition set
    protected Map<Clock,Value> K;                       //upper bound of the clocks
    protected Action blank_action;

    public TA(String name){
        this.id = id_counter.createId();
        this.name = name;
        this.Q = new TreeMap<>();
        this.Sigma = new HashMap<>();
        this.F = new TreeMap<>();
        this.X = new HashMap<>();
        this.Delta = new HashMap<>();
        this.K = new HashMap<>();
    }

    public void setBlankAction(Action action){
        if(this.blank_action==null) {
            this.addAction(action);
            this.blank_action = action;
        }
    }

    private void updateK(Constraint constraint){
        for(AtomConstraint atomConstraint : constraint.getAtomConstraints()){
            Value upperbound = atomConstraint.getUpperbound();
            if(upperbound.isInfinite() || upperbound.isMinus_infinite())
                continue;
            Clock clock1 = atomConstraint.getClock1();
            Clock clock2 = atomConstraint.getClock2();
            if(!clock1.isZeroClock()) {
                if (!this.K.containsKey(clock1))
                    this.K.put(clock1, upperbound);
                else {
                    Value old = this.K.get(clock1);
                    if (Value.gt(upperbound, old))
                        this.K.put(clock1, upperbound);
                }
            }
            if(!clock2.isZeroClock()){
                upperbound = upperbound.negative();
                if (!this.K.containsKey(clock2))
                    this.K.put(clock2, upperbound);
                else {
                    Value old = this.K.get(clock2);
                    if (Value.gt(upperbound, old))
                        this.K.put(clock2, upperbound);
                }
            }
        }
    }


    public void setSigma(Set<Action> SigmaSet){
        for(Action sigma : SigmaSet)
            this.addAction(sigma);
    }

    public void setSigma(Map<String,Action> Sigma){
        this.Sigma = new HashMap<>();
        this.Sigma.putAll(Sigma);
    }

    public void addLocation(Location q){
        this.Q.put(q.getName(),q);
        if(q.is_accepting()) this.F.put(q.getName(),q);
        this.updateK(q.getInvariant());
    }


    public Location getLocation(String name){
        return this.Q.get(name);
    }

    public void addTransition(Transition e){
        this.addLocation(e.getSource());
        this.addLocation(e.getTarget());
        this.Delta.put(e.getId(),e);
        this.updateK(e.getGuard());
    }


    public void addAction(Action action){
        this.Sigma.put(action.getName(),action);
    }


    public void removeAction(Action action){
        this.Sigma.remove(action.getName());
    }

    public Action getAction(String name){
        return this.Sigma.get(name);
    }

    public Set<Action> getAllActions(){
        List<Action> ordered = new ArrayList<>(this.Sigma.values());
        ordered.sort(Comparator.comparing(Action::getName));
        return new LinkedHashSet<>(ordered);
    }

    public Set<Clock> getAllClocks(){
        List<Clock> ordered = new ArrayList<>(this.X.values());
        ordered.sort(Comparator
                .comparing(Clock::getTA_name)
                .thenComparing(Clock::getName));
        return new LinkedHashSet<>(ordered);
    }

    public void addClock(Clock x){
        this.X.put(x.getName(),x);
    }


    public Clock getClock(String name){
        return this.X.get(name);
    }

    public boolean containsAction(Action action){
        return this.Sigma.containsKey(action.getName());
    }


    public String toString(){
        return this.name;
    }

    // hash value is directly replaced by id
    public int hashCode(){
        return this.id;
    }

    public void printMainDetails(){
        System.out.println("TA name: " + this.name);
        System.out.println("Locations:");
        List<Location> locations = getDisplayLocations();
        int maxLocationNameLength = getMaxLocationNameLength(locations);
        for(Location q : locations){
            System.out.println(q.getName()
                    + " ".repeat(Math.max(0, maxLocationNameLength - q.getName().length()))
                    + " : "
                    + q.is_accepting());
        }
        System.out.println("Initial Location:");
        System.out.println(this.q0.getName());
        System.out.println("Transitions: ");
        int i = 0;
        for(Map.Entry<Integer,Transition> entry : Delta.entrySet()){
            Transition delta = entry.getValue();
            if (isTransitionToHiddenSink(delta)) {
                continue;
            }
            System.out.println("\"" + i + "\" : " + delta);
            i++;
        }
        System.out.println();
    }

    public void removeLoc(Location loc){
        this.Q.remove(loc.getName());
        for(Transition trans : new HashSet<>(this.Delta.values())){
            if(trans.getTarget().equals(loc))
                this.Delta.remove(trans.getId());
            else if(trans.getSource().equals(loc))
                this.Delta.remove(trans.getId());
        }
        for(Location location : this.Q.values()){
            location.removeEdgeTo(location);
        }
    }

    @Override
    public int compareTo(TA o) {
        return Integer.compare(this.id, o.id);
    }

    /**
     * Determines if the current TA is complete.
     * Uses breadth-first search for all locations. Cannot directly search the location list
     * because some locations may be unreachable and would cause errors.
     * @param ctx the Z3 context
     * @return true if the TA is complete, false otherwise
     */
    public boolean isComplete(Context ctx){
        Set<Location> checked = new HashSet<>();
        Set<Location> waiting = new HashSet<>();
        Set<Location> next = new HashSet<>();
        waiting.add(this.q0);
        Set<Action> actions = new HashSet<>(this.Sigma.values());
        Set<Clock> clocks = new HashSet<>(this.X.values());
        while(!waiting.isEmpty()){
            for(Location location : waiting){
                Map<Action,Part> parts = location.getOutPartition(actions);
                for(Map.Entry<Action,Part> entry : parts.entrySet()){
                    Part part = entry.getValue();
                    if(!part.isComplete(clocks,ctx))
                        return false;
                }
                checked.add(location);
                next.addAll(location.nextLocations());
            }
            next.removeAll(checked);
            waiting = next;
            next = new HashSet<>();
        }
        return true;
    }

    /**
     * Makes the current TA complete.
     * This directly modifies the current object.
     * @param ctx the Z3 context
     * @return this TA after making it complete
     */
    public TA makeComplete(Context ctx){
        Location Sink = new Location("Sink", false, this);
        this.addLocation(Sink);

        Set<Location> checked = new HashSet<>();
        Set<Location> waiting = new HashSet<>();
        Set<Location> next = new HashSet<>();
        Set<Location> reachable = new HashSet<>();
        checked.add(Sink);
        waiting.add(this.q0);
        reachable.add(this.q0);
        Set<Action> actions = new HashSet<>(this.Sigma.values());
        Set<Clock> clocks = new HashSet<>(this.X.values());
        while(!waiting.isEmpty()){
            for(Location location : waiting){
                Map<Action,Part> parts = location.getOutPartition(actions);
                reachable.add(location);
                for(Map.Entry<Action,Part> entry : parts.entrySet()){
                    Part part = entry.getValue();
                    if(!part.isComplete(clocks,ctx)){
                        Part left_part = Part.getAll(clocks, ctx);
                        left_part.simpleMinusViaSmt(part, ctx);
                        Action action = entry.getKey();
                        for(Constraint guard : left_part.getConvex()){
                            Transition transition = new Transition(location,action,guard,clocks,Sink);
                            this.addTransition(transition);
                        }
                        reachable.add(Sink);
                    }
                }
                checked.add(location);
                next.addAll(location.nextLocations());
            }
            next.removeAll(checked);
            waiting = next;
            next = new HashSet<>();
        }

        List<String> names = new LinkedList<>();
        // Remove unreachable parts
        for(Map.Entry<String,Location> entry : this.Q.entrySet()){
            String name = entry.getKey();
            Location location = entry.getValue();
            if(!reachable.contains(location))
                names.add(name);
        }

        for(String name : names){
            Location location = this.Q.get(name);
            for(Map.Entry<Integer,Transition> entry : location.getEdges_out_map().entrySet()){
                this.Delta.remove(entry.getValue().getId());
            }
            this.Q.remove(name);
        }


        // If Sink is also included, add self-loops for all actions
        if(reachable.contains(Sink)){
            Constraint guard = new Constraint();
            for(Action action : actions){
                Transition self_loop = new Transition(Sink,action,guard,clocks,Sink);
                this.addTransition(self_loop);
            }
        }
        return this;
    }

    /**
     * Determines if the current TA is deterministic.
     * @param ctx the Z3 context
     * @return true if the TA is deterministic, false otherwise
     */
    public boolean isDeterministic(Context ctx){
        Set<Location> checked = new HashSet<>();
        Set<Location> waiting = new HashSet<>();
        Set<Location> next = new HashSet<>();
        waiting.add(this.q0);
        Set<Action> actions = new HashSet<>(this.Sigma.values());
        while(!waiting.isEmpty()){
            for(Location location : waiting){
                Map<Action,Part> parts = location.getOutPartition(actions);
                for(Map.Entry<Action,Part> entry : parts.entrySet()){
                    Part part = entry.getValue();
                    if(!part.isDeterministic(ctx))
                        return false;
                }
                checked.add(location);
                next.addAll(location.nextLocations());
            }
            next.removeAll(checked);
            waiting = next;
            next = new HashSet<>();
        }
        return true;
    }

    /**
     * Determines if the current TA can accept the specified wrl.
     * Requires the TA to be deterministic, complete, and all_accept.
     * Does not consider invariants.
     * @param wrl the reset logical timed word
     * @return a pair containing whether it's accepted and the truncation position
     */
    public Pair<Boolean, Integer> ifContainResetLogicalTimedWordStrict(ResetLogicalTimedWord wrl){
        if (!wrl.isLegal()) {
            return new Pair<>(false, -1);
        }
        Location current_loc = this.q0;
        for(int i=0;i<wrl.getLength();i++){
            ResetLogicalTimedAction rla = wrl.getTimedAction(i);
            Action action = rla.getAction();
            ClockValuation valuation = rla.getClockValuation();
            Set<Clock> reset = rla.getReset_clocks();

            List<Transition> outgoingTransitions = current_loc.getOutgoingTransitions(action);
            for(Transition outgoingTransition : outgoingTransitions){
                Constraint guard = outgoingTransition.getGuard();
                Set<Clock> tran_reset = outgoingTransition.getReset();
                Location target = outgoingTransition.getTarget();
                if(!guard.truthValue(valuation))
                    continue;   // Must satisfy guard
                // Since it's deterministic, once this guard can be passed, only this guard can be passed
                // Indicates the reset of the i-th action has a problem, i<wrl.getLength()
                if(!tran_reset.equals(reset))
                    return new Pair<>(false, i);
                current_loc = target;
            }
        }
        return new Pair<>(current_loc.is_accepting(), wrl.getLength());
    }


    /**
     * Determines if the current TA can accept the specified wrl.
     * Uses depth-first search.
     * Does not require the TA to be deterministic, complete, or all_accept.
     * Assumes the invariant at q0 can accept the all-zero state.
     * @param wrl the reset logical timed word
     * @return true if the word is accepted, false otherwise
     * @deprecated This method is deprecated
     */
    @Deprecated
    public boolean ifContainResetLogicalTimedWord(ResetLogicalTimedWord wrl){
        if(wrl.isEmpty())
            return this.q0.is_accepting();
        else
            return this.ifContainResetLogicalTimedWordCore(this.q0, 0, wrl);
    }

    private boolean ifContainResetLogicalTimedWordCore(Location current_loc,
                                                          int depth,
                                                          ResetLogicalTimedWord wrl){
        ResetLogicalTimedAction rla = wrl.getTimedAction(depth);
        Action action = rla.getAction();
        ClockValuation current_clockValuation = rla.getClockValuation();
        Set<Clock> act_reset = rla.getReset_clocks();
        Constraint current_invariant = current_loc.getInvariant();
        // If current valuation doesn't satisfy the invariant of current location, return false directly
        if(!current_invariant.truthValue(current_clockValuation))
            return false;
        List<Transition> outgoingTransitions = current_loc.getOutgoingTransitions(action);
        // Examine outgoing edges one by one
        for(Transition outgoingTransition : outgoingTransitions){
            Constraint guard = outgoingTransition.getGuard();
            Set<Clock> tran_reset = outgoingTransition.getReset();
            Location target = outgoingTransition.getTarget();
            if(!tran_reset.equals(act_reset))
                continue;   // Reset must be the same
            if(!guard.truthValue(current_clockValuation))
                continue;   // Must satisfy guard
            ClockValuation next_clock_valuation = current_clockValuation.reset(act_reset);
            // Must satisfy the invariant of target location
            Constraint target_invariant = target.getInvariant();
            if(target_invariant.truthValue(next_clock_valuation)){
                if(depth==wrl.getLength()-1) {
                    if (target.is_accepting())
                        return true;
                } else {
                    boolean outcome = this.ifContainResetLogicalTimedWordCore(target, depth + 1, wrl);
                    if (outcome)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if the current TA can accept the specified dw.
     * Uses depth-first search.
     * Does not require the TA to be deterministic, complete, or all_accept.
     * Note: does not consider the invariant of the accepting location.
     * @param dw the delay timed word
     * @return true if the word is accepted, false otherwise
     */
    public boolean ifContainDelayTimedWord(DelayTimedWord dw){
        if(dw.isEmpty())
            return this.q0.is_accepting();
        else
            return this.ifContainDelayTimedWordCore(this.q0, 0, new ClockValuation(this.getAllClocks()), dw);
    }

    private boolean ifContainDelayTimedWordCore(Location current_loc,
                                                   int depth,
                                                   ClockValuation v,
                                                   DelayTimedWord dw){
        DelayTimedAction da = dw.getTimedAction(depth);
        Action action = da.getAction();
        Value delay = da.getDelayTime();
        ClockValuation current_clockValuation = v.delay(delay);
        Constraint current_invariant = current_loc.getInvariant();
        // If current valuation doesn't satisfy the invariant of current location, return false directly
        if(!current_invariant.truthValue(current_clockValuation))
            return false;
        List<Transition> outgoingTransitions = current_loc.getOutgoingTransitions(action);
        // Examine outgoing edges one by one
        for(Transition outgoingTransition : outgoingTransitions){
            Constraint guard = outgoingTransition.getGuard();
            Location target = outgoingTransition.getTarget();
            Set<Clock> reset = outgoingTransition.getReset();
            if(!guard.truthValue(current_clockValuation))
                continue;   // Must satisfy guard
            if(depth>=dw.getLength()-1) {
                if (target.is_accepting())
                    return true;
            } else {
                boolean outcome = this.ifContainDelayTimedWordCore(target, depth + 1, current_clockValuation.reset(reset), dw);
                if (outcome)
                    return true;
            }
        }
        return false;
    }

    /**
     * Determines if the current TA can accept the specified rl.
     * Uses depth-first search.
     * Does not require the TA to be deterministic, complete, or all_accept.
     * Note: does not consider the invariant of the accepting location.
     * @param lw the logical timed word
     * @return true if the word is accepted, false otherwise
     * @deprecated This method is deprecated due to potential errors in counterexample handling
     */
    @Deprecated
    public boolean ifContainLogicalTimedWord(LogicalTimedWord lw){
        if(lw.isEmpty())
            return this.q0.is_accepting();
        else
            return this.ifContainLogicalTimedWordCore(this.q0, 0, lw);
    }

    private boolean ifContainLogicalTimedWordCore(Location current_loc,
                                                     int depth,
                                                     LogicalTimedWord lw){
        LogicalTimedAction la = lw.getTimedAction(depth);
        Action action = la.getAction();
        ClockValuation current_clockValuation = la.getClockValuation();
        Constraint current_invariant = current_loc.getInvariant();
        // If current valuation doesn't satisfy the invariant of current location, return false directly
        if(!current_invariant.truthValue(current_clockValuation))
            return false;
        List<Transition> outgoingTransitions = current_loc.getOutgoingTransitions(action);
        // Examine outgoing edges one by one
        for(Transition outgoingTransition : outgoingTransitions){
            Constraint guard = outgoingTransition.getGuard();
            Location target = outgoingTransition.getTarget();
            if(!guard.truthValue(current_clockValuation))
                continue;   // Must satisfy guard
            if(depth>=lw.getLength()-1) {
                if (target.is_accepting())
                    return true;
            } else {
                boolean outcome = this.ifContainLogicalTimedWordCore(target, depth + 1, lw);
                if (outcome)
                    return true;
            }
        }
        return false;
    }

    /**
     * Examines how a word runs in the TA.
     * This method is mainly used for debugging.
     * Primarily addresses potential issues with partition functions.
     * Since incorrect partition results may be non-deterministic,
     * non-deterministic cases are considered here.
     * @param lw the logical timed word
     * @return a list of transition paths
     */
    public List<List<Transition>> runWord(LogicalTimedWord lw){
        return this.runWordCore(lw, 0, new LinkedList<>(), this.q0);
    }


    private List<List<Transition>> runWordCore(LogicalTimedWord lw, int depth, List<Transition> path, Location loc){
        List<List<Transition>> outcome = new LinkedList<>();
        if(depth >= lw.getLength()) {
            outcome.add(path);
        } else {
            LogicalTimedAction la = lw.getTimedAction(depth);
            Action action = la.getAction();
            ClockValuation cv = la.getClockValuation();
            for(Transition trans : loc.getActionTransitions(action)){
                Constraint guard = trans.getGuard();
                if(guard.truthValue(cv)){
                    List<Transition> path_step = new LinkedList<>(path);
                    path_step.add(trans);
                    outcome.addAll(this.runWordCore(lw, depth+1, path_step, trans.getTarget()));
                }
            }
        }
        return outcome;
    }

    public String getMainDetail(){
        return this.getMainDetail(false);
    }

    public String getMainDetail(boolean hideSinkLocationWhenSinkTransitionsHidden){
        StringBuilder sb = new StringBuilder();
        sb.append("TA name: ").append(this.name).append('\n');
        sb.append("Locations:").append('\n');
        List<Location> locations = getDisplayLocations(hideSinkLocationWhenSinkTransitionsHidden);
        int maxLocationNameLength = getMaxLocationNameLength(locations);
        for(Location q : locations){
            sb.append(q.getName())
                    .append(" ".repeat(Math.max(0, maxLocationNameLength - q.getName().length())))
                    .append(" : ")
                    .append(q.is_accepting())
                    .append('\n');
        }
        sb.append("Initial Location: ").append(this.q0.getName()).append('\n');
        sb.append("Transitions: ").append('\n');
        int i = 0;
        for(Map.Entry<Integer,Transition> entry : Delta.entrySet()){
            Transition delta = entry.getValue();
            if (isTransitionToHiddenSink(delta)) {
                continue;
            }
            sb.append("\"").append(i).append("\" : ").append(delta).append('\n');
            i++;
        }
        return sb.toString().replace("h_", "");
    }


    private boolean isTransitionToHiddenSink(Transition transition) {
        if (GlobalSetting.SHOW_SINK_TRANSITIONS) {
            return false;
        }
        Location target = transition.getTarget();
        return target != null && isSinkLocation(target);
    }

    private boolean isSinkLocation(Location location) {
        if (location.is_accepting()) {
            return false;
        }
        Collection<Transition> outgoing = location.getEdges_out_map().values();
        for (Transition transition : outgoing) {
            if (!location.equals(transition.getTarget())) {
                return false;
            }
        }
        return true;
    }

    private List<Location> getDisplayLocations() {
        return this.getDisplayLocations(false);
    }

    private List<Location> getDisplayLocations(boolean hideSinkLocationWhenSinkTransitionsHidden) {
        List<Location> locations = new ArrayList<>(this.Q.values());
        if (hideSinkLocationWhenSinkTransitionsHidden && !GlobalSetting.SHOW_SINK_TRANSITIONS) {
            locations.removeIf(this::isSinkLocation);
        }
        locations.sort((left, right) -> {
            boolean leftSink = "Sink".equals(left.getName());
            boolean rightSink = "Sink".equals(right.getName());
            if (leftSink && !rightSink) {
                return 1;
            }
            if (!leftSink && rightSink) {
                return -1;
            }
            return left.getName().compareTo(right.getName());
        });
        return locations;
    }

    private int getMaxLocationNameLength(List<Location> locations) {
        int max = 0;
        for (Location location : locations) {
            max = Math.max(max, location.getName().length());
        }
        return max;
    }

    public void printMainDetail(){
        System.out.println(this.getMainDetail());
    }

}
