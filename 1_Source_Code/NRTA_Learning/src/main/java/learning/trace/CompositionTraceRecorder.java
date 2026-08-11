package learning.trace;

import language.words.DelayTimedWord;
import language.words.RTARegionWord;
import learning.learners.composition_table.multiple_RTA_learners.ComponentLearner;
import learning.learners.composition_table.multiple_RTA_learners.table.Table;
import learning.learners.composition_table.multiple_RTA_learners.table.TableRecord;
import ta.TA;
import tools.FileIO.TAUtil;
import tools.FormatOutput.ExactRationalFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional, deterministic trace export for paper examples.
 *
 * <p>The recorder is disabled unless {@code -Dnrta.trace.dir=<directory>} is
 * supplied. Consequently, ordinary experiments do not perform any additional
 * I/O and the learning algorithm is unchanged.</p>
 */
public final class CompositionTraceRecorder {
    private static Path root;
    private static int eventSequence;
    private static int rollbackSequence;
    private static final Map<String, Integer> smtSolveCounts = new HashMap<>();

    private CompositionTraceRecorder() {}

    public static synchronized boolean enabled() {
        String configured = System.getProperty("nrta.trace.dir", "").trim();
        return !configured.isEmpty();
    }

    public static synchronized void initialize(TA[] targets) {
        if (!enabled()) {
            root = null;
            return;
        }
        root = Path.of(System.getProperty("nrta.trace.dir")).toAbsolutePath().normalize();
        eventSequence = 0;
        rollbackSequence = 0;
        smtSolveCounts.clear();
        try {
            Files.createDirectories(root.resolve("target"));
            Files.createDirectories(root.resolve("rounds"));
            Files.createDirectories(root.resolve("rollback"));
            Files.createDirectories(root.resolve("smt"));
            write(root.resolve("events.csv"),
                    "sequence,event,round,component,detail\n");
            write(root.resolve("counterexamples.csv"),
                    "sequence,round,global_classification,certainty,uncertain_negative_components,global_word,component,component_classification,projection\n");
            write(root.resolve("round_statistics.csv"),
                    "round,equivalent,rq,mq,eq,stage1,stage2,stage3,uncertain_contexts,rollbacks,no_solution\n");
            write(root.resolve("smt_solves.csv"),
                    "sequence,round,component,solve_index,solver,status,table_shape,directory\n");
            write(root.resolve("run.txt"),
                    "created_at=" + Instant.now() + "\n"
                            + "trace_format=1\n"
                            + "component_count=" + targets.length + "\n");
            for (int i = 0; i < targets.length; i++) {
                TAUtil.buildJsonFromTA(root.resolve(String.format("target/P%d.json", i + 1)).toString(), targets[i]);
            }
            event("TRACE_INITIALIZED", -1, -1, "targets=" + targets.length);
        } catch (IOException exception) {
            throw new RuntimeException("cannot initialize composition trace at " + root, exception);
        }
    }

    public static synchronized void recordTurnStart(int turn) {
        event("TURN_START", turn, -1, "");
    }

    public static synchronized void recordRoundSnapshot(
            int turn,
            int component,
            ComponentLearner learner,
            TA hypothesis) {
        if (root == null) {
            return;
        }
        Path componentDir = root.resolve(String.format(
                "rounds/round_%02d/component_%02d", turn, component + 1));
        try {
            Files.createDirectories(componentDir);
            write(componentDir.resolve("observation_table.txt"), learner.currentTableExactString());
            write(componentDir.resolve("observation_table.csv"), learner.currentTableCsv());
            write(componentDir.resolve("observation_table_fingerprint.txt"),
                    learner.currentTableDebugFingerprint() + "\n");
            write(componentDir.resolve("p_tree.txt"), learner.getPTree().toPrettyString());
            write(componentDir.resolve("p_tree_nodes.csv"), learner.getPTree().toNodesCsv());
            write(componentDir.resolve("p_tree_edges.csv"), learner.getPTree().toEdgesCsv());
            TAUtil.buildJsonFromTA(componentDir.resolve("hypothesis.json").toString(), hypothesis);
            event("COMPONENT_SNAPSHOT", turn, component,
                    "directory=" + root.relativize(componentDir));
        } catch (IOException exception) {
            throw new RuntimeException("cannot record component snapshot", exception);
        }
    }

    public static synchronized void recordEquivalent(int turn) {
        event("EQUIVALENT", turn, -1, "");
    }

