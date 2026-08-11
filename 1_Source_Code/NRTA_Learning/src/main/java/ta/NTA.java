package ta;

import com.microsoft.z3.Context;
import language.actions.LogicalTimedAction;
import language.actions.RTARegionAction;
import language.actions.ResetLogicalTimedAction;
import language.words.LogicalTimedWord;
import language.words.RTARegionWord;
import language.words.ResetLogicalTimedWord;
import lombok.Getter;
import run.ClockValuation;
import run.Trace;
import run.parallel_elements.LocationVector;
import run.parallel_elements.SynchronizedTransition;
import run.state.ConcreteState;
import run.state.ZoneState;
import run.zone.Zone;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;
import learning.teachers.TeacherSetting;

import java.util.*;

@Getter
// net of Timed Automata
public class NTA implements Iterable<TA>{
    private final Map<Integer, TA> TA_map = new HashMap<>();                // int id --> TA
    private final Set<TA> TA_set;
    private final List<TA> TA_list;
    private final Map<String, Action> Action_map = new HashMap<>();         // name --> action
    private final List<Action> Action_list;
    private final Map<String, Clock> Clock_map = new HashMap<>();           // name --> clock
    private final Map<Clock, Value> K_map = new HashMap<>();
    private final List<Clock> Clock_list;
    private final ConcreteState init_concrete_state;
    private final ClockValuation init_clock_valuation;
    private final ZoneState init_DBM_state;
    private final LocationVector init_loc_vec;

    public NTA(TA... TAs){
        this(List.of(TAs));
    }

    public NTA(Collection<TA> TAs){
        this.init_loc_vec = new LocationVector();
        for(TA current_TA : TAs){
            // Map
            this.TA_map.put(current_TA.id, current_TA);
            this.Action_map.putAll(current_TA.Sigma);
            this.Clock_map.putAll(current_TA.X);
            this.K_map.putAll(current_TA.K);
            // init_loc_vec
            init_loc_vec.put(current_TA, current_TA.q0);
        }
        this.TA_list = List.copyOf(TAs);
        this.TA_set = Set.copyOf(TAs);
        this.Action_list = List.copyOf(this.Action_map.values());
        this.Clock_list = List.copyOf(this.Clock_map.values());
        if (!TeacherSetting.hasDBMClockIndexConfigured()) {
            TeacherSetting.setDBMClockIndex(this.Clock_list);
        }
        this.init_clock_valuation = new ClockValuation(this.Clock_map.values());
        this.init_concrete_state = new ConcreteState(this, init_loc_vec, null, null, this.init_clock_valuation);
        this.init_DBM_state = new ZoneState(init_loc_vec, null, null);
        this.init_DBM_state.up();
        this.init_DBM_state.applyInvariants();
    }

    public Action getAction(String name){
        return this.Action_map.get(name);
    }

    public Action getAction(int index){
        return this.Action_list.get(index);
    }

    public Clock getClock(String name){
        return this.Clock_map.get(name);
    }

    public Clock getClock(int index){
        return this.Clock_list.get(index);
    }

    public Set<Clock> getAllClocks(){
        List<Clock> ordered = new ArrayList<>(this.Clock_map.values());
        ordered.sort(Comparator
                .comparing(Clock::getTA_name)
                .thenComparing(Clock::getName));
        return new LinkedHashSet<>(ordered);
    }


    public List<Clock> getClockList() {
        return this.Clock_list;
    }

    public Set<Action> getAllActions(){
        List<Action> ordered = new ArrayList<>(this.Action_map.values());
        ordered.sort(Comparator.comparing(Action::getName));
        return new LinkedHashSet<>(ordered);
    }


    public boolean ifContain(Location location){
        return this.TA_set.contains(location.getOwnerTa());
    }

    public Value getK(Clock clock){
        return this.K_map.getOrDefault(clock, Value.ZERO);
    }

    /*******
     * More concisely adds reset information to a word, without considering whether the word is legal, just adding information
     * Currently only considers complete deterministic cases
     * @param wl
     * @return
     */
    public ResetLogicalTimedWord addResetInformationSimple(LogicalTimedWord wl){
        if(wl.getLength() == 0)
            return new ResetLogicalTimedWord();
        ResetLogicalTimedWord outcome = new ResetLogicalTimedWord();
        List<Location> current_loc = new LinkedList();
        for(TA ta : this.TA_list){
            current_loc.add(ta.getQ0());
        }
        for(LogicalTimedAction al : wl){
            Action action = al.getAction();
            ClockValuation clockValuation = al.getClockValuation();
            Set<Clock> reset = new HashSet<>();
            for(int i=0;i<this.TA_list.size();i++){
                if(TA_list.get(i).containsAction(action)){
                    Location loc = current_loc.get(i);
                    Transition transition = loc.getPassableTransition(action, clockValuation);
                    current_loc.set(i, transition.getTarget());
                    reset.addAll(transition.getReset());
                }
            }
            outcome.attend(new ResetLogicalTimedAction(action, clockValuation, reset));
        }
        return outcome;
    }


