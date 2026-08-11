package learning.learners.composition_table.P;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.RealSort;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import language.actions.DelayTimedAction;
import language.actions.RTARegionAction;
import language.words.DelayTimedWord;
import language.words.RTARegionWord;
import ta.Action;
import ta.Value;
import tools.ExtendSolver;

import java.util.*;

public class PForest {
    private final Set<Action> sigma;
    private final List<PTree> trees;
    private final List<PTreeNode> rootList;

    public PForest(Collection<PTree> trees){
        this.sigma = new HashSet<>();
        this.trees = new LinkedList<>();
        this.rootList = new LinkedList<>();
        for (PTree tree : trees) {
            this.sigma.addAll(tree.getSigma());
            this.trees.add(tree);
            this.rootList.add(tree.getRoot());
        }
    }

    public DelayTimedWord generatePi(int id, RTARegionWord delta){
        return this.generatePi(id, delta, true);
    }

    public DelayTimedWord generatePiRelaxedPartnerTiming(int id, RTARegionWord delta) {
        return this.generatePi(id, delta, false);
    }

    private DelayTimedWord generatePi(int id, RTARegionWord delta, boolean constrainPartnerTiming){
        if (delta == null) {
            return null;
        }
        if (id < 0 || id >= this.trees.size()) {
            throw new IllegalArgumentException("id out of range for PForest trees");
        }

        Set<Action> idSigma = this.trees.get(id).getSigma();
        List<Action> orderedActions = new ArrayList<>(this.sigma);
        orderedActions.sort(Comparator.comparing(Action::getName));

        try (ExtendSolver slv = new ExtendSolver()) {
            Solver solver = slv.mkSolver();
            ExtendSolver.applyRandomSeed(slv, solver);
            int treeCount = this.trees.size();
            RealExpr[] clocks = new RealExpr[treeCount];
            for (int i = 0; i < treeCount; i++) {
                clocks[i] = slv.mkReal(0);
            }

            List<PTreeNode> currentNodes = new ArrayList<>(this.rootList);
            List<Action> actionTrace = new ArrayList<>();
            List<RealExpr> delayTrace = new ArrayList<>();
            int[] varCounter = new int[]{0};

            return this.search(
                    slv,
                    solver,
                    id,
                    idSigma,
                    delta,
                    orderedActions,
                    currentNodes,
                    clocks,
                    0,
                    actionTrace,
                    delayTrace,
                    varCounter,
                    constrainPartnerTiming
            );
        }
    }