    public static synchronized void recordSmtSolve(
            int turn,
            int component,
            Table.SmtSolveSnapshot snapshot) {
        if (root == null || snapshot == null) {
            return;
        }
        String key = turn + ":" + component;
        int solveIndex = smtSolveCounts.merge(key, 1, Integer::sum);
        int sequence = ++eventSequence;
        Path solveDir = root.resolve(String.format(
                "smt/round_%02d/component_%02d/solve_%02d",
                turn, component + 1, solveIndex));
        String constraints = "; closed constraint\n(assert " + snapshot.closedConstraint() + ")\n\n"
                + "; consistency constraint\n(assert " + snapshot.consistentConstraint() + ")\n\n"
                + "; sink constraint\n(assert " + snapshot.sinkConstraint() + ")\n";
        write(solveDir.resolve("status.txt"),
                "solver=" + snapshot.solverKind() + "\n"
                        + "status=" + snapshot.status() + "\n"
                        + "table_shape=" + snapshot.tableShape() + "\n");
        write(solveDir.resolve("solver_input.smt2"), snapshot.solverInput() + "\n");
        write(solveDir.resolve("constraints.smt2"), constraints);
        write(solveDir.resolve("closed_constraint.txt"), snapshot.closedConstraint() + "\n");
        write(solveDir.resolve("consistent_constraint.txt"), snapshot.consistentConstraint() + "\n");
        write(solveDir.resolve("sink_constraint.txt"), snapshot.sinkConstraint() + "\n");
        write(solveDir.resolve("model.txt"), snapshot.model() + "\n");
        write(solveDir.resolve("solved_observation_table.csv"), snapshot.solvedTableCsv());
        append(root.resolve("smt_solves.csv"), csv(
                Integer.toString(sequence), Integer.toString(turn),
                Integer.toString(component + 1), Integer.toString(solveIndex),
                snapshot.solverKind(), snapshot.status(), snapshot.tableShape(),
                root.relativize(solveDir).toString()));
        appendEvent(sequence, "SMT_SOLVE", turn, component,
                "solve=" + solveIndex + ";status=" + snapshot.status()
                        + ";directory=" + root.relativize(solveDir));
    }

    public static synchronized void recordRoundStatistics(
            int turn,
            boolean equivalent,
            int rq,
            int mq,
            int eq,
            int stage1,
            int stage2,
            int stage3,
            int uncertain,
            int rollback,
            int noSolution) {
        if (root == null) {
            return;
        }
        append(root.resolve("round_statistics.csv"), csv(
                Integer.toString(turn), Boolean.toString(equivalent),
                Integer.toString(rq), Integer.toString(mq), Integer.toString(eq),
                Integer.toString(stage1), Integer.toString(stage2), Integer.toString(stage3),
                Integer.toString(uncertain), Integer.toString(rollback),
                Integer.toString(noSolution)));
    }

    public static synchronized void recordCounterexample(
            int turn,
            DelayTimedWord globalWord,
            boolean positive,
            boolean certain,
            int uncertainNegativeComponents,
            List<RTARegionWord> projections,
            List<String> componentClassifications) {
        if (root == null) {
            return;
        }
        int sequence = ++eventSequence;
        String globalClassification = positive ? "POSITIVE" : "NEGATIVE";
        for (int i = 0; i < projections.size(); i++) {
            append(root.resolve("counterexamples.csv"), csv(
                    Integer.toString(sequence),
                    Integer.toString(turn),
                    globalClassification,
                    certain ? "CERTAIN" : "UNCERTAIN",
                    Integer.toString(uncertainNegativeComponents),
                    ExactRationalFormat.delayWord(globalWord),
                    Integer.toString(i + 1),
                    componentClassifications.get(i),
                    ExactRationalFormat.regionWord(projections.get(i))));
        }
        appendEvent(sequence, "COUNTEREXAMPLE", turn, -1,
                globalClassification + ";" + (certain ? "CERTAIN" : "UNCERTAIN"));
    }

    public static synchronized void recordUncertainPush(
            int turn,
            int component,
            List<String> stackContexts,
            List<TableRecord> stackRecords) {
        if (root == null) {
            return;
        }
        int sequence = ++eventSequence;
        Path path = root.resolve(String.format(
                "rollback/stack_after_push_%03d_component_%02d.csv", sequence, component + 1));
        writeStack(path, stackContexts, stackRecords, -1);
        appendEvent(sequence, "UNCERTAIN_PUSH", turn, component,
                "depth=" + stackContexts.size() + ";snapshot=" + root.relativize(path));
    }

    public static synchronized void recordFinalStack(
            int turn,
            int component,
            List<String> stackContexts,
            List<TableRecord> stackRecords) {
        if (root == null) {
            return;
        }
        int sequence = ++eventSequence;
        Path path = root.resolve(String.format(
                "rollback/final_stack_component_%02d.csv", component + 1));
        writeStack(path, stackContexts, stackRecords, -1);
        appendEvent(sequence, "FINAL_STACK", turn, component,
                "depth=" + stackContexts.size() + ";snapshot=" + root.relativize(path));
    }

