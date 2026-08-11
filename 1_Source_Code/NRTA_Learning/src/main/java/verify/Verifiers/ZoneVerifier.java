package verify.Verifiers;

import com.microsoft.z3.*;
import language.words.ResetDelayTimedWord;
import lombok.Getter;
import run.Trace;
import run.zone.DBM;
import run.zone.Zone;
import run.parallel_elements.LocationVector;
import run.parallel_elements.SynchronizedTransition;
import run.state.ZoneState;
import ta.*;

import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;
import tools.ExtendSolver;
import learning.teachers.TeacherSetting;
import verify.StateHistory.StateHistory;

import java.util.*;

@Getter
public class ZoneVerifier extends Verifier {
    private static final class ExtrapolationProfile {
        private final TeacherSetting.ExtrapolationType type;
        private final Map<Clock, Value> lowerBounds;
        private final Map<Clock, Value> upperBounds;
        private final Map<Clock, Value> mBounds;

        private ExtrapolationProfile(TeacherSetting.ExtrapolationType type,
                                     Map<Clock, Value> lowerBounds,
                                     Map<Clock, Value> upperBounds,
                                     Map<Clock, Value> mBounds) {
            this.type = type;
            this.lowerBounds = lowerBounds;
            this.upperBounds = upperBounds;
            this.mBounds = mBounds;
        }
    }

    private static final class Witness {
        private final ZoneState state;
        private final boolean acceptingInNta1;

        private Witness(ZoneState state, boolean acceptingInNta1) {
            this.state = state;
            this.acceptingInNta1 = acceptingInNta1;
        }
    }

    private static final class ConcreteWitness {
        private final Witness witness;
        private final Trace trace;
        private final ResetDelayTimedWord resetDelayTimedWord;

        private ConcreteWitness(Witness witness, Trace trace, ResetDelayTimedWord resetDelayTimedWord) {
            this.witness = witness;
            this.trace = trace;
            this.resetDelayTimedWord = resetDelayTimedWord;
        }
    }

    private final Context ctx;
    private final Solver solver;

    public ZoneVerifier(Context ctx){
        this.ctx = ctx;
        this.solver = ctx.mkSolver();
        ExtendSolver.applyRandomSeed(ctx, this.solver);
    }

    public boolean satisfiable(ZoneState zoneState){
        Zone dbm = zoneState.getZone();
        List<Clock> clock_list = zoneState.getZone().getClockList();
        boolean isSatisfiable;
        solver.push();
        int size = dbm.getSize();
        for(int i=0;i<size;i++){
            for (int j=0;j<size;j++){
                if(i == j)
                    continue; // Equal to itself, nothing to say
                Value upper_bound = dbm.getValue(i,j);
                boolean closed = dbm.getClosed(i,j);
                if(upper_bound.isInfinite())
                    continue; // Less than infinity, must be true
                else if(upper_bound.isMinus_infinite()){
                    solver.pop();
                    return false; // Clock difference less than negative infinity is impossible, unsatisfiable directly.
                } else {
                    RealExpr clock_real1;
                    RealExpr clock_real2;

                    if(i == 0) clock_real1 = ctx.mkReal(0);
                    else clock_real1 = clock_list.get(i).getSmt_exp();

                    if(j == 0) clock_real2 = ctx.mkReal(0);
                    else clock_real2 = clock_list.get(j).getSmt_exp();

                    BoolExpr element_expr;
                    if(closed == AtomConstraint.LESS){
                        element_expr = ctx.mkGt(
                                upper_bound.getValue().getSmt(ctx),
                                ctx.mkSub(clock_real1,clock_real2)
                        );
                    } else {
                        element_expr = ctx.mkGe(
                                upper_bound.getValue().getSmt(ctx),
                                ctx.mkSub(clock_real1,clock_real2)
                        );
                    }
                    solver.add(element_expr);
                }
            }
        }

        isSatisfiable = (solver.check() == Status.SATISFIABLE);
        solver.pop();
        return isSatisfiable;
    }

