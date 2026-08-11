package learning.learners.composition_table.multiple_RTA_learners.table;


import com.microsoft.z3.*;
import exception.ValueExceptions;
import language.actions.RTARegionAction;
import language.words.RTARegionWord;
import learning.learners.composition_table.TriBool;
import learning.learners.composition_table.multiple_RTA_learners.ExptHelp;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementRecord;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementNode;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementTree;
import lombok.AllArgsConstructor;
import lombok.Getter;
import ta.*;
import tools.ExtendSolver;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import tools.FormatOutput.ExactRationalFormat;

@AllArgsConstructor
public class Table implements Iterable<Cell>{
    public record SmtSolveSnapshot(
            String solverKind,
            String status,
            String tableShape,
            String solverInput,
            String closedConstraint,
            String consistentConstraint,
            String sinkConstraint,
            String model,
            String solvedTableCsv
    ) {}

    private final ExtendSolver slv;
    private final Clock clock;
    private final Set<Action> actions;
    private final String hypothesisName;
    private final Clock h_clock;
    @Getter
    private final ElementTree elementTree;
    private final Set<Prefix> s_area;
    private final Set<Prefix> r_area;
    private final Map<String, Prefix> prefixIndex;
    private final List<Suffix> e_area;
    private final Set<RTARegionWord> certainNegativeCounterExamples;
    private final Set<Cell> pendingMqCells;
    private final Prefix epsilon_prefix;
    @Getter
    private RTA interHypothesis;
    @Getter
    private SmtSolveSnapshot lastSmtSolveSnapshot;

    public Table(Clock clock, Set<Action> actions, String hypothesisName, Clock h_clock, ExtendSolver slv){
        this.slv = slv;
        this.clock = clock;
        this.actions = actions;
        this.hypothesisName = hypothesisName;
        this.h_clock = h_clock;
        this.elementTree = new ElementTree(slv, clock);
        this.s_area = new LinkedHashSet<>();
        this.r_area = new LinkedHashSet<>();
        this.prefixIndex = new LinkedHashMap<>();
        this.e_area = new LinkedList<>();
        this.certainNegativeCounterExamples = new LinkedHashSet<>();
        this.pendingMqCells = new LinkedHashSet<>();

        this.epsilon_prefix = new Prefix(new RTARegionWord(clock), null, this.elementTree.getRoot(), this.slv);
        this.s_area.add(epsilon_prefix);
        this.prefixIndex.put(this.wordKey(this.epsilon_prefix.word()), this.epsilon_prefix);
        this.add_r_prefix(epsilon_prefix);
        Suffix epsilon_suffix = new Suffix(new RTARegionWord(clock));
        this.e_area.add(epsilon_suffix);

        this.pendingMqCells.addAll(this.constructCells());
    }

    public void add_r_prefix(Prefix s){
        if (!this.shouldExpandPrefixToRArea(s)) {
            return;
        }
        List<Prefix> next_prefixes = s.appendAction(this.actions, this.clock);
        for (Prefix prefix : next_prefixes) {
            this.prefixIndex.put(this.wordKey(prefix.word()), prefix);
        }
        this.r_area.addAll(next_prefixes);
    }

    private boolean shouldExpandPrefixToRArea(Prefix prefix) {
        int level = LearningSetting.PREFIX_EXPANSION_PRUNE_LEVEL;
        if (level <= 0) {
            return true;
        }
        TriBool epsilonAccept = this.getEpsilonSuffixAccept(prefix);
        if (level == 1) {
            return epsilonAccept != TriBool.tri_false;
        }
        // level >= 2: aggressive pruning, only expand confirmed-true prefixes.
        return epsilonAccept == TriBool.tri_true;
    }

    private TriBool getEpsilonSuffixAccept(Prefix prefix) {
        for (Cell cell : prefix.getCells()) {
            if (cell.isSuffixEmpty()) {
                return cell.isAccept();
            }
        }
        // Conservative fallback: if epsilon cell is absent, allow expansion.
        return TriBool.tri_true;
    }

    /**
     * Constructs cells and establishes connections with nodes.
     */
    public List<Cell> constructCells(){
        List<Cell> freshCellsNeedMq = new LinkedList<>();
        for(Prefix prefix : this.getSrArea()){
            int oldCellCount = prefix.getCells().size();
            prefix.constructCells(this.e_area);
            for(int i = oldCellCount; i < prefix.getCells().size(); i++){
                Cell cell = prefix.getCells().get(i);
                ElementNode elementNode = this.elementTree.seekNode(cell.getPrefix().word(), cell.getSuffix().word());
                if (elementNode == null) {
                    elementNode = this.elementTree.addNode(cell.getPrefix().word(), cell.getSuffix().word());
                }
                cell.setElementNode(elementNode);
                if (elementNode.isAccept() == TriBool.unknown) {
                    freshCellsNeedMq.add(cell);
                }
            }
        }
        return freshCellsNeedMq;
    }

    /**
     * Updates information.
     */
    public void updateInformation(Cell cell, TriBool accept){
        if(accept == TriBool.tri_true){
            cell.getElementNode().setAccept(TriBool.tri_true);
        } else if (accept == TriBool.tri_false) {
            cell.getElementNode().setAccept(TriBool.tri_false);
        }
    }

