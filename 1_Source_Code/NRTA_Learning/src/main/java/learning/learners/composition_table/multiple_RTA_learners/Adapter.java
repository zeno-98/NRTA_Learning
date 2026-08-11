package learning.learners.composition_table.multiple_RTA_learners;

import language.actions.LogicalTimedAction;
import language.actions.RTARegionAction;
import language.words.DelayTimedWord;
import language.words.LogicalTimedWord;
import language.words.RTARegionWord;
import language.words.ResetLogicalTimedWord;
import learning.learners.composition_table.P.PForest;
import learning.learners.composition_table.P.PTree;
import learning.learners.composition_table.TriBool;
import learning.teachers.Teacher;
import lombok.Getter;
import run.ClockValuation;
import ta.*;
import tools.Tuple.Triplet;

import java.util.*;

public class Adapter {
    public record CounterExampleClassification(boolean certain, int uncertainNegativeComponents) {}

    private final int componentNum;
    private final Teacher teacher;
    private final ComponentLearner[] learners;
    public final PForest pForest;
    @Getter
    private final Map<Action, Set<Clock>> actionResetClocksMap;
    @Getter
    private volatile int totalMqCnt = 0;
    @Getter
    private volatile int firstStageMqSuccessCnt = 0;
    @Getter
    private volatile int secondStageMqSuccessCnt = 0;
    @Getter
    private volatile int thirdStageMqSuccessCnt = 0;
    @Getter
    private int positiveEvidenceVersion = 0;

    public Adapter(Teacher teacher, ComponentLearner[] learners) {
        this.componentNum = learners.length;
        this.teacher = teacher;
        this.learners = learners;
        this.actionResetClocksMap = new LinkedHashMap<>();
        List<ComponentLearner> orderedLearners = new ArrayList<>(Arrays.asList(learners));
        orderedLearners.sort(Comparator.comparing(l -> l.getClock().getTA_name() + "." + l.getClock().getName()));
        for (ComponentLearner learner : orderedLearners) {
            Clock clock = learner.getClock();
            for (Action action : learner.getAllActions()) {
                this.actionResetClocksMap
                        .computeIfAbsent(action, a -> new LinkedHashSet<>())
                        .add(clock);
            }
        }
        List<PTree> forestTrees = new ArrayList<>();
        for (int i = 0; i < this.componentNum; i++) {
            forestTrees.add(this.learners[i].getPTree());
        }
        this.pForest = new PForest(forestTrees);
    }

    public TriBool processComponentMembershipQuery(int id, RTARegionWord delta) {
        this.totalMqCnt++;
        // Fast path: build a representative global word directly from delta.
        DelayTimedWord representativeWord = delta.toRepresentativeDelayTimedWord();
        LogicalTimedWord representativeLogicalWord = this.toLogicalTimedWord(representativeWord);
        boolean representativeMq = this.teacher.MembershipQuery(representativeLogicalWord);
        if (representativeMq) {
            this.firstStageMqSuccessCnt++;
            this.absorbPositiveEvidence(id, delta, false);
            this.shallowRefineOtherLearnersByAcceptedWord(id, representativeWord);
            return TriBool.tri_true;
        }

        List<RTARegionWord> representativeProjection = this.projectToRTA(representativeWord);
        boolean allOtherComponentsAcceptProjection = true;
        for (int i = 0; i < this.componentNum; i++) {
            if (i == id) {
                continue;
            }
            if (!this.learners[i].getPTree().containsWord(representativeProjection.get(i))) {
                allOtherComponentsAcceptProjection = false;
                break;
            }
        }
        if (allOtherComponentsAcceptProjection) {
            this.secondStageMqSuccessCnt++;
            return TriBool.tri_false;
        }

        // Fallback to the original generatePi-based logic.
        DelayTimedWord globalDelayWord = this.pForest.generatePi(id, delta);
        if (globalDelayWord == null) {
            return TriBool.unknown;
        }
        LogicalTimedWord logicalTimedWord = this.toLogicalTimedWord(globalDelayWord);
        boolean mqResult = this.teacher.MembershipQuery(logicalTimedWord);
        this.thirdStageMqSuccessCnt++;
        if (mqResult) {
            this.addPositiveEvidence(id, delta, false);
            return TriBool.tri_true;
        }
        return TriBool.tri_false;
    }

    private void shallowRefineOtherLearnersByAcceptedWord(int id, DelayTimedWord acceptedGlobalWord) {
        List<RTARegionWord> projected = this.projectToRTA(acceptedGlobalWord);
        for (int i = 0; i < this.componentNum; i++) {
            if (i == id) {
                continue;
            }
            this.absorbPositiveEvidence(i, projected.get(i), false);
        }
    }

    private void notePositiveEvidenceChanged() {
        this.positiveEvidenceVersion++;
    }

    private void absorbPositiveEvidence(int id, RTARegionWord word, boolean probeNeighborhood) {
        this.learners[id].absorbPositiveEvidenceShallow(word);
        this.notePositiveEvidenceChanged();
    }

    private void addPositiveEvidence(int id, RTARegionWord word, boolean probeNeighborhood) {
        this.learners[id].getPTree().add_P(word);
        this.notePositiveEvidenceChanged();
    }