    /*************
     * Check if nta1's language can include nta2
     * Search strategy:
     * 1) Explore reachable DBM states of the composed system by BFS layers.
     * 2) Prune states already covered by StateHistory inclusion.
     * 3) Stop at first witness where nta2 is accepting while nta1 is not.
     *
     * Return:
     * - (true, null, null): inclusion holds
     * - (false, counterexampleTraceOrNull, acceptingInNta1): inclusion violated
     *   where acceptingInNta1 is false at the witness state.
     * @param nta1
     * @param nta2
     * @return
     */
    @Override
    public Triplet<Boolean, Trace, Boolean> checkLanguageInclude(NTA nta1, NTA nta2){
        List<TA> nta1_loc_list = nta1.getTA_list();
        List<TA> nta2_loc_list = nta2.getTA_list();
        List<TA> composed = new LinkedList<>();
        composed.addAll(nta1_loc_list);
        composed.addAll(nta2_loc_list);
        TeacherSetting.setDBMClockIndex(collectClocks(composed));
        NTA composed_nta = new NTA(composed);
        ExtrapolationProfile extrapolationProfile = buildExtrapolationProfile(composed);

        StateHistory<ZoneState> passed = TeacherSetting.createStateHistory();
        Set<ZoneState> waiting = new LinkedHashSet<>();
        ZoneState initState = composed_nta.getInit_DBM_state();
        this.extrapolateState(initState, extrapolationProfile);
        waiting.add(initState);
        Set<ZoneState> next = new LinkedHashSet<>();

        while(true){
            List<Witness> witnessCandidates = new ArrayList<>();
            waiting:
            for(ZoneState zoneState : waiting){
                this.extrapolateState(zoneState, extrapolationProfile);
                LocationVector current_loc = zoneState.getLocation_vec();
                // Check if state is satisfiable (generally disabled)
                if(TeacherSetting.VERIFY_DBM_SATISFIABLE_CHECK) {
                    if (!this.satisfiable(zoneState)) {
                        System.out.println(ColoredFront.dealtString("satisfiable_check_failed", Color.Red, Type.Common));
                        continue;
                    }
                }
                // Check if reached a location with inconsistent accepting states
                boolean accepting_1 = current_loc.projectionAccepting(nta1_loc_list);
                boolean accepting_2 = current_loc.projectionAccepting(nta2_loc_list);
                if(!accepting_1 && accepting_2){
                    witnessCandidates.add(new Witness(zoneState, accepting_1));
                    continue;
                }

                // Inclusion-based pruning: if an old state already covers current DBM, skip expansion.
                if (passed.isIncluded(zoneState)) {
                    continue;
                }
                for (ZoneState successor : zoneState.getAllNext()) {
                    this.extrapolateState(successor, extrapolationProfile);
                    next.add(successor);
                }
                passed.add(zoneState);
            }
            if (!witnessCandidates.isEmpty()) {
                Pair<Witness, Trace> selected = this.selectConcretizableWitness(witnessCandidates, composed_nta);
                if (selected != null) {
                    return new Triplet<>(
                            false,
                            selected.p1(),
                            selected.p0().acceptingInNta1
                    );
                }
                // All witness candidates are spurious under current abstraction, block and continue.
                for (Witness witness : witnessCandidates) {
                    passed.add(witness.state);
                }
            }

            if(next.isEmpty()) {
                return new Triplet<>(true, null, null);
            }

            waiting = next;
            next = new LinkedHashSet<>();
        }
    }