    /**
     * @return true if no modifications were made, false otherwise
     */
    public boolean makeClosed(){
        boolean modified = false;
        List<Row> rows = new LinkedList<>();
        List<Row> rowR = new LinkedList<>();
        for(Prefix prefix : this.sortPrefixes(this.s_area)) {
            rows.add(prefix.row());
        }
        for(Prefix prefix : this.sortPrefixes(this.r_area)) {
            rowR.add(prefix.row());
        }

        for(Row r : rowR){
            boolean seeked = false;
            for(Row s : rows){
                if(r.directionStrictEqual(s)) {
                    seeked = true;
                    break;
                }
            }
            if(!seeked){
                modified = true;
                Prefix prefix = r.getPrefix();
                this.r_area.remove(prefix);
                this.s_area.add(prefix);
                this.add_r_prefix(prefix);
                break;
            }
        }
        if(modified){
            this.pendingMqCells.addAll(this.constructCells());
        }
        return !modified;
    }

    /**
     * Makes the table consistent.
     * @return true if no modifications were made, false otherwise
     */
    public boolean makeConsistent(){
        List<Prefix> sr = this.sortPrefixes(this.getSrArea());
        for(int i=0;i<sr.size();i++){
            Prefix pi = sr.get(i);
            for(int j=i+1;j<sr.size();j++){
                Prefix pj = sr.get(j);
                if(pi.rowLooseEqual(pj)){
                    RTARegionWord key = this.isConsistentCore(pi, pj);
                    if(key != null) {
                        if (!this.containsSuffixWord(key)) {
                            this.e_area.add(new Suffix(key));
                            this.pendingMqCells.addAll(this.constructCells());
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private RTARegionWord isConsistentCore(Prefix pi, Prefix pj){
        Map<Action, Set<Prefix>> children_i = pi.getChildren();
        Map<Action, Set<Prefix>> children_j = pj.getChildren();
        List<Map.Entry<Action, Set<Prefix>>> orderedEntries = new ArrayList<>(children_i.entrySet());
        orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
        for(Map.Entry<Action, Set<Prefix>> entry_i : orderedEntries){
            Action action = entry_i.getKey();
            List<Prefix> group_i = this.sortPrefixes(entry_i.getValue());
            if(children_j.containsKey(action)){
                List<Prefix> group_j = this.sortPrefixes(children_j.get(action));
                for(Prefix pi_ : group_i){
                    for(Prefix pj_ : group_j){
                        assert pi_.getLast().getAction().equals(action);
                        assert pj_.getLast().getAction().equals(action);
                        if(pi_.getLast().regionEquals(pj_.getLast())){
                            if(!pi_.rowLooseEqual(pj_)){
                                List<Cell> cells_i = pi_.getCells();
                                List<Cell> cells_j = pj_.getCells();
                                for(int i=0;i<this.e_area.size();i++){
                                    Cell cell_i = cells_i.get(i);
                                    Cell cell_j = cells_j.get(i);
                                    if(!cell_i.suffixLooseEqual(cell_j)){
                                        RTARegionWord outcome = new RTARegionWord(this.clock);
                                        outcome.attend(pi_.getLast());
                                        outcome.attendAll(cell_i.getSuffix().word());
                                        return outcome;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Generates the corresponding hypothesis automaton.
     * @return the generated hypothesis automaton
     */
    public RTA generateHypothesis(){
        BoolExpr closedConstraint = this.generateClosedConstraints();
        BoolExpr consistentConstraint = this.generateConsistentConstraints();
        BoolExpr sinkConstraint = this.generateSinkConstraints();
        Model model;
        Status solveStatus;
        String solverKind;
        String solverInput;
        if (TeacherSetting.VERIFY_CANONICAL_TABLE_SOLVE) {
            solverKind = "Optimize";
            Optimize opt = this.slv.mkOptimize();
            ExtendSolver.applyRandomSeed(this.slv, opt);
            opt.Add(closedConstraint);
            opt.Add(consistentConstraint);
            opt.Add(sinkConstraint);
            this.addCanonicalSolveObjectives(opt);
            solverInput = opt.toString();
            solveStatus = opt.Check();
            if (solveStatus != Status.SATISFIABLE) {
                this.captureSmtSolveSnapshot(
                        solverKind, solveStatus, solverInput, closedConstraint,
                        consistentConstraint, sinkConstraint, null);
                this.interHypothesis = null;
                System.out.println("hypothesis has no solution");
                ExptHelp.Hyp_No_Solution++;
                return null;
            }
            model = opt.getModel();
        } else {
            solverKind = "Solver";
            Solver slv = this.slv.mkSolver();
            ExtendSolver.applyRandomSeed(this.slv, slv);
            slv.add(closedConstraint);
            slv.add(consistentConstraint);
            slv.add(sinkConstraint);
            solverInput = slv.toString();
            solveStatus = slv.check();
            if (solveStatus != Status.SATISFIABLE) {
                this.captureSmtSolveSnapshot(
                        solverKind, solveStatus, solverInput, closedConstraint,
                        consistentConstraint, sinkConstraint, null);
                this.interHypothesis = null;
                System.out.println("hypothesis has no solution");
                ExptHelp.Hyp_No_Solution++;
                return null;
            }
            model = slv.getModel();
        }

        this.captureSmtSolveSnapshot(
                solverKind, solveStatus, solverInput, closedConstraint,
                consistentConstraint, sinkConstraint, model);

        if (LearningSetting.isLearningVerbose()) {
            System.out.println(
                    ColoredFront.dealtString(
                            "***** the table from SMT solver *****",
                            Color.Green,
                            Type.Common));
            this.printSolvedTable(model);
        }

        Map<Prefix, BigInteger> s_model_map = new HashMap<>();
        Map<Prefix, BigInteger> r_model_map = new HashMap<>();
        for(Prefix s : s_area){
            BitVecExpr bv = s.rowSmtExpr();
            BigInteger value = this.slv.getBvValue(bv, model);
            s_model_map.put(s, value);
        }

        for(Prefix r : r_area){
            BitVecExpr bv = r.rowSmtExpr();
            BigInteger value = this.slv.getBvValue(bv, model);
            r_model_map.put(r, value);
        }
        this.interHypothesis = this.generateHypothesisFromModel(s_model_map, r_model_map, model);
        return this.interHypothesis;

    }

    private void captureSmtSolveSnapshot(
            String solverKind,
            Status status,
            String solverInput,
            BoolExpr closedConstraint,
            BoolExpr consistentConstraint,
            BoolExpr sinkConstraint,
            Model model) {
        this.lastSmtSolveSnapshot = new SmtSolveSnapshot(
                solverKind,
                status.toString(),
                this.getShapeFingerprint(),
                solverInput,
                closedConstraint.toString(),
                consistentConstraint.toString(),
                sinkConstraint.toString(),
                model == null ? "" : model.toString(),
                model == null ? "" : this.toSolvedLongCsv(model));
    }

    private void addCanonicalSolveObjectives(Optimize opt) {
        List<ElementNode> orderedNodes = new ArrayList<>();
        for (ElementNode node : this.elementTree) {
            if (node.isAccept() == TriBool.unknown) {
                orderedNodes.add(node);
            }
        }
        orderedNodes.sort(Comparator.comparing(ElementNode::wordToString));
        for (ElementNode node : orderedNodes) {
            BitVecExpr acceptBv = node.getBvAccept();
            ArithExpr acceptAsInt = this.slv.mkBV2Int(acceptBv, false);
            opt.MkMinimize(acceptAsInt);
        }

        for (Prefix s : this.sortPrefixes(this.s_area)) {
            BitVecExpr rowBv = s.rowSmtExpr();
            ArithExpr rowAsInt = this.slv.mkBV2Int(rowBv, false);
            opt.MkMinimize(rowAsInt);
        }
        for (Prefix r : this.sortPrefixes(this.r_area)) {
            BitVecExpr rowBv = r.rowSmtExpr();
            ArithExpr rowAsInt = this.slv.mkBV2Int(rowBv, false);
            opt.MkMinimize(rowAsInt);
        }
    }

    private List<Prefix> removeDuplicatedPrefix(Map<Prefix, BigInteger> s_model_map,
                                                Map<Prefix, BigInteger> r_model_map){
        Map<BigInteger, Prefix> remainedPrefix = new LinkedHashMap<>();
        Map<Prefix, BigInteger> removed_s = new LinkedHashMap<>();
        List<Map.Entry<Prefix, BigInteger>> orderedS = new ArrayList<>(s_model_map.entrySet());
        orderedS.sort(Comparator
                .comparingInt((Map.Entry<Prefix, BigInteger> e) -> e.getKey().getLength())
                .thenComparing(e -> e.getKey().toStringWithReset()));
        for(Map.Entry<Prefix, BigInteger> entry : orderedS){
            Prefix s = entry.getKey();
            BigInteger s_value = entry.getValue();
            Prefix existed = remainedPrefix.get(s_value);
            if (existed != null) {
                removed_s.put(s, s_value);
            } else {
                remainedPrefix.put(s_value, s);
            }
        }
        for(Map.Entry<Prefix, BigInteger> entry : removed_s.entrySet()){
            Prefix s = entry.getKey();
            BigInteger s_value = entry.getValue();
            s_model_map.remove(s);
            r_model_map.put(s, s_value);
        }

        List<Prefix> outcome = new LinkedList<>(s_model_map.keySet());
        outcome.sort(Comparator.comparingInt(Prefix::getLength));

        return outcome;
    }

    private void printSolvedTable(Model model){
        System.out.println(this.toStringBasedOnModel(model));
    }

    private RTA generateHypothesisFromModel(Map<Prefix, BigInteger> s_model_map,
                                            Map<Prefix, BigInteger> r_model_map,
                                            Model model){
        RTA hypothesis = new RTA(hypothesisName);
        hypothesis.addClock(this.h_clock);
        Set<Clock> h_reset = Set.of(this.h_clock);
        List<Action> orderedActionsForHyp = new ArrayList<>(this.actions);
        orderedActionsForHyp.sort(Comparator.comparing(Action::getName));
        for(Action action : orderedActionsForHyp)
            hypothesis.addAction(action);
        Map<BigInteger, Location> rowLocationMap = new HashMap<>();
        Map<BigInteger, List<Prefix>> rowPrefixMap = new HashMap<>();
        int id = 0;
        List<Prefix> s_list_dealt = this.removeDuplicatedPrefix(s_model_map, r_model_map);
        for(Prefix s : s_list_dealt){
            BigInteger value = s_model_map.get(s);
            boolean accepted = s.solvedAccept(model);

            Location location = new Location("q"+id, accepted, Constraint.TRUE, hypothesis);
            hypothesis.addLocation(location);
            rowLocationMap.put(value, location);
            List<Prefix> prefixesWithSameValue = new LinkedList<>();
            prefixesWithSameValue.add(s);
            rowPrefixMap.put(value, prefixesWithSameValue);
            if(s.isEmpty())
                hypothesis.setQ0(location);
            id++;
        }

        List<Prefix> orderedR = new ArrayList<>(r_model_map.keySet());
        orderedR.sort(Comparator.comparingInt(Prefix::getLength).thenComparing(Prefix::toStringWithReset));
        for(Prefix r : orderedR) {
            BigInteger value = r_model_map.get(r);
            rowPrefixMap.get(value).add(r);
        }
        assert rowPrefixMap.size() == rowLocationMap.size();
        assert rowPrefixMap.values().stream().flatMap(List::stream).count() == s_model_map.size() + r_model_map.size();
        Map<Prefix, BigInteger> srMap = new HashMap<>();
        srMap.putAll(s_model_map);
        srMap.putAll(r_model_map);

        Map<Location, Map<Action, List<RegionTarget>>> originalTransInfo = new HashMap<>();

        List<BigInteger> orderedRows = new ArrayList<>(rowPrefixMap.keySet());
        orderedRows.sort(Comparator.naturalOrder());
        for(BigInteger rowValue : orderedRows){
            Location source = rowLocationMap.get(rowValue);
            List<Prefix> prefixesOfSameRow = rowPrefixMap.get(rowValue);
            Map<Action, List<RegionTarget>> outTransMap = new LinkedHashMap<>();
            for(Prefix prefix : prefixesOfSameRow){
                Set<Prefix> allChildren = prefix.getAllChildren();
                List<Prefix> orderedChildren = new ArrayList<>(allChildren);
                orderedChildren.sort(Comparator.comparingInt(Prefix::getLength).thenComparing(Prefix::toStringWithReset));
                for (Prefix child : orderedChildren) {
                    Action action = child.getLast().getAction();
                    List<RegionTarget> regionTargetList;
                    if (outTransMap.containsKey(action))
                        regionTargetList = outTransMap.get(action);
                    else {
                        regionTargetList = new LinkedList<>();
                        outTransMap.put(action, regionTargetList);
                    }
                    RTARegionAction regionAction = child.getLast();
                    Location target = rowLocationMap.get(srMap.get(child));
                    regionTargetList.add(new RegionTarget(regionAction, target));
                }
            }
            originalTransInfo.put(source, outTransMap);
        }

        this.generateTransitions(hypothesis, originalTransInfo, h_reset);
        return hypothesis;
    }
    private void generateTransitions(TA hypothesis,
                                     Map<Location, Map<Action, List<RegionTarget>>> originalTransInfo,
                                     Set<Clock> hReset){
        List<Location> orderedSources = new ArrayList<>(originalTransInfo.keySet());
        orderedSources.sort(Comparator.comparing(Location::getName));
        for(Location source : orderedSources){
            Map<Action, List<RegionTarget>> actionListMap = originalTransInfo.get(source);
            List<Action> orderedActions = new ArrayList<>(actionListMap.keySet());
            orderedActions.sort(Comparator.comparing(Action::getName));
            for(Action action : orderedActions){
                List<RegionTarget> regionTargetList = actionListMap.get(action);
                List<GuardTarget> partitionData = this.partitionFunctionRta(regionTargetList);
                for(GuardTarget guardTarget : partitionData){
                    Transition transition = new Transition(source, action, guardTarget.guard, hReset, guardTarget.target);
                    hypothesis.addTransition(transition);
                }
            }
        }
    }

    private List<GuardTarget> partitionFunctionRta(List<RegionTarget> regionTargets){
        if(regionTargets.isEmpty())
            throw new RuntimeException(ValueExceptions.UNABLE_TO_PARTITION_NULL_VALUE);

        List<RegionTarget> orderedTargets = new ArrayList<>(regionTargets);
        orderedTargets.sort(Comparator
                .comparing((RegionTarget rt) -> rt.regionAction.getLower())
                .thenComparing(rt -> rt.regionAction.isLowerClosed() ? 0 : 1)
                .thenComparing(rt -> rt.regionAction.getUpper())
                .thenComparing(rt -> rt.regionAction.isUpperClosed() ? 0 : 1)
                .thenComparing(rt -> rt.target.getName()));

        List<RegionTarget> compressedTargets = new ArrayList<>();
        for (RegionTarget current : orderedTargets) {
            if (compressedTargets.isEmpty()) {
                compressedTargets.add(current);
                continue;
            }
            RegionTarget lastKept = compressedTargets.get(compressedTargets.size() - 1);
            if (!lastKept.target.equals(current.target)) {
                compressedTargets.add(current);
            }
        }

        List<IntervalTarget> intervals = new ArrayList<>();
        for (int i = 0; i < compressedTargets.size(); i++) {
            RegionTarget current = compressedTargets.get(i);
            RTARegionAction currentAction = current.regionAction;
            Value upper = null;
            boolean upperClosed = false;
            if (i + 1 < compressedTargets.size()) {
                RTARegionAction nextAction = compressedTargets.get(i + 1).regionAction;
                upper = nextAction.getLower();
                upperClosed = !nextAction.isLowerClosed();
            }
            intervals.add(new IntervalTarget(
                    currentAction.getLower(),
                    currentAction.isLowerClosed(),
                    upper,
                    upperClosed,
                    current.target));
        }

        List<GuardTarget> outcome = new LinkedList<>();
        for (IntervalTarget interval : intervals) {
            outcome.add(new GuardTarget(this.buildGuard(interval), interval.target));
        }
        return outcome;
    }

    private Constraint buildGuard(IntervalTarget interval) {
        Constraint guard = new Constraint();
        if (interval.lowerClosed) {
            guard.addAtomConstraint(AtomConstraint.generateGe(this.h_clock, interval.lower));
        } else {
            guard.addAtomConstraint(AtomConstraint.generateGt(this.h_clock, interval.lower));
        }
        if (interval.upper != null) {
            if (interval.upperClosed) {
                guard.addAtomConstraint(AtomConstraint.generateLe(this.h_clock, interval.upper));
            } else {
                guard.addAtomConstraint(AtomConstraint.generateLt(this.h_clock, interval.upper));
            }
        }
        return guard;
    }

    private static boolean canMerge(IntervalTarget left, IntervalTarget right) {
        if (!left.target.equals(right.target)) {
            return false;
        }
        if (left.upper == null) {
            return true;
        }
        int cmp = Value.compare(left.upper, right.lower);
        if (cmp < 0) {
            return false;
        }
        if (cmp > 0) {
            return true;
        }
        return left.upperClosed || right.lowerClosed;
    }

    private static class RegionTarget {
        private final RTARegionAction regionAction;
        private final Location target;

        private RegionTarget(RTARegionAction regionAction, Location target) {
            this.regionAction = regionAction;
            this.target = target;
        }
    }

    private static class GuardTarget {
        private final Constraint guard;
        private final Location target;

        private GuardTarget(Constraint guard, Location target) {
            this.guard = guard;
            this.target = target;
        }
    }

    private static class IntervalTarget {
        private final Value lower;
        private final boolean lowerClosed;
        private final Value upper; // null means +inf
        private final boolean upperClosed;
        private final Location target;

        private IntervalTarget(Value lower, boolean lowerClosed, Value upper, boolean upperClosed, Location target) {
            this.lower = lower;
            this.lowerClosed = lowerClosed;
            this.upper = upper;
            this.upperClosed = upperClosed;
            this.target = target;
        }

        private IntervalTarget merge(IntervalTarget other) {
            return new IntervalTarget(this.lower, this.lowerClosed, other.upper, other.upperClosed, this.target);
        }
    }

    private static class Anchor {
        private final Value lower;
        private final boolean lowerClosed;
        private final Location target;

        private Anchor(Value lower, boolean lowerClosed, Location target) {
            this.lower = lower;
            this.lowerClosed = lowerClosed;
            this.target = target;
        }
    }

    /**
     * Generates closed constraints.
     * @return the closed constraints as a boolean expression
     */
    private BoolExpr generateClosedConstraints(){
        BoolExpr[] all_case_array = new BoolExpr[r_area.size()];
        int i = 0;
        for(Prefix r : this.sortPrefixes(this.r_area)){
            BitVecExpr r_bv = r.rowSmtExpr();
            BoolExpr[] case_array = new BoolExpr[s_area.size()];
            int j = 0;
            for(Prefix s : this.sortPrefixes(this.s_area)){
                BitVecExpr s_bv = s.rowSmtExpr();
                case_array[j++] = this.slv.mkEq(r_bv, s_bv);
            }
            all_case_array[i++] = this.slv.mkArrayOr(case_array);
        }
        return this.slv.mkArrayAnd(all_case_array);
    }

    /**
     * Generates consistent constraints.
     * @return the consistent constraints as a boolean expression
     */
    private BoolExpr generateConsistentConstraints(){
        List<BoolExpr> constraints = new LinkedList<>();
        List<Prefix> sr = this.sortPrefixes(this.getSrArea());
        for(int i=0;i<sr.size();i++){
            Prefix pi = sr.get(i);
            for(int j=i+1;j<sr.size();j++){
                Prefix pj = sr.get(j);
                BoolExpr antecedent = this.slv.mkEq(pi.rowSmtExpr(), pj.rowSmtExpr());
                this.generateConsistentConstraintsCore(pi,pj,antecedent,constraints);
            }
        }
        if(constraints.isEmpty())
            return this.slv.mkTrue();
        else
            return this.slv.mkArrayAnd(constraints.toArray(new BoolExpr[0]));
    }

    private void generateConsistentConstraintsCore(Prefix pi,
                                                   Prefix pj,
                                                   BoolExpr antecedent,
                                                   List<BoolExpr> constraints){
        Map<Action, Set<Prefix>> children_i = pi.getChildren();
        Map<Action, Set<Prefix>> children_j = pj.getChildren();
        List<Map.Entry<Action, Set<Prefix>>> orderedEntries = new ArrayList<>(children_i.entrySet());
        orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
        for(Map.Entry<Action, Set<Prefix>> entry_i : orderedEntries){
            Action action = entry_i.getKey();
            List<Prefix> group_i = this.sortPrefixes(entry_i.getValue());
            if(children_j.containsKey(action)){
                List<Prefix> group_j = this.sortPrefixes(children_j.get(action));
                for(Prefix pi_ : group_i){
                    for(Prefix pj_ : group_j){
                        assert pi_.getLast().getAction().equals(action);
                        assert pj_.getLast().getAction().equals(action);
                        if(pi_.getLast().regionEquals(pj_.getLast())){
                            BoolExpr rowEq = this.slv.mkEq(pi_.rowSmtExpr(), pj_.rowSmtExpr());
                            constraints.add(this.slv.mkImplies(antecedent, rowEq));
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates constraints: if a node does not accept, then all its successors also do not accept.
     * @return the sink constraints as a boolean expression
     */
    private BoolExpr generateSinkConstraints(){
        return this.elementTree.generateSinkConstraint();
    }

    /**
     * Updates the table based on a counterexample.
     * @param ctx the counterexample (reset logical timed word)
     * @param isPosOrNeg true for positive counterexample, false for negative counterexample
     */
    public void refineCounterExample(RTARegionWord ctx, boolean isPosOrNeg){
        assert !ctx.isEmpty();
        this.elementTree.updateTreeByCtx(ctx,isPosOrNeg);
        List<RTARegionWord> prefixList = ctx.getAllPrefixes();
        List<Prefix> freshPrefixList = new LinkedList<>();
        Prefix last = this.epsilon_prefix;
        for(int i=1;i<prefixList.size();i++){
            RTARegionWord lw = prefixList.get(i);
            Prefix existed = this.prefixIndex.get(this.wordKey(lw));
            if (existed != null) {
                last = existed;
            } else {
                ElementNode node = this.elementTree.seekNode(lw);
                if (node == null) {
                    node = this.elementTree.addNode(lw);
                }
                Prefix freshPrefix = new Prefix(lw, last, node, this.slv);
                freshPrefixList.add(freshPrefix);
                last.addChild(freshPrefix);
                this.prefixIndex.put(this.wordKey(freshPrefix.word()), freshPrefix);
                last = freshPrefix;
            }
        }
        this.r_area.addAll(freshPrefixList);
        this.pendingMqCells.addAll(this.constructCells());
    }

    public List<Cell> drainPendingMqCells() {
        List<Cell> result = new LinkedList<>(this.pendingMqCells);
        this.pendingMqCells.clear();
        return result;
    }

    public void requeuePendingMqCells(Collection<Cell> cells) {
        for (Cell cell : cells) {
            if (cell != null && cell.isAccept() == TriBool.unknown) {
                this.pendingMqCells.add(cell);
            }
        }
    }

    public String getShapeFingerprint() {
        return this.s_area.size()
                + "|"
                + this.r_area.size()
                + "|"
                + this.e_area.size()
                + "|"
                + this.pendingMqCells.size();
    }

    public TableRecord exportRecord() {
        List<RTARegionWord> sWords = new LinkedList<>();
        for (Prefix prefix : this.sortPrefixes(this.s_area)) {
            sWords.add(prefix.word().copy());
        }
        List<RTARegionWord> rWords = new LinkedList<>();
        for (Prefix prefix : this.sortPrefixes(this.r_area)) {
            rWords.add(prefix.word().copy());
        }
        List<RTARegionWord> eWords = new LinkedList<>();
        for (Suffix suffix : this.e_area) {
            eWords.add(suffix.word().copy());
        }
        ElementRecord elementRecord = this.elementTree.exportRecord();
        return new TableRecord(sWords, rWords, eWords, elementRecord);
    }

    public void rollback(TableRecord tableRecord) {
        this.elementTree.rollback(tableRecord.getElementRecord(), this.certainNegativeCounterExamples);
        this.restoreEArea(tableRecord.getEAreaWords());
        this.restoreSrAreas(tableRecord.getSAreaWords(), tableRecord.getRAreaWords());
        this.collectUnknownCellsToPending();
    }

    public void addCertainNegativeCounterExample(RTARegionWord ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return;
        }
        List<RTARegionWord> toRemove = new LinkedList<>();
        for (RTARegionWord stored : this.certainNegativeCounterExamples) {
            if (stored.isPrefix(ctx)) {
                return;
            }
            if (ctx.isPrefix(stored)) {
                toRemove.add(stored);
            }
        }
        this.certainNegativeCounterExamples.removeAll(toRemove);
        this.certainNegativeCounterExamples.add(ctx.copy());
    }

    public List<RTARegionWord> getCertainNegativeCounterExamples() {
        List<RTARegionWord> outcome = new LinkedList<>();
        for (RTARegionWord word : this.certainNegativeCounterExamples) {
            outcome.add(word.copy());
        }
        outcome.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));
        return outcome;
    }

    @Override
    public Iterator<Cell> iterator() {
        List<Cell> cells = new LinkedList<>();
        for(Prefix prefix : this.sortPrefixes(this.getSrArea()))
            cells.addAll(prefix.getCells());
        return cells.iterator();
    }

    private Set<Prefix> getSrArea() {
        Set<Prefix> merged = new LinkedHashSet<>(this.s_area);
        merged.addAll(this.r_area);
        return merged;
    }

    private void restoreEArea(List<RTARegionWord> eWords) {
        this.e_area.clear();
        for (RTARegionWord word : eWords) {
            this.e_area.add(new Suffix(word.copy()));
        }
    }

    private void restoreSrAreas(List<RTARegionWord> sWords, List<RTARegionWord> rWords) {
        this.s_area.clear();
        this.r_area.clear();
        this.prefixIndex.clear();
        this.epsilon_prefix.clearChildren();
        this.epsilon_prefix.rebuildCells(this.e_area, this.elementTree);
        this.pendingMqCells.clear();

        Map<String, Prefix> prefixMap = new LinkedHashMap<>();
        prefixMap.put(this.wordKey(this.epsilon_prefix.word()), this.epsilon_prefix);
        this.prefixIndex.put(this.wordKey(this.epsilon_prefix.word()), this.epsilon_prefix);

        Set<String> sWordKeys = new LinkedHashSet<>();
        for (RTARegionWord sWord : sWords) {
            sWordKeys.add(this.wordKey(sWord));
        }
        Set<String> rWordKeys = new LinkedHashSet<>();
        for (RTARegionWord rWord : rWords) {
            rWordKeys.add(this.wordKey(rWord));
        }

        List<RTARegionWord> allWords = new LinkedList<>();
        allWords.addAll(sWords);
        allWords.addAll(rWords);
        allWords.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));

        for (RTARegionWord word : allWords) {
            if (word.isEmpty()) {
                continue;
            }
            String key = this.wordKey(word);
            if (prefixMap.containsKey(key)) {
                continue;
            }
            RTARegionWord parentWord = word.subWord(0, word.getLength() - 1);
            Prefix parent = prefixMap.get(this.wordKey(parentWord));
            if (parent == null) {
                throw new RuntimeException("rollback failed: missing parent prefix " + parentWord);
            }

            ElementNode elementNode = this.elementTree.seekNode(word);
            if (elementNode == null) {
                elementNode = this.elementTree.addNode(word);
            }
            Prefix prefix = new Prefix(word.copy(), parent, elementNode, this.slv);
            prefix.rebuildCells(this.e_area, this.elementTree);
            parent.addChild(prefix);
            prefixMap.put(key, prefix);
            this.prefixIndex.put(key, prefix);
        }

        for (Prefix prefix : prefixMap.values()) {
            String key = this.wordKey(prefix.word());
            if (sWordKeys.contains(key)) {
                this.s_area.add(prefix);
            } else if (rWordKeys.contains(key)) {
                this.r_area.add(prefix);
            }
        }
        if (!this.s_area.contains(this.epsilon_prefix)) {
            this.s_area.add(this.epsilon_prefix);
        }
    }

    private void collectUnknownCellsToPending() {
        this.pendingMqCells.clear();
        for (Prefix prefix : this.sortPrefixes(this.getSrArea())) {
            for (Cell cell : prefix.getCells()) {
                if (cell.isAccept() == TriBool.unknown) {
                    this.pendingMqCells.add(cell);
                }
            }
        }
    }

    private boolean containsSuffixWord(RTARegionWord word) {
        for (Suffix suffix : this.e_area) {
            if (suffix.word().equals(word)) {
                return true;
            }
        }
        return false;
    }

    private String wordKey(RTARegionWord word) {
        return word.toString();
    }

    private List<Prefix> getPrefixList() {
        List<Prefix> sr = new LinkedList<>(this.s_area);
        sr.sort(Comparator.comparingInt(Prefix::getLength).thenComparing(Prefix::toStringWithReset));
        List<Prefix> r = new LinkedList<>(this.r_area);
        r.sort(Comparator.comparingInt(Prefix::getLength).thenComparing(Prefix::toStringWithReset));
        sr.addAll(r);
        return sr;
    }

    private List<Prefix> sortPrefixes(Collection<Prefix> prefixes) {
        List<Prefix> ordered = new ArrayList<>(prefixes);
        ordered.sort(Comparator.comparingInt(Prefix::getLength).thenComparing(Prefix::toStringWithReset));
        return ordered;
    }

    public String debugFingerprintSummary() {
        return "shape=" + this.getShapeFingerprint()
                + ",elementTree={" + this.elementTree.fingerprintSummary() + "}"
                + ",pendingHash=" + this.pendingMqFingerprint()
                + ",constraintsHash=" + this.constraintsFingerprint();
    }

    private String pendingMqFingerprint() {
        List<String> pending = new ArrayList<>();
        for (Cell cell : this.pendingMqCells) {
            pending.add(cell.toString() + ":" + cell.isAccept());
        }
        Collections.sort(pending);
        return this.sha256Hex(String.join("|", pending));
    }

    private String constraintsFingerprint() {
        String closed = this.generateClosedConstraints().toString();
        String consistent = this.generateConsistentConstraints().toString();
        String sink = this.generateSinkConstraints().toString();
        return this.sha256Hex("C=" + closed + "\nK=" + consistent + "\nS=" + sink);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private String toStringBasedOnModel(Model model){
        List<Prefix> prefixList = this.getPrefixList();
        List<String> stringList = new LinkedList<>();
        for(Prefix prefix : prefixList){
            stringList.add(prefix.word().toString());
        }

        int num = stringList.size();
        int num_length = String.valueOf(num).length() + 2;
        int maxLen = stringList.stream().mapToInt(String::length).max().orElse(0);
        List<String> suffixStringList = this.e_area.stream().map(Suffix::toString).toList();

        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(" ".repeat(maxLen + num_length + 1));
        sBuilder.append("|");
        for (String suffix : suffixStringList) {
            sBuilder.append(suffix).append("|");
        }
        sBuilder.append("\n");
        int order = 1;
        for (int i = 0; i < prefixList.size(); i++) {
            sBuilder.append(order);
            sBuilder.append(" ".repeat(Math.max(0, num_length - String.valueOf(order).length())));
            order++;
            sBuilder.append("|");
            String prefixString = stringList.get(i);
            sBuilder.append(prefixString);
            sBuilder.append(" ".repeat(Math.max(0, maxLen - prefixString.length())));

            Row prefixRow = prefixList.get(i).row(model);
            sBuilder.append(prefixRow.toStringFitSuffix(true, prefixList.get(i)));
            sBuilder.append("\n");

            if (i == this.s_area.size() - 1) {
                sBuilder.append("-".repeat(maxLen + num_length + 1));
                order = 1;
                sBuilder.append("|");
                for (String suffixString : suffixStringList) {
                    sBuilder.append("-".repeat(suffixString.length()));
                    sBuilder.append("|");
                }
                sBuilder.append("\n");
            }
        }
        return sBuilder.toString();
    }

    @Override
    public String toString(){
        return this.toStringInternal(false);
    }

    /** Human-readable observation table with exact rational timed words. */
    public String toExactString() {
        return this.toStringInternal(true);
    }

    private String toStringInternal(boolean exact) {
        List<Prefix> prefixList = this.getPrefixList();
        List<String> stringList = exact
                ? prefixList.stream().map(prefix -> ExactRationalFormat.regionWord(prefix.word())).toList()
                : prefixList.stream().map(Prefix::toStringWithReset).toList();
        int num = stringList.size();
        int num_length = String.valueOf(num).length() + 2;
        int maxLen = stringList.stream().mapToInt(String::length).max().orElse(0);
        List<String> suffixStringList = exact
                ? this.e_area.stream().map(suffix -> ExactRationalFormat.regionWord(suffix.word())).toList()
                : this.e_area.stream().map(Suffix::toString).toList();

        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(" ".repeat(maxLen + num_length + 1));
        sBuilder.append("|");
        for (String suffix : suffixStringList) {
            sBuilder.append(suffix).append("|");
        }
        sBuilder.append("\n");
        int order = 1;
        for (int i = 0; i < prefixList.size(); i++) {
            sBuilder.append(order);
            sBuilder.append(" ".repeat(Math.max(0, num_length - String.valueOf(order).length())));
            order++;
            sBuilder.append("|");
            String prefixString = stringList.get(i);
            sBuilder.append(prefixString);
            sBuilder.append(" ".repeat(Math.max(0, maxLen - prefixString.length())));

            Row prefixRow = prefixList.get(i).row();
            sBuilder.append(prefixRow.toStringFitSuffix(true, prefixList.get(i)));
            sBuilder.append("\n");

            if (i == this.s_area.size() - 1) {
                sBuilder.append("-".repeat(maxLen + num_length + 1));
                order = 1;
                sBuilder.append("|");
                for (String suffixString : suffixStringList) {
                    sBuilder.append("-".repeat(suffixString.length()));
                    sBuilder.append("|");
                }
                sBuilder.append("\n");
            }
        }
        return sBuilder.toString();
    }

    /** Long-form CSV export intended for plotting and paper artifact traces. */
    public String toLongCsv() {
        StringBuilder output = new StringBuilder(
                "section,prefix_index,prefix,suffix_index,suffix,value\n");
        List<Prefix> prefixes = this.getPrefixList();
        for (int prefixIndex = 0; prefixIndex < prefixes.size(); prefixIndex++) {
            Prefix prefix = prefixes.get(prefixIndex);
            String section = this.s_area.contains(prefix) ? "S" : "R";
            List<Cell> cells = prefix.getCells();
            for (int suffixIndex = 0; suffixIndex < cells.size(); suffixIndex++) {
                Cell cell = cells.get(suffixIndex);
                output.append(this.csvRow(
                        section,
                        Integer.toString(prefixIndex),
                        ExactRationalFormat.regionWord(prefix.word()),
                        Integer.toString(suffixIndex),
                        ExactRationalFormat.regionWord(cell.getSuffix().word()),
                        cell.isAccept().toString()));
            }
        }
        return output.toString();
    }

    /** Long-form observation table after substituting the actual Z3 model. */
    private String toSolvedLongCsv(Model model) {
        StringBuilder output = new StringBuilder(
                "section,prefix_index,prefix,suffix_index,suffix,pre_solve_value,solved_value\n");
        List<Prefix> prefixes = this.getPrefixList();
        for (int prefixIndex = 0; prefixIndex < prefixes.size(); prefixIndex++) {
            Prefix prefix = prefixes.get(prefixIndex);
            String section = this.s_area.contains(prefix) ? "S" : "R";
            List<Cell> cells = prefix.getCells();
            for (int suffixIndex = 0; suffixIndex < cells.size(); suffixIndex++) {
                Cell cell = cells.get(suffixIndex);
                output.append(this.csvRow(
                        section,
                        Integer.toString(prefixIndex),
                        ExactRationalFormat.regionWord(prefix.word()),
                        Integer.toString(suffixIndex),
                        ExactRationalFormat.regionWord(cell.getSuffix().word()),
                        cell.isAccept().toString(),
                        cell.getSolvedData(model).toString()));
            }
        }
        return output.toString();
    }

    private String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append('"').append(values[i].replace("\"", "\"\"")).append('"');
        }
        return row.append('\n').toString();
    }
}