    public List<RTARegionWord> projectToRTA(DelayTimedWord dw) {
        List<RTARegionWord> outcome = new ArrayList<>(this.componentNum);
        for (int i = 0; i < this.componentNum; i++) {
            Clock clock = this.learners[i].getClock();
            Set<Action> actions = this.learners[i].getAllActions();
            RTARegionWord projectedWord = new RTARegionWord(clock);
            Value accumulatedDelay = Value.ZERO;

            for (var delayAction : dw) {
                Action action = delayAction.getAction();
                Value delay = delayAction.getDelayTime();
                if (!actions.contains(action)) {
                    accumulatedDelay = Value.add(accumulatedDelay, delay);
                    continue;
                }
                Value projectedDelay = Value.add(accumulatedDelay, delay);
                projectedWord.attend(new RTARegionAction(action, clock, projectedDelay));
                accumulatedDelay = Value.ZERO;
            }
            outcome.add(projectedWord);
        }
        return outcome;
    }

    private LogicalTimedWord toLogicalTimedWord(DelayTimedWord dw) {
        Set<Clock> allComponentClocks = new LinkedHashSet<>();
        for (ComponentLearner learner : this.learners) {
            allComponentClocks.add(learner.getClock());
        }
        ClockValuation currentValuation = new ClockValuation(allComponentClocks);
        LogicalTimedWord outcome = new LogicalTimedWord();
        for (var delayAction : dw) {
            currentValuation = currentValuation.delay(delayAction.getDelayTime());
            outcome.attend(new LogicalTimedAction(delayAction.getAction(), currentValuation));
            Set<Clock> resetClocks = this.actionResetClocksMap.getOrDefault(delayAction.getAction(), Collections.emptySet());
            currentValuation = currentValuation.reset(resetClocks);
        }
        return outcome;
    }

    public Triplet<Boolean, DelayTimedWord, Boolean> processEquivalentQuery(NTA hyp) {
        Triplet<Boolean, ResetLogicalTimedWord, Boolean> outcome = this.teacher.EquivalenceQuery(hyp);
        if (outcome.p0())
            return new Triplet<>(true, null, null);
        return new Triplet<>(false, outcome.p1().toDelayTimedWord(), outcome.p2());
    }

    public void refineCounterExample(DelayTimedWord globalCtx, boolean isPosOrNeg) {
        List<RTARegionWord> projected = this.projectToRTA(globalCtx);
        boolean[] positiveFlags = this.computePositiveFlags(globalCtx, projected, isPosOrNeg);
        int negativeCount = this.computeNegativeCount(positiveFlags);

        for (int i = 0; i < this.componentNum; i++) {
            boolean isPositive = positiveFlags[i];
            boolean isCertain = isPositive || negativeCount == 1;
            this.refineCounterExample(i, projected.get(i), isPositive, isCertain);
        }
    }

    public CounterExampleClassification classifyCounterExample(DelayTimedWord globalCtx, boolean isPosOrNeg) {
        List<RTARegionWord> projected = this.projectToRTA(globalCtx);
        boolean[] positiveFlags = this.computePositiveFlags(globalCtx, projected, isPosOrNeg);
        int negativeCount = this.computeNegativeCount(positiveFlags);
        int uncertainNegative = 0;
        if (negativeCount > 1) {
            for (boolean positive : positiveFlags) {
                if (!positive) {
                    uncertainNegative++;
                }
            }
        }
        return new CounterExampleClassification(uncertainNegative == 0, uncertainNegative);
    }

    /** Returns the refinement role assigned to every projected component. */
    public List<String> classifyCounterExampleComponents(DelayTimedWord globalCtx, boolean isPosOrNeg) {
        List<RTARegionWord> projected = this.projectToRTA(globalCtx);
        boolean[] positiveFlags = this.computePositiveFlags(globalCtx, projected, isPosOrNeg);
        int negativeCount = this.computeNegativeCount(positiveFlags);
        List<String> outcome = new ArrayList<>(this.componentNum);
        for (boolean positive : positiveFlags) {
            if (positive) {
                outcome.add("POSITIVE");
            } else if (negativeCount == 1) {
                outcome.add("CERTAIN_NEGATIVE");
            } else {
                outcome.add("UNCERTAIN_NEGATIVE");
            }
        }
        return outcome;
    }

    private boolean[] computePositiveFlags(DelayTimedWord globalCtx, List<RTARegionWord> projected, boolean isPosOrNeg) {
        Action lastAction = globalCtx.isEmpty() ? null : globalCtx.getLast().getAction();
        boolean[] positiveFlags = new boolean[this.componentNum];
        for (int i = 0; i < this.componentNum; i++) {
            boolean concernThis = globalCtx.isEmpty() || this.learners[i].getAllActions().contains(lastAction);
            positiveFlags[i] = this.computePositiveFlag(i, projected.get(i), isPosOrNeg, concernThis);
        }
        return positiveFlags;
    }

    private int computeNegativeCount(boolean[] positiveFlags) {
        int negativeCount = 0;
        for (boolean positiveFlag : positiveFlags) {
            if (!positiveFlag) {
                negativeCount++;
            }
        }
        return negativeCount;
    }

    private boolean computePositiveFlag(int id, RTARegionWord ctx, boolean isPosOrNeg, boolean concernThis) {
        ComponentLearner learner = this.learners[id];
        boolean refineAsPositive = isPosOrNeg || !concernThis;
        if (!isPosOrNeg && concernThis && learner.getPTree().containsWord(ctx)) {
            refineAsPositive = true;
        }
        return refineAsPositive;
    }

    private void refineCounterExample(int id, RTARegionWord ctx, boolean isPosOrNeg, boolean isCertain) {
        ComponentLearner learner = this.learners[id];
        if (isPosOrNeg) {
            this.addPositiveEvidence(id, ctx, true);
        } else if (!ctx.isEmpty()) {
            RTARegionWord prefix = ctx.subWord(0, ctx.getLength() - 1);
            if (!prefix.isEmpty()) {
                this.addPositiveEvidence(id, prefix, false);
            }
        }

        learner.refineCounterExample(ctx, isPosOrNeg, isCertain);
    }

}