    /*************
     * Input here must ensure both NTAs are complete
     * all_accept indicates whether all locations except sink are accepting
     * Search strategy is the same as inclusion, but failure condition is accepting mismatch.
     *
     * Return:
     * - (true, null, null): languages are equivalent
     * - (false, counterexampleTraceOrNull, acceptingInNta1): equivalence violated
     */
    @Override
    public Triplet<Boolean, Trace, Boolean> checkLanguageEquivalence(NTA nta1, NTA nta2){
        List<TA> nta1_loc_list = nta1.getTA_list();
        List<TA> nta2_loc_list = nta2.getTA_list();
        List<TA> composed = new LinkedList<>();
        composed.addAll(nta1_loc_list);
        composed.addAll(nta2_loc_list);
        TeacherSetting.setDBMClockIndex(collectClocks(composed));
        NTA composed_nta = new NTA(composed);
        ExtrapolationProfile extrapolationProfile = buildExtrapolationProfile(composed);

        StateHistory<ZoneState> passed = TeacherSetting.createStateHistory();
        Set<ZoneState> waiting = new LinkedHashSet<>();
        ZoneState initState = composed_nta.getInit_DBM_state();
        this.extrapolateState(initState, extrapolationProfile);
        waiting.add(initState);
        Set<ZoneState> next = new LinkedHashSet<>();

        int i = 0;
        long expandedStates = 0L;
        long prunedByInclusion = 0L;
        long generatedSuccessors = 0L;
        while(true){
            List<Witness> witnessCandidates = new ArrayList<>();
            waiting:
            for(ZoneState zoneState : waiting){
                expandedStates++;
                this.extrapolateState(zoneState, extrapolationProfile);
                LocationVector current_loc = zoneState.getLocation_vec();
                // Check if state is satisfiable (generally disabled)
                if(TeacherSetting.VERIFY_DBM_SATISFIABLE_CHECK) {
                    if (!this.satisfiable(zoneState)) {
                        System.out.println(ColoredFront.dealtString("satisfiable_check_failed", Color.Red, Type.Common));
                        continue;
                    }
                }
                // Check if reached a location with inconsistent accepting states
                boolean accepting_1 = current_loc.projectionAccepting(nta1_loc_list);
                boolean accepting_2 = current_loc.projectionAccepting(nta2_loc_list);
                if(accepting_1 != accepting_2){
                    witnessCandidates.add(new Witness(zoneState, accepting_1));
                    continue;
                }

                // Inclusion-based pruning in the same location partition.
                boolean includedByHistory = passed.isIncluded(zoneState);
                if (includedByHistory) {
                    prunedByInclusion++;
                    continue;
                }
                int before = next.size();
                if(TeacherSetting.VERIFY_ALL_ACCEPT){
                    if(accepting_1) // If both automata reach non-accepting states, continuing is meaningless
                        for (ZoneState successor : zoneState.getAllNext()) {
                            this.extrapolateState(successor, extrapolationProfile);
                            next.add(successor);
                        }
                } else {
                    for (ZoneState successor : zoneState.getAllNext()) {
                        this.extrapolateState(successor, extrapolationProfile);
                        next.add(successor);
                    }
                }
                generatedSuccessors += Math.max(0, next.size() - before);
                passed.add(zoneState);
            }
            if (!witnessCandidates.isEmpty()) {
                Pair<Witness, Trace> selected = this.selectConcretizableWitness(witnessCandidates, composed_nta);
                if (selected != null) {
                    return new Triplet<>(
                            false,
                            selected.p1(),
                            selected.p0().acceptingInNta1
                    );
                }
                // All witness candidates are spurious under current abstraction, block and continue.
                for (Witness witness : witnessCandidates) {
                    passed.add(witness.state);
                }
            }

            if(next.isEmpty()) {
                return new Triplet<>(true, null, null);
            }

            waiting = next;
            next = new LinkedHashSet<>();
            i++;
        }
    }

    @Override
    public Pair<Boolean, Trace> checkLocationReachable(NTA nta, Location location) {
        // Assumption: the given location is an object inside the same NRTA instance.
        // This method checks reachability from the initial DBM state under current global settings.
        if (nta == null || location == null) {
            throw new IllegalArgumentException("nta and location must not be null");
        }
        if (!nta.ifContain(location)) {
            throw new IllegalArgumentException("target location does not belong to the given NRTA");
        }

        StateHistory<ZoneState> passed = TeacherSetting.createStateHistory();
        Set<ZoneState> waiting = new LinkedHashSet<>();
        ExtrapolationProfile extrapolationProfile = buildExtrapolationProfile(nta.getTA_list());
        ZoneState initState = nta.getInit_DBM_state();
        this.extrapolateState(initState, extrapolationProfile);
        waiting.add(initState);
        Set<ZoneState> next = new LinkedHashSet<>();

        while (true) {
            for (ZoneState zoneState : waiting) {
                this.extrapolateState(zoneState, extrapolationProfile);
                LocationVector currentLoc = zoneState.getLocation_vec();

                if (TeacherSetting.VERIFY_DBM_SATISFIABLE_CHECK && !this.satisfiable(zoneState)) {
                    System.out.println(ColoredFront.dealtString("satisfiable_check_failed", Color.Red, Type.Common));
                    continue;
                }

                // Reachability witness found.
                if (location.equals(currentLoc.getLocation(location.getOwnerTa()))) {
                    return new Pair<>(true, TeacherSetting.VERIFY_STORE_TRACE ? zoneState.getTrace(nta) : null);
                }

                // Covered by historical state, no need to expand.
                if (passed.isIncluded(zoneState)) {
                    continue;
                }
                for (ZoneState successor : zoneState.getAllNext()) {
                    this.extrapolateState(successor, extrapolationProfile);
                    next.add(successor);
                }
                passed.add(zoneState);
            }

            if (next.isEmpty()) {
                return new Pair<>(false, null);
            }
            waiting = next;
            next = new LinkedHashSet<>();
        }
    }