    /*********
     * Converts a LogicalTimedWord into a ResetLogicalTimedWord
     * Does not require determinism, uses depth-first search
     */
    public Triplet<Boolean,ResetLogicalTimedWord,Trace> addResetInformation(LogicalTimedWord wl){
        if(wl.getLength() == 0)
            return new Triplet<>(true,new ResetLogicalTimedWord(),new Trace(this));

        Triplet<Boolean,List<Set<Clock>>,List<SynchronizedTransition>> outcome = this.addResetInformationCore(wl, 0, this.init_loc_vec, new ClockValuation(this.Clock_list));
        ResetLogicalTimedWord wrl = new ResetLogicalTimedWord();
        int i = 0;
        for(Set<Clock> reset : outcome.p1()){
            LogicalTimedAction al = wl.getTimedAction(i);
            ResetLogicalTimedAction arl = new ResetLogicalTimedAction(al.getAction(),al.getClockValuation(),reset);
            wrl.attend(arl);
            i++;
        }
        return new Triplet(outcome.p0(),wrl, new Trace(this,outcome.p2()));
    }


    private Triplet<Boolean,List<Set<Clock>>,List<SynchronizedTransition>> addResetInformationCore(LogicalTimedWord wl, int depth, LocationVector current_loc, ClockValuation last_v){
        LogicalTimedAction al = wl.getTimedAction(depth);
        ClockValuation start_v = al.getClockValuation();
        Action action = al.getAction();
        if(current_loc.ifSatisfyInvariant(start_v) && last_v.delayEquals(start_v)){ // last_v is the start time of current location, determined by previous node's time and whether transition resets, need to check if current action can be reached from this start time
            List<SynchronizedTransition> outgoingTransitions = current_loc.outTransitions(action); // Only outgoingTransitions filtered by action
            List<Set<Clock>> reset_list = new LinkedList<>();
            List<SynchronizedTransition> trans_list = new LinkedList<>();
            Set<Clock> new_reset = null;
            SynchronizedTransition new_trans = null;
            boolean first = true;
            for(SynchronizedTransition trans : outgoingTransitions){ // If complete, only one transition can pass here
                if(trans.canPass(start_v)){
                    Set<Clock> reset = trans.getReset();
                    ClockValuation next_v = start_v.reset(reset);
                    LocationVector next_loc = trans.getTargetVector();
                    if(next_loc.ifSatisfyInvariant(next_v)){
                        if(depth == wl.getLength()-1)
                            return new Triplet<>(true, List.of(reset), List.of(trans));
                        else {
                            Triplet<Boolean, List<Set<Clock>>, List<SynchronizedTransition>> outcome = this.addResetInformationCore(wl, depth + 1, next_loc, next_v);
                            if (outcome.p0()) {
                                List<Set<Clock>> new_reset_list = new LinkedList<>();
                                new_reset_list.add(reset);
                                new_reset_list.addAll(outcome.p1());
                                List<SynchronizedTransition> new_trans_list = new LinkedList<>();
                                new_trans_list.add(trans);
                                new_trans_list.addAll(outcome.p2());
                                return new Triplet<>(true, new_reset_list, new_trans_list);
                            } else {
                                if (first) {
                                    reset_list = outcome.p1();
                                    new_reset = reset;
                                    trans_list = outcome.p2();
                                    new_trans = trans;
                                    first = false;
                                } else {
                                    if (reset_list.size() < outcome.p1().size()) {
                                        reset_list = outcome.p1();
                                        new_reset = reset;
                                        trans_list = outcome.p2();
                                        new_trans = trans;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(first)
                return new Triplet<>(false, new LinkedList<>(), new LinkedList<>());
            else{
                List<Set<Clock>> new_reset_list = new LinkedList<>();
                new_reset_list.add(new_reset);
                new_reset_list.addAll(reset_list);
                List<SynchronizedTransition> new_trans_list = new LinkedList<>();
                new_trans_list.add(new_trans);
                new_trans_list.addAll(trans_list);
                return new Triplet<>(false, new_reset_list, new_trans_list);
            }
        } else
            return new Triplet<>(false, new LinkedList<>(), new LinkedList<>());
    }


    /***
     * Determines if NRTA contains a ResetLogicalTimedWord
     * Depth-first search
     * This function does not require deterministic and complete
     * @param wrl
     * @return
     */
//    public boolean ifContain(ResetLogicalTimedWord wrl){
//        if(wrl.isEmpty())
//            return this.init_loc_vec.isAccepting();
//        else
//            return this.ifContainCore(this.init_loc_vec, wrl, 0);
//    }
//
//    private boolean ifContainCore(LocationVector current_loc_vec, ResetLogicalTimedWord wrl, int depth){
//        ResetLogicalTimedAction arl = wrl.get(depth);
//        if(current_loc_vec.ifSatisfyInvariant(arl.getClockValuation())){
//            List<SynchronizedTransition> out_trans_list = current_loc_vec.outTransitions(arl);
//            ClockValuation next = arl.getNextClockValuation();
//            if(depth == wrl.getLength() - 1){
//                for(SynchronizedTransition out_trans : out_trans_list){
//                    if(out_trans.getTargetVector().ifSatisfyInvariant(next) && out_trans.getTargetVector().isAccepting())
//                        return true;
//                }
//                return false;
//            } else {
//                for(SynchronizedTransition out_trans : out_trans_list){
//                    LocationVector next_loc_vec = out_trans.getTargetVector();
//                    if(out_trans.getTargetVector().ifSatisfyInvariant(next)) {
//                        if (this.ifContainCore(next_loc_vec, wrl, depth + 1))
//                            return true;
//                    }
//                }
//                return false;
//            }
//        } else {
//            return false;
//        }
//    }

    /***
     * Determines if NRTA contains a LogicalTimedWord
     * Depth-first search
     * This function does not require deterministic and complete
     * @param lw
     * @return
     */
    public Pair<Boolean,ResetLogicalTimedWord> ifContain(LogicalTimedWord lw){
        if(lw.isEmpty()) {
            return new Pair<>(this.init_loc_vec.isAccepting(), new ResetLogicalTimedWord());
        } else {
            ResetLogicalTimedWord rlw = new ResetLogicalTimedWord();
            return this.ifContainCore(this.init_loc_vec, lw, rlw);
        }
    }

// Parameters are current location, complete logical-timed-word and incrementally generated reset-logical-timed-word; for each iteration, infer which la in lw to find next from rlw;
// Check transitions from current location that satisfy this la, combine current la's time and transition's reset to get next location's time and generate new rlw (both need reset information)
// Then iterate again until only one remains, at which point need to check if final location is accepting
    private Pair<Boolean,ResetLogicalTimedWord> ifContainCore(LocationVector current_loc_vec, LogicalTimedWord lw, ResetLogicalTimedWord rlw){
        int depth = rlw.getLength();
        LogicalTimedAction la = lw.getTimedAction(depth);
        if(current_loc_vec.ifSatisfyInvariant(la.getClockValuation())){
            List<SynchronizedTransition> out_trans_list = current_loc_vec.outTransitions(la);
            if(depth == lw.getLength() - 1){
                int i = 0;
                for(SynchronizedTransition out_trans : out_trans_list){
                    i++;
                    ClockValuation next = la.getNextClockValuation(out_trans.getReset());
                    if(out_trans.getTargetVector().ifSatisfyInvariant(next)) {
                        if(out_trans.getTargetVector().isAccepting()) {
                            rlw.attend(lw.getTimedAction(depth), out_trans.getReset());
                            return new Pair<>(true, rlw);
                        } else if(i==out_trans_list.size()){
                            rlw.attend(lw.getTimedAction(depth), out_trans.getReset());
                            return new Pair<>(false, rlw);
                        }
                    }
                }
                // Only non-complete TAs reach this branch
                System.out.print("WARNING: the TA is not complete!");
                return new Pair<>(false, rlw);
            } else {
                ResetLogicalTimedWord fall_path = null;
                for(SynchronizedTransition out_trans : out_trans_list){
                    LocationVector next_loc_vec = out_trans.getTargetVector();
                    ClockValuation next = la.getNextClockValuation(out_trans.getReset());
                    if(out_trans.getTargetVector().ifSatisfyInvariant(next)) {
                        ResetLogicalTimedWord rlw_copy = rlw.commonClone();
                        rlw_copy.attend(lw.getTimedAction(depth), out_trans.getReset());
                        Pair<Boolean,ResetLogicalTimedWord> outcome = this.ifContainCore(next_loc_vec, lw, rlw_copy);
                        if(outcome.p0())
                            return outcome;
                        else{
                            if(fall_path==null)
                                fall_path = outcome.p1();
                            else if (fall_path.getLength()<outcome.p1().getLength())
                                fall_path = outcome.p1();
                        }
                    }
                }
                return new Pair<>(false, fall_path);
            }
        } else {
            return new Pair<>(false, rlw);
        }
    }


    public String toString(){
        if(this.TA_list.size()==1)
            return this.TA_list.get(0).getMainDetail();
        List<String> outcome = new LinkedList<>();
        for(TA ta : this.TA_list)
            outcome.add(ta.getMainDetail());
        return outcome.toString();
    }

    /****
     * Note: this will modify the object
     * @param ctx
     * @return
     */
    public NTA makeComplete(Context ctx){
        for(TA ta : this.TA_list)
            ta.makeComplete(ctx);
        return this;
    }

    @Override
    public Iterator<TA> iterator() {
        return this.TA_list.iterator();
    }

    public boolean existsAcceptedWordProjectingTo(RTARegionWord word,
                                                  Set<Action> idAlphabet,
                                                  Clock projectionClock,
                                                  int maxLenInclusive) {
        return this.existsAcceptedWordProjectingToWithWitness(word, idAlphabet, projectionClock, maxLenInclusive).p0();
    }

    public Pair<Boolean, List<SynchronizedTransition>> existsAcceptedWordProjectingToWithWitness(RTARegionWord word,
                                                                                                 Set<Action> idAlphabet,
                                                                                                 Clock projectionClock,
                                                                                                 int maxLenInclusive) {
        if (word == null) {
            return new Pair<>(false, Collections.emptyList());
        }
        if (idAlphabet == null || projectionClock == null) {
            return new Pair<>(false, Collections.emptyList());
        }
        if (!this.Clock_map.containsValue(projectionClock)) {
            return new Pair<>(false, Collections.emptyList());
        }

        final int targetLen = word.getLength();
        if (targetLen == 0 && this.init_loc_vec.isAccepting()) {
            return new Pair<>(true, Collections.emptyList());
        }

        Deque<SearchNode> queue = new ArrayDeque<>();
        Map<SearchKey, List<Zone>> visited = new HashMap<>();
        SearchNode init = new SearchNode(this.init_loc_vec, this.init_DBM_state.getZone().copy(), 0, 0, null, null);
        queue.addLast(init);
        this.addVisited(visited, init.key(), init.zone);

        List<Action> orderedActions = new ArrayList<>(this.Action_map.values());
        orderedActions.sort(Comparator.comparing(Action::getName));

        while (!queue.isEmpty()) {
            SearchNode current = queue.removeFirst();
            if (maxLenInclusive >= 0 && current.length >= maxLenInclusive) {
                continue;
            }
            for (Action action : orderedActions) {
                boolean inProjectionAlphabet = idAlphabet.contains(action);
                if (inProjectionAlphabet) {
                    if (current.pos >= targetLen) {
                        continue;
                    }
                    if (!action.equals(word.getTimedAction(current.pos).getAction())) {
                        continue;
                    }
                }

                Constraint extraConstraint = null;
                if (inProjectionAlphabet) {
                    RTARegionAction expected = word.getTimedAction(current.pos);
                    extraConstraint = this.intervalConstraintOnClock(
                            projectionClock,
                            expected.getLower(),
                            expected.isLowerClosed(),
                            expected.getUpper(),
                            expected.isUpperClosed());
                }

                List<SynchronizedTransition> syncTransitions = current.locationVector.outTransitions(action);
                for (SynchronizedTransition syncTransition : syncTransitions) {
                    SearchStepResult next = this.applySynchronizedTransition(current, syncTransition, extraConstraint);
                    if (next == null) {
                        continue;
                    }
                    int nextPos = inProjectionAlphabet ? current.pos + 1 : current.pos;
                    int nextLen = current.length + 1;
                    if (maxLenInclusive >= 0 && nextLen > maxLenInclusive) {
                        continue;
                    }
                    SearchNode nextNode = new SearchNode(
                            next.locationVector,
                            next.zone,
                            nextPos,
                            nextLen,
                            current,
                            syncTransition);

                    if (nextPos == targetLen && next.locationVector.isAccepting()) {
                        return new Pair<>(true, this.reconstructWitness(nextNode));
                    }
                    SearchKey key = nextNode.key();
                    if (this.isDominated(visited, key, nextNode.zone)) {
                        continue;
                    }
                    this.addVisited(visited, key, nextNode.zone);
                    queue.addLast(nextNode);
                }
            }
        }
        return new Pair<>(false, Collections.emptyList());
    }

    private Constraint intervalConstraintOnClock(Clock clock,
                                                 Value lower,
                                                 boolean lowerClosed,
                                                 Value upper,
                                                 boolean upperClosed) {
        Constraint constraint = new Constraint();
        if (lowerClosed) {
            constraint.addAtomConstraint(AtomConstraint.generateGe(clock, lower));
        } else {
            constraint.addAtomConstraint(AtomConstraint.generateGt(clock, lower));
        }
        if (upperClosed) {
            constraint.addAtomConstraint(AtomConstraint.generateLe(clock, upper));
        } else {
            constraint.addAtomConstraint(AtomConstraint.generateLt(clock, upper));
        }
        return constraint;
    }

    private SearchStepResult applySynchronizedTransition(SearchNode current,
                                                         SynchronizedTransition syncTransition,
                                                         Constraint extraConstraint) {
        Zone nextZone = current.zone.copy();
        LocationVector targetVector = new LocationVector();
        List<Constraint> targetInvariants = new LinkedList<>();

        for (Map.Entry<TA, Transition> entry : syncTransition.getTransitions().entrySet()) {
            Transition transition = entry.getValue();
            nextZone.and(transition.getGuard());
            Location target = transition.getTarget();
            targetInvariants.add(target.getInvariant());
            targetVector.put(entry.getKey(), target);
        }
        if (extraConstraint != null) {
            nextZone.and(extraConstraint);
        }
        nextZone.canonical();
        if (!nextZone.isConsistent()) {
            return null;
        }

        for (Clock clock : syncTransition.getReset()) {
            nextZone.reset(clock);
        }

        for (Constraint invariant : targetInvariants) {
            nextZone.and(invariant);
        }
        nextZone.canonical();
        if (!nextZone.isConsistent()) {
            return null;
        }

        nextZone.up();
        for (Constraint invariant : targetInvariants) {
            nextZone.and(invariant);
        }
        nextZone.canonical();
        if (!nextZone.isConsistent()) {
            return null;
        }

        return new SearchStepResult(targetVector, nextZone);
    }

    private boolean isDominated(Map<SearchKey, List<Zone>> visited, SearchKey key, Zone candidate) {
        List<Zone> known = visited.get(key);
        if (known == null) {
            return false;
        }
        for (Zone zone : known) {
            if (zone.includes(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void addVisited(Map<SearchKey, List<Zone>> visited, SearchKey key, Zone candidate) {
        List<Zone> known = visited.computeIfAbsent(key, ignored -> new LinkedList<>());
        Iterator<Zone> iterator = known.iterator();
        while (iterator.hasNext()) {
            Zone zone = iterator.next();
            if (candidate.includes(zone)) {
                iterator.remove();
            }
        }
        known.add(candidate);
    }

    private List<SynchronizedTransition> reconstructWitness(SearchNode node) {
        LinkedList<SynchronizedTransition> path = new LinkedList<>();
        SearchNode current = node;
        while (current != null && current.viaTransition != null) {
            path.addFirst(current.viaTransition);
            current = current.parent;
        }
        return path;
    }

    private static final class SearchStepResult {
        private final LocationVector locationVector;
        private final Zone zone;

        private SearchStepResult(LocationVector locationVector, Zone zone) {
            this.locationVector = locationVector;
            this.zone = zone;
        }
    }

    private static final class SearchNode {
        private final LocationVector locationVector;
        private final Zone zone;
        private final int pos;
        private final int length;
        private final SearchNode parent;
        private final SynchronizedTransition viaTransition;

        private SearchNode(LocationVector locationVector,
                           Zone zone,
                           int pos,
                           int length,
                           SearchNode parent,
                           SynchronizedTransition viaTransition) {
            this.locationVector = locationVector;
            this.zone = zone;
            this.pos = pos;
            this.length = length;
            this.parent = parent;
            this.viaTransition = viaTransition;
        }

        private SearchKey key() {
            return new SearchKey(this.locationVector, this.pos, this.length);
        }
    }

    private static final class SearchKey {
        private final LocationVector locationVector;
        private final int pos;
        private final int length;

        private SearchKey(LocationVector locationVector, int pos, int length) {
            this.locationVector = locationVector;
            this.pos = pos;
            this.length = length;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SearchKey key)) return false;
            return this.pos == key.pos
                    && this.length == key.length
                    && this.locationVector.equals(key.locationVector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.locationVector, this.pos, this.length);
        }
    }
}