    public static synchronized void recordRollback(
            int turn,
            int component,
            RTARegionWord positiveContext,
            int rollbackIndex,
            List<String> stackBeforeContexts,
            List<TableRecord> stackBeforeRecords,
            List<String> stackAfterContexts,
            List<TableRecord> stackAfterRecords,
            String tableBefore,
            String tableAfter,
            String tableBeforeCsv,
            String tableAfterCsv,
            String pTree,
            String pTreeNodesCsv,
            String pTreeEdgesCsv) {
        if (root == null) {
            return;
        }
        int sequence = ++eventSequence;
        int rollback = ++rollbackSequence;
        Path rollbackDir = root.resolve(String.format("rollback/rollback_%02d", rollback));
        try {
            Files.createDirectories(rollbackDir);
            write(rollbackDir.resolve("metadata.txt"),
                    "sequence=" + sequence + "\n"
                            + "turn=" + turn + "\n"
                            + "component=" + (component + 1) + "\n"
                            + "positive_context=" + ExactRationalFormat.regionWord(positiveContext) + "\n"
                            + "rollback_index=" + rollbackIndex + "\n"
                            + "stack_depth_before=" + stackBeforeContexts.size() + "\n"
                            + "stack_depth_after=" + stackAfterContexts.size() + "\n");
            writeStack(rollbackDir.resolve("stack_before.csv"),
                    stackBeforeContexts, stackBeforeRecords, rollbackIndex);
            writeStack(rollbackDir.resolve("stack_after.csv"),
                    stackAfterContexts, stackAfterRecords, -1);
            write(rollbackDir.resolve("observation_table_before.txt"), tableBefore);
            write(rollbackDir.resolve("observation_table_after.txt"), tableAfter);
            write(rollbackDir.resolve("observation_table_before.csv"), tableBeforeCsv);
            write(rollbackDir.resolve("observation_table_after.csv"), tableAfterCsv);
            write(rollbackDir.resolve("p_tree_at_rollback.txt"), pTree);
            write(rollbackDir.resolve("p_tree_nodes_at_rollback.csv"), pTreeNodesCsv);
            write(rollbackDir.resolve("p_tree_edges_at_rollback.csv"), pTreeEdgesCsv);
            appendEvent(sequence, "ROLLBACK", turn, component,
                    "index=" + rollbackIndex + ";directory=" + root.relativize(rollbackDir));
        } catch (IOException exception) {
            throw new RuntimeException("cannot record rollback", exception);
        }
    }

    private static void writeStack(
            Path path,
            List<String> contexts,
            List<TableRecord> records,
            int selectedIndex) {
        StringBuilder output = new StringBuilder("index,selected,uncertain_context,s_area_size,r_area_size,e_area_size\n");
        Path detailDir = path.resolveSibling(
                path.getFileName().toString().replaceFirst("\\.csv$", "_records"));
        for (int i = 0; i < contexts.size(); i++) {
            TableRecord record = records.get(i);
            output.append(csv(
                    Integer.toString(i),
                    Boolean.toString(i == selectedIndex),
                    contexts.get(i),
                    Integer.toString(record.getSAreaWords().size()),
                    Integer.toString(record.getRAreaWords().size()),
                    Integer.toString(record.getEAreaWords().size())));
            StringBuilder detail = new StringBuilder("area,word_index,word\n");
            appendWords(detail, "S", record.getSAreaWords());
            appendWords(detail, "R", record.getRAreaWords());
            appendWords(detail, "E", record.getEAreaWords());
            write(detailDir.resolve(String.format("frame_%02d.csv", i)), detail.toString());
        }
        write(path, output.toString());
    }

    private static void appendWords(StringBuilder output, String area, List<RTARegionWord> words) {
        for (int i = 0; i < words.size(); i++) {
            output.append(csv(area, Integer.toString(i),
                    ExactRationalFormat.regionWord(words.get(i))));
        }
    }

    private static void event(String event, int turn, int component, String detail) {
        if (root == null) {
            return;
        }
        int sequence = ++eventSequence;
        appendEvent(sequence, event, turn, component, detail);
    }

    private static void appendEvent(int sequence, String event, int turn, int component, String detail) {
        append(root.resolve("events.csv"), csv(
                Integer.toString(sequence),
                event,
                turn < 0 ? "" : Integer.toString(turn),
                component < 0 ? "" : Integer.toString(component + 1),
                detail));
    }

    private static String csv(String... fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            String value = fields[i] == null ? "" : fields[i];
            row.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return row.append('\n').toString();
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new RuntimeException("cannot write trace file " + path, exception);
        }
    }

    private static void append(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new RuntimeException("cannot append trace file " + path, exception);
        }
    }
}