    private ExtrapolationProfile buildExtrapolationProfile(Collection<TA> tas) {
        TeacherSetting.ExtrapolationType type = TeacherSetting.VERIFY_EXTRAPOLATION_TYPE;
        Map<Clock, Value> lower = new HashMap<>();
        Map<Clock, Value> upper = new HashMap<>();
        Map<Clock, Value> m = new HashMap<>();
        for (Clock clock : collectClocks(new ArrayList<>(tas))) {
            lower.put(clock, Value.ZERO);
            upper.put(clock, Value.ZERO);
            m.put(clock, Value.ZERO);
        }

        for (TA ta : tas) {
            for (Location location : ta.getQ().values()) {
                this.accumulateBoundsFromConstraint(location.getInvariant(), lower, upper, m);
            }
            for (Transition transition : ta.getDelta().values()) {
                this.accumulateBoundsFromConstraint(transition.getGuard(), lower, upper, m);
            }
        }
        return new ExtrapolationProfile(type, lower, upper, m);
    }

    private void accumulateBoundsFromConstraint(Constraint constraint,
                                                Map<Clock, Value> lower,
                                                Map<Clock, Value> upper,
                                                Map<Clock, Value> m) {
        for (AtomConstraint atom : constraint.getAtomConstraints()) {
            Value bound = atom.getUpperbound();
            if (bound.isInfinite() || bound.isMinus_infinite()) {
                continue;
            }
            Clock c1 = atom.getClock1();
            Clock c2 = atom.getClock2();
            Value nonNegativeUpper = this.clampNonNegative(bound);
            Value nonNegativeLower = this.clampNonNegative(Value.negative(bound));
            Value absBound = this.absFinite(bound);

            if (!c1.isZeroClock() && c2.isZeroClock()) {
                maximize(upper, c1, nonNegativeUpper);
                maximize(m, c1, absBound);
            } else if (c1.isZeroClock() && !c2.isZeroClock()) {
                maximize(lower, c2, nonNegativeLower);
                maximize(m, c2, absBound);
            } else if (!c1.isZeroClock() && !c2.isZeroClock()) {
                // Conservative handling for diagonal constraints.
                maximize(upper, c1, nonNegativeUpper);
                maximize(lower, c2, nonNegativeLower);
                maximize(m, c1, absBound);
                maximize(m, c2, absBound);
            }
        }
    }

    private void extrapolateState(ZoneState state, ExtrapolationProfile profile) {
        if (profile.type == TeacherSetting.ExtrapolationType.NONE) {
            return;
        }
        Zone zone = state.getZone();
        if (!(zone instanceof DBM dbm)) {
            throw new IllegalStateException(
                    "Extrapolation currently supports EXACT_DBM only; current zone implementation is "
                            + zone.getClass().getSimpleName());
        }
        dbm.extrapolate(profile.lowerBounds, profile.upperBounds, profile.mBounds, profile.type);
        dbm.canonical();
    }

    private static void maximize(Map<Clock, Value> map, Clock clock, Value candidate) {
        Value old = map.get(clock);
        if (old == null || Value.gt(candidate, old)) {
            map.put(clock, candidate);
        }
    }

    private Value absFinite(Value value) {
        if (Value.lt(value, Value.ZERO)) {
            return Value.negative(value);
        }
        return value;
    }

    private Value clampNonNegative(Value value) {
        if (value == null || value.isInfinite() || value.isMinus_infinite()) {
            return Value.ZERO;
        }
        if (Value.lt(value, Value.ZERO)) {
            return Value.ZERO;
        }
        return value;
    }

    private static List<Clock> collectClocks(List<TA> tas) {
        Set<Clock> clocks = new LinkedHashSet<>();
        for (TA ta : tas) {
            clocks.addAll(ta.getX().values());
        }
        return new ArrayList<>(clocks);
    }

    private Witness selectBestWitness(List<Witness> candidates) {
        return candidates.stream()
                .min(this::compareWitness)
                .orElseThrow(() -> new IllegalStateException("witness candidates should not be empty"));
    }

    private Pair<Witness, Trace> selectConcretizableWitness(List<Witness> candidates, NTA composedNta) {
        if (!TeacherSetting.VERIFY_STORE_TRACE) {
            Witness best = this.selectBestWitness(candidates);
            return new Pair<>(best, null);
        }
        List<ConcreteWitness> concreteWitnesses = new ArrayList<>();
        int spuriousSkipped = 0;
        List<Witness> ordered = new ArrayList<>(candidates);
        ordered.sort(this::compareWitness);
        for (Witness witness : ordered) {
            Trace trace = witness.state.getTrace(composedNta);
            try {
                // Validate that this abstract witness has a concrete timed realization.
                ResetDelayTimedWord resetDelayTimedWord = trace.getResetDelayTimedWord(this.ctx);
                concreteWitnesses.add(new ConcreteWitness(witness, trace, resetDelayTimedWord));
            } catch (RuntimeException ex) {
                spuriousSkipped++;
            }
        }
        if (!concreteWitnesses.isEmpty()) {
            concreteWitnesses.sort(this::compareConcreteWitness);
            ConcreteWitness selected = concreteWitnesses.get(0);
            return new Pair<>(selected.witness, selected.trace);
        }
        return null;
    }

