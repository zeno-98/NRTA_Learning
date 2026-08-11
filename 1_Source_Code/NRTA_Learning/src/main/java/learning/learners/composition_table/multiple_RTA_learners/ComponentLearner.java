package learning.learners.composition_table.multiple_RTA_learners;

import com.microsoft.z3.RealExpr;
import language.words.RTARegionWord;
import learning.learners.composition_table.P.PTree;
import learning.learners.composition_table.TriBool;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementNode;
import learning.learners.composition_table.multiple_RTA_learners.table.Cell;
import learning.learners.composition_table.multiple_RTA_learners.table.Table;
import learning.learners.composition_table.multiple_RTA_learners.table.TableRecord;
import lombok.Getter;
import lombok.Setter;
import ta.Clock;
import ta.RTA;
import ta.TA;
import tools.ExtendSolver;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;
import tools.FormatOutput.ExactRationalFormat;
import learning.LearningSetting;
import learning.trace.CompositionTraceRecorder;

import java.util.*;

public class ComponentLearner {
    private static final int ROLLBACK_LABEL_WIDTH = 13;
    @Getter
    private final int id;
    private final String hypothesisName;
    private TA internalHyp;
    private final Clock h_clock;
    @Setter
    private Adapter adapter;
    @Getter
    private final Clock clock;
    @Getter
    private final PTree pTree;
    @Getter
    private final Set<ta.Action> allActions;

    private final Table table;
    private final List<UncertainLog> uncertainLogs = new LinkedList<>();
    private final Set<ElementNode> turnScopedUnknownTriedNodes = new HashSet<>();
    private final Map<ElementNode, Integer> unknownEvidenceVersion = new HashMap<>();
    private int activeTurnId = -1;

    public ComponentLearner(int id, String hypothesisName, Clock clock, Set<ta.Action> actions, ExtendSolver slv) {
        this.id = id;
        this.hypothesisName = hypothesisName;
        this.clock = clock;
        String h_name = "h_" + this.clock.getName();
        RealExpr h_smt_exp = slv.mkRealConst(h_name);
        this.h_clock = new Clock(h_name, this.hypothesisName, h_smt_exp);
        this.allActions = actions;
        this.pTree = new PTree(this.allActions);
        this.table = new Table(clock, actions, this.hypothesisName, this.h_clock, slv);
    }

    public void completePTreeWithElementTree() {
        Set<RTARegionWord> longestAcceptedWords = this.table.getElementTree().getLongestAcceptedWords();
        for (RTARegionWord acceptedWord : longestAcceptedWords) {
            this.pTree.add_P(acceptedWord);
        }
    }

