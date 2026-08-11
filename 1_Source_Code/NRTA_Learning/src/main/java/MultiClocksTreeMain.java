import language.words.ResetLogicalTimedWord;
import learning.LearningSetting;
import learning.learners.multi_clocks_tree.MultiClockTreeLearner;
import learning.teachers.Teacher;
import ta.Action;
import ta.Clock;
import ta.NTA;
import ta.TA;
import tools.ExtendSolver;
import tools.FileIO.TAParallel;
import tools.FileIO.TAUtil;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.LearningConsole;
import tools.FormatOutput.Type;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;
import verify.GlobalSetting;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public class MultiClocksTreeMain {
    public record RunResult(Triplet<Integer, Integer, Integer> queryCount, long timeMs, NTA finalHypothesis) {}

    private final Teacher teacher;
    private final MultiClockTreeLearner learner;
    private final ExtendSolver ctx;

    public MultiClocksTreeMain(String[] taPaths) {
        applyLearningLogToGlobalSetting();
        this.ctx = new ExtendSolver();
        TA[] targetTAs = new TA[taPaths.length];
        for (int i = 0; i < taPaths.length; i++) {
            targetTAs[i] = TAUtil.buildTAFromJson(taPaths[i], this.ctx);
            System.out.println("TA at \"" + taPaths[i] + "\" imported!");
        }
        TA paralleled = TAParallel.parallel(targetTAs);
        if (paralleled == null) {
            throw new IllegalStateException("failed to parallel target TAs");
        }
        ResetLogicalTimedWord.setKMap(paralleled.getK());
        NTA targetNTA = new NTA(paralleled);
        List<Action> actionList = new ArrayList<>(targetNTA.getAllActions());
        List<Clock> clockList = new ArrayList<>(targetNTA.getAllClocks());
        this.teacher = new Teacher(targetNTA, this.ctx);
        this.teacher.setEquivalence_strategy(Teacher.SHORTEST_FIRST);
        if (LearningSetting.isLearningNormalOrVerbose()) {
            this.teacher.print_target();
        }
        this.learner = new MultiClockTreeLearner(
                clockList,
                actionList,
                GlobalSetting.DEFAULT_HYPOTHESIS_NAME,
                targetNTA.getK_map(),
                this.teacher,
                this.ctx
        );
        this.learner.initTree();
    }

    private static void applyLearningLogToGlobalSetting() {
        GlobalSetting.SHOW_HYPOTHESIS = LearningSetting.isLearningVerbose();
        GlobalSetting.SHOW_COUNTER_EXAMPLES = LearningSetting.isLearningNormalOrVerbose();
        GlobalSetting.SHOW_TABLE_CHANGE = false;
        GlobalSetting.SHOW_SINK_TRANSITIONS = LearningSetting.SHOW_SINK_TRANSITIONS;
    }

    public void run() {
        this.runWithHypothesis();
    }

    public TA runWithHypothesis() {
        int turn = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("MultiClocksTreeMain interrupted");
            }
            if (LearningSetting.isLearningNormalOrVerbose()) {
                LearningConsole.showTurnHeader(turn);
            }
            if (LearningSetting.isLearningVerbose()) {
                this.learner.printClassifyTree();
            }
            TA hypothesis = this.learner.generateHypothesis();
            if (GlobalSetting.SHOW_HYPOTHESIS) {
                hypothesis.printMainDetail();
            }
            if (LearningSetting.SAVE_HYPOTHESIS) {
                TAUtil.buildJsonFromTA(String.format("./ttt_hyp_T%04d.json", turn), hypothesis);
            }
            Triplet<Boolean, ResetLogicalTimedWord, Boolean> reply =
                    this.teacher.EquivalenceQuery(new NTA(hypothesis));
            if (reply.getP0()) {
                return hypothesis;
            }
            if (GlobalSetting.SHOW_COUNTER_EXAMPLES) {
                String label = reply.getP2() ? "POSITIVE_CTX" : "NEGATIVE_CTX";
                System.out.printf("%-12s : %s%n", label, ColoredFront.dealtString(reply.getP1().toString(), Color.Orange, Type.Common));
            }
            this.learner.refineCounterExample(reply.getP1(), reply.getP2());
            turn++;
        }
    }

    public Pair<Triplet<Integer, Integer, Integer>, Long> runAndCountTimeWithStats() {
        RunResult result = this.runAndCollectStats();
        return new Pair<>(result.queryCount(), result.timeMs());
    }

    public RunResult runAndCollectStats() {
        Instant start = Instant.now();
        ScheduledExecutorService progressReporter = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tct-progress-reporter");
            thread.setDaemon(true);
            return thread;
        });
        progressReporter.scheduleAtFixedRate(() -> {
            Triplet<Integer, Integer, Integer> queries = this.teacher.snapshotQueryCount();
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.printf(
                    "ALG_PROGRESS:algorithm=TCT,timeMs=%d,rq=%d,mq=%d,eq=%d%n",
                    elapsed, queries.p0(), queries.p1(), queries.p2()
            );
        }, 1, 1, TimeUnit.SECONDS);
        LearningConsole.QuietSpinner spinner = null;
        if (LearningSetting.isLearningQuiet()) {
            spinner = new LearningConsole.QuietSpinner();
            spinner.start();
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TA finalHypothesis = null;
        try {
            Future<TA> future = executor.submit(this::runWithHypothesis);
            int timeoutSeconds = LearningSetting.RUN_CASE_TIMEOUT_SECONDS;
            if (timeoutSeconds <= 0) {
                finalHypothesis = future.get();
            } else {
                finalHypothesis = future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
        } catch (TimeoutException e) {
            executor.shutdownNow();
            return new RunResult(this.teacher.consumeQueryCount(), -1L, null);
        } catch (Exception e) {
            executor.shutdownNow();
            return new RunResult(this.teacher.consumeQueryCount(), -2L, null);
        } finally {
            progressReporter.shutdownNow();
            executor.shutdownNow();
            if (spinner != null) {
                spinner.stop();
            }
        }
        long duration = Duration.between(start, Instant.now()).toMillis();
        return new RunResult(this.teacher.consumeQueryCount(), duration, new NTA(finalHypothesis));
    }

    public static Pair<Triplet<Integer, Integer, Integer>, Long> runCase(String path) {
        File exampleDirectory = new File(path);
        String[] taPaths = Arrays.stream(Objects.requireNonNull(exampleDirectory.listFiles()))
                .filter(file -> file.getName().endsWith(".json"))
                .filter(file -> file.getName().startsWith("P"))
                .map(File::getPath)
                .toArray(String[]::new);
        Arrays.sort(taPaths);
        MultiClocksTreeMain exp = new MultiClocksTreeMain(taPaths);
        return exp.runAndCountTimeWithStats();
    }
}