    private DelayTimedWord search(ExtendSolver slv,
                                  Solver solver,
                                  int id,
                                  Set<Action> idSigma,
                                  RTARegionWord delta,
                                  List<Action> orderedActions,
                                  List<PTreeNode> currentNodes,
                                  RealExpr[] clocks,
                                  int deltaIndex,
                                  List<Action> actionTrace,
                                  List<RealExpr> delayTrace,
                                  int[] varCounter,
                                  boolean constrainPartnerTiming) {
        if (deltaIndex == delta.getLength()) {
            if (solver.check() != Status.SATISFIABLE) {
                return null;
            }
            return this.buildDelayTimedWord(slv, solver.getModel(), actionTrace, delayTrace);
        }

        Action requiredAction = delta.getTimedAction(deltaIndex).getAction();
        for (Action action : orderedActions) {
            boolean actionInIdSigma = idSigma.contains(action);
            if (actionInIdSigma && !action.equals(requiredAction)) {
                continue;
            }
            ExpansionContext expansion = this.prepareExpansion(id, action, currentNodes);
            if (!expansion.valid) {
                continue;
            }
            int nextDeltaIndex = deltaIndex;
            if (actionInIdSigma) {
                nextDeltaIndex += 1;
            }
            DelayTimedWord found = this.exploreCombinations(
                    slv,
                    solver,
                    id,
                    action,
                    actionInIdSigma,
                    delta,
                    deltaIndex,
                    nextDeltaIndex,
                    orderedActions,
                    idSigma,
                    currentNodes,
                    clocks,
                    expansion,
                    0,
                    new ArrayList<>(currentNodes),
                    new RTARegionAction[this.trees.size()],
                    actionTrace,
                    delayTrace,
                    varCounter,
                    constrainPartnerTiming
            );
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private DelayTimedWord exploreCombinations(ExtendSolver slv,
                                               Solver solver,
                                               int id,
                                               Action action,
                                               boolean actionInIdSigma,
                                               RTARegionWord delta,
                                               int deltaIndex,
                                               int nextDeltaIndex,
                                               List<Action> orderedActions,
                                               Set<Action> idSigma,
                                               List<PTreeNode> currentNodes,
                                               RealExpr[] clocks,
                                               ExpansionContext expansion,
                                               int componentIdx,
                                               List<PTreeNode> nextNodes,
                                               RTARegionAction[] chosenActions,
                                               List<Action> actionTrace,
                                               List<RealExpr> delayTrace,
                                               int[] varCounter,
                                               boolean constrainPartnerTiming) {
        if (componentIdx == this.trees.size()) {
            return this.checkAndRecurseWithTiming(
                    slv,
                    solver,
                    id,
                    action,
                    actionInIdSigma,
                    delta,
                    deltaIndex,
                    nextDeltaIndex,
                    orderedActions,
                    idSigma,
                    nextNodes,
                    clocks,
                    chosenActions,
                    actionTrace,
                    delayTrace,
                    varCounter,
                    constrainPartnerTiming
            );
        }

        if (componentIdx == id) {
            return this.exploreCombinations(
                    slv, solver, id, action, actionInIdSigma, delta, deltaIndex, nextDeltaIndex,
                    orderedActions, idSigma, currentNodes, clocks, expansion,
                    componentIdx + 1, nextNodes, chosenActions, actionTrace, delayTrace, varCounter,
                    constrainPartnerTiming
            );
        }

        if (!expansion.participates[componentIdx]) {
            nextNodes.set(componentIdx, currentNodes.get(componentIdx));
            chosenActions[componentIdx] = null;
            return this.exploreCombinations(
                    slv, solver, id, action, actionInIdSigma, delta, deltaIndex, nextDeltaIndex,
                    orderedActions, idSigma, currentNodes, clocks, expansion,
                    componentIdx + 1, nextNodes, chosenActions, actionTrace, delayTrace, varCounter,
                    constrainPartnerTiming
            );
        }

        for (PTreeNode child : expansion.childrenByComponent.get(componentIdx)) {
            nextNodes.set(componentIdx, child);
            chosenActions[componentIdx] = child.getTimedAction();
            DelayTimedWord found = this.exploreCombinations(
                    slv, solver, id, action, actionInIdSigma, delta, deltaIndex, nextDeltaIndex,
                    orderedActions, idSigma, currentNodes, clocks, expansion,
                    componentIdx + 1, nextNodes, chosenActions, actionTrace, delayTrace, varCounter,
                    constrainPartnerTiming
            );
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private DelayTimedWord checkAndRecurseWithTiming(ExtendSolver slv,
                                                     Solver solver,
                                                     int id,
                                                     Action action,
                                                     boolean actionInIdSigma,
                                                     RTARegionWord delta,
                                                     int deltaIndex,
                                                     int nextDeltaIndex,
                                                     List<Action> orderedActions,
                                                     Set<Action> idSigma,
                                                     List<PTreeNode> nextNodes,
                                                     RealExpr[] clocks,
                                                     RTARegionAction[] chosenActions,
                                                     List<Action> actionTrace,
                                                     List<RealExpr> delayTrace,
                                                     int[] varCounter,
                                                     boolean constrainPartnerTiming) {
        solver.push();
        try {
            RealExpr stepDelay = slv.mkRealConst("pf_t_" + varCounter[0]++);
            solver.add(slv.mkGe(stepDelay, slv.mkReal(0)));

            RealExpr[] nextClocks = new RealExpr[this.trees.size()];
            for (int i = 0; i < this.trees.size(); i++) {
                RealExpr clockAfterDelay = (RealExpr) slv.mkAdd(clocks[i], stepDelay);
                if (i == id) {
                    if (actionInIdSigma) {
                        RTARegionAction idAction = delta.getTimedAction(deltaIndex);
                        if (!idAction.getAction().equals(action)) {
                            return null;
                        }
                        this.addRangeConstraint(slv, solver, clockAfterDelay, idAction);
                        nextClocks[i] = slv.mkReal(0);
                    } else {
                        nextClocks[i] = clockAfterDelay;
                    }
                    continue;
                }

                RTARegionAction chosenAction = chosenActions[i];
                if (chosenAction != null) {
                    if (constrainPartnerTiming) {
                        this.addRangeConstraint(slv, solver, clockAfterDelay, chosenAction);
                    }
                    nextClocks[i] = slv.mkReal(0);
                } else {
                    nextClocks[i] = clockAfterDelay;
                }
            }

            if (solver.check() != Status.SATISFIABLE) {
                return null;
            }

            actionTrace.add(action);
            delayTrace.add(stepDelay);
            try {
                return this.search(
                        slv,
                        solver,
                        id,
                        idSigma,
                        delta,
                        orderedActions,
                        new ArrayList<>(nextNodes),
                        nextClocks,
                        nextDeltaIndex,
                        actionTrace,
                        delayTrace,
                        varCounter,
                        constrainPartnerTiming
                );
            } finally {
                actionTrace.remove(actionTrace.size() - 1);
                delayTrace.remove(delayTrace.size() - 1);
            }
        } finally {
            solver.pop();
        }
    }

    private ExpansionContext prepareExpansion(int id, Action action, List<PTreeNode> currentNodes) {
        boolean[] participates = new boolean[this.trees.size()];
        Map<Integer, List<PTreeNode>> childrenByComponent = new HashMap<>();
        for (int i = 0; i < this.trees.size(); i++) {
            if (i == id) {
                continue;
            }
            PTree tree = this.trees.get(i);
            if (!tree.containsAction(action)) {
                participates[i] = false;
                continue;
            }
            Set<PTreeNode> children = currentNodes.get(i).getNodeSet(action);
            if (children == null || children.isEmpty()) {
                return new ExpansionContext(false, participates, childrenByComponent);
            }
            participates[i] = true;
            List<PTreeNode> orderedChildren = new ArrayList<>(children);
            orderedChildren.sort(Comparator.comparing(this::timedActionKey));
            childrenByComponent.put(i, orderedChildren);
        }
        return new ExpansionContext(true, participates, childrenByComponent);
    }

    private void addRangeConstraint(ExtendSolver slv, Solver solver, RealExpr expr, RTARegionAction action) {
        BoolExpr lowerExpr = action.isLowerClosed()
                ? slv.mkGe(expr, this.valueExpr(slv, action.getLower()))
                : slv.mkGt(expr, this.valueExpr(slv, action.getLower()));
        BoolExpr upperExpr = action.isUpperClosed()
                ? slv.mkLe(expr, this.valueExpr(slv, action.getUpper()))
                : slv.mkLt(expr, this.valueExpr(slv, action.getUpper()));
        solver.add(lowerExpr);
        solver.add(upperExpr);
    }

    private RealExpr valueExpr(ExtendSolver slv, Value value) {
        return value.getValue().getSmt(slv);
    }

    private DelayTimedWord buildDelayTimedWord(ExtendSolver slv,
                                               Model model,
                                               List<Action> actionTrace,
                                               List<RealExpr> delayTrace) {
        DelayTimedWord word = new DelayTimedWord();
        for (int i = 0; i < actionTrace.size(); i++) {
            Expr<RealSort> eval = model.eval(delayTrace.get(i), true);
            word.attend(new DelayTimedAction(actionTrace.get(i), new Value(eval)));
        }
        return word;
    }

    private String timedActionKey(PTreeNode node) {
        RTARegionAction action = node.getTimedAction();
        return action.getAction().getName() + "|" + action.getLower() + "|" + action.getUpper();
    }

    private static class ExpansionContext {
        private final boolean valid;
        private final boolean[] participates;
        private final Map<Integer, List<PTreeNode>> childrenByComponent;

        private ExpansionContext(boolean valid, boolean[] participates, Map<Integer, List<PTreeNode>> childrenByComponent) {
            this.valid = valid;
            this.participates = participates;
            this.childrenByComponent = childrenByComponent;
        }
    }

}
