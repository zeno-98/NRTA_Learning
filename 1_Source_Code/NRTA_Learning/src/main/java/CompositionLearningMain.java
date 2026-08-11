import language.words.DelayTimedWord;
import language.words.RTARegionWord;
import language.words.ResetLogicalTimedWord;
import learning.learners.composition_table.multiple_RTA_learners.Adapter;
import learning.learners.composition_table.multiple_RTA_learners.ComponentLearner;
import learning.learners.composition_table.multiple_RTA_learners.ExptHelp;
import learning.teachers.Teacher;
import ta.*;

import tools.ExtendSolver;
import tools.FileIO.TAUtil;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.LearningConsole;
import tools.FormatOutput.Type;
import tools.Tuple.Triplet;
import learning.LearningSetting;
import learning.trace.CompositionTraceRecorder;
import verify.GlobalSetting;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompositionLearningMain {
    public record ExperimentStats(
            int rqCnt,
            int mqCnt,
            int mqStage1SuccessCnt,
            int mqStage2SuccessCnt,
            int mqStage3SuccessCnt,
            int eqCnt,
            int uncertainCtxCnt,
            int rollbackCnt,
            int hypNoSolution,
            long timeConsumed,
            TA[] finalHypotheses
    ) {}

    public static final class ExperimentFailure extends RuntimeException {
        private final ExperimentStats stats;

        ExperimentFailure(String message, Throwable cause, ExperimentStats stats) {
            super(message, cause);
            this.stats = stats;
        }

        public ExperimentStats stats() {
            return this.stats;
        }
    }

    private final int componentNum;
    private final ExtendSolver slv;
    private final Teacher teacher;
    private final Adapter adapter;
    private final ComponentLearner[] learners;
    private final TA[] targetTAs;
    private volatile int uncertainCtxCnt = 0;
    private TA[] finalHypotheses = null;

    private static final class QuietSpinner {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread worker;

        void start() {
            if (!this.running.compareAndSet(false, true)) {
                return;
            }
            this.worker = new Thread(() -> {
                final String[] frames = new String[]{
                        "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
                };
                final String yellow = "\u001B[33m";
                final String reset = "\u001B[0m";
                int i = 0;
                while (this.running.get()) {
                    System.err.print("\r" + yellow
                            + "[ LEARNING ] Running " + frames[i % frames.length]
                            + reset);
                    i++;
                    try {
                        Thread.sleep(90);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.err.print("\r" + yellow + "[ LEARNING ] Finished                                " + reset + "\n");
                System.err.flush();
            }, "quiet-learning-spinner");
            this.worker.setDaemon(true);
            this.worker.start();
        }

        void stop() {
            if (!this.running.compareAndSet(true, false)) {
                return;
            }
            if (this.worker != null) {
                this.worker.interrupt();
                try {
                    this.worker.join(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public CompositionLearningMain(String[] TA_paths) {
        GlobalSetting.SHOW_TABLE_CHANGE = false;
        GlobalSetting.SHOW_SINK_TRANSITIONS = LearningSetting.SHOW_SINK_TRANSITIONS;
        this.componentNum = TA_paths.length;
        this.slv = new ExtendSolver();
        this.targetTAs = new TA[this.componentNum];
        this.learners = new ComponentLearner[this.componentNum];

        for (int i = 0; i < this.componentNum; i++) {
            this.targetTAs[i] = TAUtil.buildTAFromJson(TA_paths[i], slv);
            System.out.println("TA at \"" + TA_paths[i] + "\" imported!");

            Set<Action> actions = this.targetTAs[i].getAllActions();
            Set<Clock> clocks = this.targetTAs[i].getAllClocks();
            assert clocks.size() == 1 : "error: the clock number of component should be one";
            for (Clock clock : clocks) {
                this.learners[i] = new ComponentLearner(i, String.format("H%02d", i + 1), clock, actions, this.slv);
            }
        }
        this.ensureTargetsAreRtaWithResetOnEveryTransition();

        NTA targetNta = new NTA(this.targetTAs);
        ResetLogicalTimedWord.setKMap(targetNta.getK_map());

        this.teacher = new Teacher(targetNta, this.slv);
        this.adapter = new Adapter(this.teacher, this.learners);
        for (ComponentLearner learner : this.learners) learner.setAdapter(this.adapter);
        CompositionTraceRecorder.initialize(this.targetTAs);
    }

    private void ensureTargetsAreRtaWithResetOnEveryTransition() {
        for (TA ta : this.targetTAs) {
            Set<Clock> allClocks = ta.getAllClocks();
            if (allClocks.size() != 1) {
                throw new IllegalStateException(
                        "Target TA \"" + ta.getName() + "\" is not RTA: expected exactly 1 clock, found " + allClocks.size() + "."
                );
            }

            for (Transition transition : ta.getDelta().values()) {
                Set<Clock> reset = transition.getReset();
                if (!Objects.equals(reset, allClocks)) {
                    throw new IllegalStateException(
                            "Target TA \"" + ta.getName() + "\" is not RTA: transition " + transition.getId()
                                    + " does not reset all clocks. expected=" + allClocks + ", actual=" + reset
                                    + ", transition=" + transition
                    );
                }
            }
        }
    }

    public void startExperiment(File logDir) {
        TA[] allHypothesis = new TA[this.componentNum];
        Set<DelayTimedWord> seenCertainCounterExamples = new HashSet<>();
        Map<DelayTimedWord, Integer> uncertainCounterExampleSeenEpoch = new HashMap<>();

        if (LearningSetting.isLearningNormalOrVerbose()) {
            this.teacher.print_target();
        }

        int turns = 0;
        while (true) {
            this.throwIfInterrupted();
            CompositionTraceRecorder.recordTurnStart(turns);
            if (LearningSetting.isLearningNormalOrVerbose()) {
                LearningConsole.showTurnHeader(turns);
            }
            for (int i = 0; i < this.componentNum; i++) {
                this.throwIfInterrupted();
                this.learners[i].beginTurn(turns);
            }

            for (int i = 0; i < this.componentNum; i++) {
                this.throwIfInterrupted();
                if (LearningSetting.isLearningVerbose()) {
                    LearningConsole.showLearnerHeader(i, this.componentNum, "TABLE PREPARE");
                }
                
                ComponentLearner learner = this.learners[i];

                boolean prepared = learner.prepareTable();
                if (!prepared) {
                    String err = "[FATAL] prepareTable failed at learner " + i + " in turn " + turns + ".";
                    System.err.println(err);
                    throw new IllegalStateException(err);
                }
                if (LearningSetting.isLearningVerbose()) {
                    System.out.println(
                            ColoredFront.dealtString(
                                    "********* the unknown table *********",
                                    Color.Green,
                                    Type.Common));
                    System.out.println(learner.currentTableString());
                }

                allHypothesis[i] = learner.generateHypothesis();
                this.enforceLocalConsistency(learner, allHypothesis, i);
                CompositionTraceRecorder.recordRoundSnapshot(
                        turns, i, learner, allHypothesis[i]);

                if (LearningSetting.isLearningVerbose()) {
                    System.out.println(ColoredFront.dealtString("**** the corresponding hypothesis ***", Color.Green, Type.Common));
                    System.out.println(allHypothesis[i].getMainDetail(true));
                }
                if (LearningSetting.SAVE_HYPOTHESIS) {
                    TAUtil.buildJsonFromTA(logDir.toPath().resolve(String.format("viz/H%02d/T%d.json", i + 1, turns)).toString(), allHypothesis[i]);
                }
            }
            if (LearningSetting.isLearningNormalOrVerbose()) {
                LearningConsole.showStageHeader("Start Equivalent Query");
            }

            NTA hyp = new NTA(allHypothesis);
            Triplet<Boolean, DelayTimedWord, Boolean> reply = this.adapter.processEquivalentQuery(hyp);
            if (reply.p0()) {
                CompositionTraceRecorder.recordEquivalent(turns);
                for (ComponentLearner learner : this.learners) {
                    learner.recordFinalTraceStack();
                }
                this.recordRoundStatistics(turns, true);
                this.finalHypotheses = Arrays.copyOf(allHypothesis, allHypothesis.length);
                if (LearningSetting.isLearningNormalOrVerbose()) {
                    System.out.println(ColoredFront.dealtString("********* Target Successfully Ended! *********", Color.Green, Type.Common));
                }
                return;
            } else {
                DelayTimedWord counterExample = reply.p1();
                Adapter.CounterExampleClassification classification = this.adapter.classifyCounterExample(counterExample, reply.p2());
                List<RTARegionWord> projections = this.adapter.projectToRTA(counterExample);
                List<String> componentClassifications =
                        this.adapter.classifyCounterExampleComponents(counterExample, reply.p2());
                CompositionTraceRecorder.recordCounterexample(
                        turns,
                        counterExample,
                        reply.p2(),
                        classification.certain(),
                        classification.uncertainNegativeComponents(),
                        projections,
                        componentClassifications);
                int rollbackEpoch = ExptHelp.ROLLBACK_CNT;
                if (classification.certain()) {
                    if (seenCertainCounterExamples.contains(counterExample)) {
                        if (LearningSetting.isLearningNormalOrVerbose()) {
                            System.out.println(
                                    ColoredFront.dealtString(
                                            "********* Repeated certain counterexample detected, stop learning. *********",
                                            Color.Orange,
                                            Type.Common));
                            String label = reply.p2() ? "REPEATED_CERTAIN_POS_CTX" : "REPEATED_CERTAIN_NEG_CTX";
                            System.out.printf("%-25s : %s%n", label, ColoredFront.dealtString(counterExample.toString(), Color.Orange, Type.Common));
                        }
                        this.finalHypotheses = Arrays.copyOf(allHypothesis, allHypothesis.length);
                        return;
                    }
                    seenCertainCounterExamples.add(counterExample);
                } else {
                    this.uncertainCtxCnt++;
                    Integer seenEpoch = uncertainCounterExampleSeenEpoch.get(counterExample);
                    if (seenEpoch != null && seenEpoch == rollbackEpoch) {
                        if (LearningSetting.isLearningNormalOrVerbose()) {
                            System.out.println(
                                    ColoredFront.dealtString(
                                            "********* Warning: repeated uncertain counterexample in current rollback epoch. *********",
                                            Color.Orange,
                                            Type.Common));
                            String label = reply.p2() ? "REPEATED_UNCERTAIN_POS_CTX" : "REPEATED_UNCERTAIN_NEG_CTX";
                            System.out.printf("%-27s : %s%n", label, ColoredFront.dealtString(counterExample.toString(), Color.Orange, Type.Common));
                        }
                    }
                    uncertainCounterExampleSeenEpoch.put(counterExample, rollbackEpoch);
                }

                if (LearningSetting.isLearningNormalOrVerbose()) {
                    String prefix = classification.certain() ? "CERTAIN_" : "UNCERTAIN_";
                    String label = reply.p2() ? prefix + "POSITIVE_CTX" : prefix + "NEGATIVE_CTX";
                    System.out.printf("%-14s : %s%n", label, ColoredFront.dealtString(counterExample.toString(), Color.Orange, Type.Common));
                }
                this.adapter.refineCounterExample(counterExample, reply.p2());
                this.recordRoundStatistics(turns, false);
                turns += 1;
            }
        }
    }

    private void enforceLocalConsistency(ComponentLearner learner, TA[] allHypothesis, int learnerIndex) {
        int guard = 0;
        while (true) {
            this.throwIfInterrupted();
            Optional<String> violation = learner.repairOneLocalInconsistency();
            if (violation.isEmpty()) {
                return;
            }
            if (LearningSetting.isLearningVerbose()) {
                System.out.printf("[LOCAL-REPAIR][learner=%02d] %s%n", learnerIndex + 1, violation.get());
            }
            boolean prepared = learner.prepareTable();
            if (!prepared) {
                String err = "[FATAL] local consistency repair prepare failed at learner " + learnerIndex + ".";
                System.err.println(err);
                throw new IllegalStateException(err);
            }
            allHypothesis[learnerIndex] = learner.generateHypothesis();
            guard++;
            if (guard > 20000) {
                String err = "[FATAL] local consistency repair overflow at learner " + learnerIndex + ".";
                System.err.println(err);
                throw new IllegalStateException(err);
            }
        }
    }

    private void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("CompositionLearningMain interrupted");
        }
    }

    private void recordRoundStatistics(int turn, boolean equivalent) {
        Triplet<Integer, Integer, Integer> queries = this.teacher.snapshotQueryCount();
        CompositionTraceRecorder.recordRoundStatistics(
                turn,
                equivalent,
                queries.p0(),
                this.adapter.getTotalMqCnt(),
                queries.p2(),
                this.adapter.getFirstStageMqSuccessCnt(),
                this.adapter.getSecondStageMqSuccessCnt(),
                this.adapter.getThirdStageMqSuccessCnt(),
                this.uncertainCtxCnt,
                ExptHelp.ROLLBACK_CNT,
                ExptHelp.Hyp_No_Solution);
    }

    private ExperimentStats snapshotStats(Instant start) {
        Triplet<Integer, Integer, Integer> queryCnt = this.teacher.snapshotQueryCount();
        return new ExperimentStats(
                queryCnt.p0(),
                this.adapter.getTotalMqCnt(),
                this.adapter.getFirstStageMqSuccessCnt(),
                this.adapter.getSecondStageMqSuccessCnt(),
                this.adapter.getThirdStageMqSuccessCnt(),
                queryCnt.p2(),
                this.uncertainCtxCnt,
                ExptHelp.ROLLBACK_CNT,
                ExptHelp.Hyp_No_Solution,
                Duration.between(start, Instant.now()).toMillis(),
                this.finalHypotheses == null ? null : Arrays.copyOf(this.finalHypotheses, this.finalHypotheses.length)
        );
    }

    private static void printProgress(ExperimentStats stats) {
        System.out.printf(
                "CL_PROGRESS:timeMs=%d,rq=%d,mq=%d,eq=%d,stage1=%d,stage2=%d,stage3=%d,uncertain=%d,rollback=%d,noSolution=%d%n",
                stats.timeConsumed(), stats.rqCnt(), stats.mqCnt(), stats.eqCnt(),
                stats.mqStage1SuccessCnt(), stats.mqStage2SuccessCnt(),
                stats.mqStage3SuccessCnt(), stats.uncertainCtxCnt(),
                stats.rollbackCnt(), stats.hypNoSolution());
        System.out.flush();
    }

    public static ExperimentStats runCase(String path) {
        Action.GLOBAL_ACTION_MAP.clear();
        ExptHelp.ROLLBACK_CNT = 0;
        ExptHelp.Hyp_No_Solution = 0;
        ExptHelp.timeConsumed = 0;
        File pathFile = new File(path);
        String[] taPaths;
        File logDir;
        if (pathFile.isFile()) {
            if (!pathFile.getName().endsWith(".json")) {
                throw new IllegalArgumentException("error: file path must end with .json, got: " + path);
            }
            taPaths = new String[]{pathFile.getPath()};
            logDir = pathFile.getParentFile() == null ? new File(".") : pathFile.getParentFile();
        } else if (pathFile.isDirectory()) {
            File[] taFiles = pathFile.listFiles();
            if (taFiles == null) {
                throw new IllegalArgumentException("error: path has no files: " + path);
            }
            taPaths = Arrays.stream(taFiles)
                    .filter(file -> file.getName().endsWith(".json"))
                    .filter(file -> file.getName().startsWith("P"))
                    .map(File::getPath)
                    .toArray(String[]::new);
            Arrays.sort(taPaths);
            logDir = pathFile;
        } else {
            throw new IllegalArgumentException("error: path is neither file nor directory: " + path);
        }
        if (taPaths.length == 0) {
            throw new IllegalArgumentException("error: no json TA files found under path: " + path);
        }
        CompositionLearningMain exp = new CompositionLearningMain(taPaths);
        QuietSpinner spinner = null;
        if (LearningSetting.isLearningQuiet()) {
            spinner = new QuietSpinner();
            spinner.start();
        }

        int timeoutSeconds = LearningSetting.RUN_CASE_TIMEOUT_SECONDS;
        Instant start = Instant.now();
        ScheduledExecutorService progressExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cl-progress-reporter");
            thread.setDaemon(true);
            return thread;
        });
        progressExecutor.scheduleAtFixedRate(() -> {
            try {
                printProgress(exp.snapshotStats(start));
            } catch (Throwable ignored) {
                // A previous snapshot remains available if an OOM prevents a fresh one.
            }
        }, 1, 1, TimeUnit.SECONDS);
        try {
            if (timeoutSeconds <= 0) {
                exp.startExperiment(logDir);
            } else {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<?> future = executor.submit(() -> exp.startExperiment(logDir));
                try {
                    future.get(timeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    ExperimentStats stats = exp.snapshotStats(start);
                    printProgress(stats);
                    throw new ExperimentFailure(
                            "CompositionLearningMain.run_case timeout after " + timeoutSeconds + " seconds", e, stats);
                } catch (InterruptedException e) {
                    future.cancel(true);
                    Thread.currentThread().interrupt();
                    ExperimentStats stats = exp.snapshotStats(start);
                    printProgress(stats);
                    throw new ExperimentFailure("CompositionLearningMain.run_case interrupted", e, stats);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    ExperimentStats stats = exp.snapshotStats(start);
                    printProgress(stats);
                    String kind = cause instanceof OutOfMemoryError ? "OOM" : "failed";
                    throw new ExperimentFailure(
                            "CompositionLearningMain.run_case " + kind + ": "
                                    + cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                            cause, stats);
                } finally {
                    executor.shutdownNow();
                }
            }
        } finally {
            progressExecutor.shutdownNow();
            if (spinner != null) {
                spinner.stop();
            }
        }
        ExperimentStats stats = exp.snapshotStats(start);
        printProgress(stats);
        return stats;
    }
}