    private int compareConcreteWitness(ConcreteWitness left, ConcreteWitness right) {
        int leftDepth = this.traceDepth(left.witness.state);
        int rightDepth = this.traceDepth(right.witness.state);
        if (leftDepth != rightDepth) {
            return Integer.compare(leftDepth, rightDepth);
        }
        int cmpWord = left.resetDelayTimedWord.toDelayTimedWord().toString()
                .compareTo(right.resetDelayTimedWord.toDelayTimedWord().toString());
        if (cmpWord != 0) {
            return cmpWord;
        }
        int cmpResetWord = left.resetDelayTimedWord.toString().compareTo(right.resetDelayTimedWord.toString());
        if (cmpResetWord != 0) {
            return cmpResetWord;
        }
        int cmpKind = Boolean.compare(left.witness.acceptingInNta1, right.witness.acceptingInNta1);
        if (cmpKind != 0) {
            return cmpKind;
        }
        return this.compareWitness(left.witness, right.witness);
    }

    private int compareWitness(Witness left, Witness right) {
        int leftDepth = this.traceDepth(left.state);
        int rightDepth = this.traceDepth(right.state);
        if (leftDepth != rightDepth) {
            return Integer.compare(leftDepth, rightDepth);
        }
        String leftLoc = left.state.getLocation_vec().toString();
        String rightLoc = right.state.getLocation_vec().toString();
        int cmpLoc = leftLoc.compareTo(rightLoc);
        if (cmpLoc != 0) {
            return cmpLoc;
        }
        String leftActions = this.traceActionSignature(left.state);
        String rightActions = this.traceActionSignature(right.state);
        int cmpActions = leftActions.compareTo(rightActions);
        if (cmpActions != 0) {
            return cmpActions;
        }
        String leftTransitions = this.traceTransitionSignature(left.state);
        String rightTransitions = this.traceTransitionSignature(right.state);
        return leftTransitions.compareTo(rightTransitions);
    }

    private int traceDepth(ZoneState state) {
        int depth = 0;
        ZoneState current = state;
        while (current != null && current.getLast_state() != null) {
            depth++;
            current = (ZoneState) current.getLast_state();
        }
        return depth;
    }

    private String traceActionSignature(ZoneState state) {
        if (!TeacherSetting.VERIFY_STORE_TRACE) {
            return "";
        }
        List<String> actions = new ArrayList<>();
        ZoneState current = state;
        while (current != null && current.getLast_state() != null) {
            SynchronizedTransition transition = current.getLast_transitions();
            String actionName = transition == null ? "" : transition.getAction().getName();
            actions.add(actionName);
            current = (ZoneState) current.getLast_state();
        }
        Collections.reverse(actions);
        return String.join(",", actions);
    }

    private String traceTransitionSignature(ZoneState state) {
        if (!TeacherSetting.VERIFY_STORE_TRACE) {
            return "";
        }
        List<String> transitions = new ArrayList<>();
        ZoneState current = state;
        while (current != null && current.getLast_state() != null) {
            SynchronizedTransition synchronizedTransition = current.getLast_transitions();
            transitions.add(this.synchronizedTransitionKey(synchronizedTransition));
            current = (ZoneState) current.getLast_state();
        }
        Collections.reverse(transitions);
        return String.join(";", transitions);
    }

    private String synchronizedTransitionKey(SynchronizedTransition synchronizedTransition) {
        if (synchronizedTransition == null) {
            return "";
        }
        List<Map.Entry<TA, Transition>> entries = new ArrayList<>(synchronizedTransition.getTransitions().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<TA, Transition> entry : entries) {
            Transition transition = entry.getValue();
            parts.add(entry.getKey().getName()
                    + ":"
                    + transition.getSource().getName()
                    + "->"
                    + transition.getTarget().getName()
                    + "@"
                    + transition.getAction().getName()
                    + "@"
                    + transition.getGuard().toStringDisplay()
                    + "@"
                    + this.resetKey(transition.getReset()));
        }
        return synchronizedTransition.getAction().getName() + "[" + String.join("|", parts) + "]";
    }

    private String resetKey(Set<Clock> reset) {
        List<String> names = new ArrayList<>();
        for (Clock clock : reset) {
            names.add(clock.getTA_name() + "." + clock.getName());
        }
        Collections.sort(names);
        return String.join(",", names);
    }

}