    /**
     * Shallow positive refinement: only update pTree and elementTree.
     * No table row/column expansion is performed here.
     */
    public void absorbPositiveEvidenceShallow(RTARegionWord ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return;
        }
        this.pTree.add_P(ctx);
        this.table.getElementTree().updateTreeByCtx(ctx, true);
    }

    public void beginTurn(int turnId) {
        if (this.activeTurnId != turnId) {
            this.activeTurnId = turnId;
            this.turnScopedUnknownTriedNodes.clear();
        }
    }

    public boolean prepareTable() {
        Set<ElementNode> unknownTriedInThisPrepare = new HashSet<>();
        this.fillCellsByMembershipQuery(this.table.drainPendingMqCells(), unknownTriedInThisPrepare);
        int guard = 0;
        while (true) {
            String shapeBefore = this.table.getShapeFingerprint();
            boolean closed = this.table.makeClosed();
            int updatedAfterClosed = this.fillCellsByMembershipQuery(this.table.drainPendingMqCells(), unknownTriedInThisPrepare);
            boolean consistent = this.table.makeConsistent();
            int updatedAfterConsistent = this.fillCellsByMembershipQuery(this.table.drainPendingMqCells(), unknownTriedInThisPrepare);
            this.completePTreeWithElementTree();
            if (closed && consistent) {
                return true;
            }
            String shapeAfter = this.table.getShapeFingerprint();
            if (!shapeAfter.equals(shapeBefore)) {
                unknownTriedInThisPrepare.clear();
            }
            if (updatedAfterClosed + updatedAfterConsistent == 0 && shapeAfter.equals(shapeBefore)) {
                System.err.println("[prepareTable] no progress; learnerId=" + this.id
                        + ", shape=" + shapeAfter
                        + ", closed=" + closed
                        + ", consistent=" + consistent);
                return false;
            }
            guard++;
            if (guard > 20000) {
                System.err.println("[prepareTable] guard overflow; learnerId=" + this.id
                        + ", shape=" + shapeAfter
                        + ", guard=" + guard);
                return false;
            }
        }
    }

    private TriBool raiseMembershipQuery(RTARegionWord delta) {
        return this.adapter.processComponentMembershipQuery(this.id, delta);
    }

    public TA generateHypothesis() {
        TA hypOrUnsolved = this.table.generateHypothesis();
        CompositionTraceRecorder.recordSmtSolve(
                this.activeTurnId, this.id, this.table.getLastSmtSolveSnapshot());
        if (hypOrUnsolved == null) {
            hypOrUnsolved = this.pTree.getPassiveLearnedRTA(this.hypothesisName, this.h_clock);
        }
        this.internalHyp = hypOrUnsolved;
        return this.internalHyp;
    }

    public TA getInternalHyp() {
        return this.internalHyp;
    }

    public String currentTableString() {
        return this.table.toString();
    }

    public String currentTableExactString() {
        return this.table.toExactString();
    }

    public String currentTableCsv() {
        return this.table.toLongCsv();
    }

    public String currentTableDebugFingerprint() {
        return this.table.debugFingerprintSummary();
    }

    public Optional<String> repairOneLocalInconsistency() {
        if (!(this.internalHyp instanceof RTA rta)) {
            return Optional.empty();
        }

        List<RTARegionWord> certainNegativeWords = this.table.getCertainNegativeCounterExamples();
        for (RTARegionWord negativeCtx : certainNegativeWords) {
            if (rta.ifContainRTAZoneWord(negativeCtx)) {
                this.refineCounterExample(negativeCtx, false, true);
                return Optional.of("violates certain negative: " + negativeCtx);
            }
        }

        Set<RTARegionWord> trueFrontierWords = this.table.getElementTree().getLongestAcceptedWords();
        List<RTARegionWord> orderedFrontierWords = new LinkedList<>(trueFrontierWords);
        orderedFrontierWords.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));
        for (RTARegionWord frontier : orderedFrontierWords) {
            List<Boolean> trace = rta.prefixAcceptTrace(frontier);
            List<RTARegionWord> prefixes = frontier.getAllPrefixes();
            int checkSize = Math.min(trace.size(), prefixes.size());
            for (int i = 1; i < checkSize; i++) {
                if (!trace.get(i)) {
                    this.refineCounterExample(prefixes.get(i), true, true);
                    return Optional.of("violates true-prefix acceptance: " + prefixes.get(i));
                }
            }
            if (trace.size() < prefixes.size()) {
                RTARegionWord missingPrefix = prefixes.get(trace.size());
                this.refineCounterExample(missingPrefix, true, true);
                return Optional.of("violates true-prefix acceptance: " + missingPrefix);
            }
        }
        return Optional.empty();
    }

    public void refineCounterExample(RTARegionWord ctx, boolean isPosOrNeg, boolean isCertain) {
        if (ctx == null || ctx.isEmpty()) {
            return;
        }

        if (isPosOrNeg) {
            this.rollbackContradictNegative(ctx);
            this.table.refineCounterExample(ctx, true);
            return;
        }

        if (!isCertain) {
            if (this.containsUncertain(ctx)) {
                return;
            }
            TableRecord record = this.table.exportRecord();
            this.uncertainLogs.add(new UncertainLog(ctx.copy(), record));
            CompositionTraceRecorder.recordUncertainPush(
                    this.activeTurnId,
                    this.id,
                    this.uncertainStackContexts(),
                    this.uncertainStackRecords());
            this.table.refineCounterExample(ctx, false);
            return;
        }

        this.table.refineCounterExample(ctx, false);
        this.table.addCertainNegativeCounterExample(ctx);
    }

    private void rollbackContradictNegative(RTARegionWord positiveCtx) {
        int rollbackIndex = -1;
        for (int i = 0; i < this.uncertainLogs.size(); i++) {
            UncertainLog uncertainLog = this.uncertainLogs.get(i);
            if (positiveCtx.isPrefix(uncertainLog.ctx)) {
                rollbackIndex = i;
                break;
            }
        }
        if (rollbackIndex < 0) {
            return;
        }

        UncertainLog rollbackPoint = this.uncertainLogs.get(rollbackIndex);
        List<String> stackBeforeContexts = this.uncertainStackContexts();
        List<TableRecord> stackBeforeRecords = this.uncertainStackRecords();
        String tableBefore = this.table.toExactString();
        String tableBeforeCsv = this.table.toLongCsv();
        this.table.rollback(rollbackPoint.tableRecord);
        ExptHelp.ROLLBACK_CNT += 1;
        while (this.uncertainLogs.size() > rollbackIndex) {
            this.uncertainLogs.remove(this.uncertainLogs.size() - 1);
        }
        CompositionTraceRecorder.recordRollback(
                this.activeTurnId,
                this.id,
                positiveCtx,
                rollbackIndex,
                stackBeforeContexts,
                stackBeforeRecords,
                this.uncertainStackContexts(),
                this.uncertainStackRecords(),
                tableBefore,
                this.table.toExactString(),
                tableBeforeCsv,
                this.table.toLongCsv(),
                this.pTree.toPrettyString(),
                this.pTree.toNodesCsv(),
                this.pTree.toEdgesCsv());
        if (LearningSetting.isLearningNormalOrVerbose()) {
            String learnerStr = ColoredFront.dealtString(String.format("%02d", this.id + 1), Color.Orange, Type.Common);
            String positiveCtxStr = ColoredFront.dealtString(positiveCtx.toString(), Color.Orange, Type.Common);
            String uncertainCtxStr = ColoredFront.dealtString(rollbackPoint.ctx.toString(), Color.Orange, Type.Common);
            System.out.println(ColoredFront.dealtString("[ ROLLBACK ]", Color.Green, Type.Common));
            System.out.printf("%-" + ROLLBACK_LABEL_WIDTH + "s : %s%n", "LEARNER", learnerStr);
            System.out.printf("%-" + ROLLBACK_LABEL_WIDTH + "s : %s%n", "POSITIVE_CTX", positiveCtxStr);
            System.out.printf("%-" + ROLLBACK_LABEL_WIDTH + "s : %s%n", "UNCERTAIN_CTX", uncertainCtxStr);
        }
    }

    private boolean containsUncertain(RTARegionWord ctx) {
        for (UncertainLog uncertainLog : this.uncertainLogs) {
            if (uncertainLog.ctx.equals(ctx)) {
                return true;
            }
        }
        return false;
    }

    private List<String> uncertainStackContexts() {
        List<String> contexts = new ArrayList<>();
        for (UncertainLog log : this.uncertainLogs) {
            contexts.add(ExactRationalFormat.regionWord(log.ctx));
        }
        return contexts;
    }

    private List<TableRecord> uncertainStackRecords() {
        List<TableRecord> records = new ArrayList<>();
        for (UncertainLog log : this.uncertainLogs) {
            records.add(log.tableRecord);
        }
        return records;
    }

    public void recordFinalTraceStack() {
        CompositionTraceRecorder.recordFinalStack(
                this.activeTurnId,
                this.id,
                this.uncertainStackContexts(),
                this.uncertainStackRecords());
    }

    private int fillCellsByMembershipQuery(List<Cell> cells, Set<ElementNode> unknownTriedInThisPrepare) {
        int updated = 0;
        List<Cell> unresolved = new LinkedList<>();
        for (Cell cell : cells) {
            ElementNode elementNode = cell.getElementNode();
            if (cell.isAccept() != TriBool.unknown) {
                unknownTriedInThisPrepare.remove(elementNode);
                this.turnScopedUnknownTriedNodes.remove(elementNode);
                continue;
            }
            if (unknownTriedInThisPrepare.contains(elementNode)
                    || this.turnScopedUnknownTriedNodes.contains(elementNode)
                    || this.unknownEvidenceVersion.getOrDefault(elementNode, -1) == this.adapter.getPositiveEvidenceVersion()) {
                unresolved.add(cell);
                continue;
            }
            RTARegionWord delta = cell.getPrefix().word().concat(cell.getSuffix().word());
            TriBool outcome = this.raiseMembershipQuery(delta);
            if (outcome != TriBool.unknown) {
                this.table.updateInformation(cell, outcome);
                unknownTriedInThisPrepare.remove(elementNode);
                this.turnScopedUnknownTriedNodes.remove(elementNode);
                this.unknownEvidenceVersion.remove(elementNode);
                updated++;
            } else {
                unknownTriedInThisPrepare.add(elementNode);
                this.turnScopedUnknownTriedNodes.add(elementNode);
                this.unknownEvidenceVersion.put(elementNode, this.adapter.getPositiveEvidenceVersion());
                unresolved.add(cell);
            }
        }
        this.table.requeuePendingMqCells(unresolved);
        return updated;
    }

    private static class UncertainLog {
        private final RTARegionWord ctx;
        private final TableRecord tableRecord;

        private UncertainLog(RTARegionWord ctx, TableRecord tableRecord) {
            this.ctx = ctx;
            this.tableRecord = tableRecord;
        }
    }
}
