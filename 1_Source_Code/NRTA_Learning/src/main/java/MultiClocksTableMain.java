import language.words.ResetLogicalTimedWord;
import learning.LearningSetting;
import learning.learners.multi_clocks_table.MultiClockTableLearner;
import learning.teachers.Teacher;
import ta.NTA;
import ta.TA;
import tools.ExtendSolver;
import tools.FileIO.TAUtil;
import tools.FormatOutput.LearningConsole;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;
import verify.GlobalSetting;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.*;

public class MultiClocksTableMain {
    public record RunResult(Triplet<Integer, Integer, Integer> queryCount, long timeMs, NTA finalHypothesis) {}

    private final ExtendSolver ctx;
    private final TA[] targetTAs;
    private final NTA NTA;
    private final Teacher teacher;
    private final MultiClockTableLearner learner;

    public MultiClocksTableMain(String[] casePaths) {
        applyLearningLogToGlobalSetting();
        this.ctx = new ExtendSolver();
        this.targetTAs = new TA[casePaths.length];
        for (int i = 0; i < casePaths.length; i++) {
            this.targetTAs[i] = TAUtil.buildTAFromJson(casePaths[i], this.ctx);
            System.out.println("TA at \"" + casePaths[i] + "\" imported!");
        }

        this.NTA = new NTA(this.targetTAs);
        ResetLogicalTimedWord.setKMap(this.NTA.getK_map());
        this.teacher = new Teacher(this.NTA, this.ctx);
        this.teacher.setEquivalence_strategy(Teacher.SHORTEST_FIRST);
        if (LearningSetting.isLearningNormalOrVerbose()) {
            this.teacher.print_target();
        }

        this.learner = new MultiClockTableLearner(
                this.teacher,
                this.NTA.getAllClocks(),
                this.NTA.getAllActions(),
                GlobalSetting.DEFAULT_HYPOTHESIS_NAME
        );
        this.learner.setClosedMethod(MultiClockTableLearner.ADD_ZERO);
        if (LearningSetting.SAVE_HYPOTHESIS) {
            this.learner.setHypothesisSaveDir(new File("."));
        }
    }

    private static void applyLearningLogToGlobalSetting() {
        GlobalSetting.SHOW_PREPARED_TABLE = LearningSetting.isLearningVerbose();
        GlobalSetting.SHOW_HYPOTHESIS = LearningSetting.isLearningVerbose();
        GlobalSetting.SHOW_COUNTER_EXAMPLES = LearningSetting.isLearningNormalOrVerbose();
        GlobalSetting.SHOW_TABLE_CHANGE = LearningSetting.isLearningPresentation();
        GlobalSetting.SHOW_SINK_TRANSITIONS = LearningSetting.SHOW_SINK_TRANSITIONS;
    }

    public Pair<Triplet<Integer, Integer, Integer>, Long> runAndCountTimeWithStats() {
        RunResult result = this.runAndCollectStats();
        return new Pair<>(result.queryCount(), result.timeMs());
    }

    public RunResult runAndCollectStats() {
        Instant start = Instant.now();
        ScheduledExecutorService progressReporter = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tot-progress-reporter");
            thread.setDaemon(true);
            return thread;
        });
        progressReporter.scheduleAtFixedRate(() -> {
            Triplet<Integer, Integer, Integer> queries = this.teacher.snapshotQueryCount();
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.printf(
                    "ALG_PROGRESS:algorithm=TOT,timeMs=%d,rq=%d,mq=%d,eq=%d%n",
                    elapsed, queries.p0(), queries.p1(), queries.p2()
            );
        }, 1, 1, TimeUnit.SECONDS);
        LearningConsole.QuietSpinner spinner = null;
        if (LearningSetting.isLearningQuiet()) {
            spinner = new LearningConsole.QuietSpinner();
            spinner.start();
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        NTA finalHypothesis = null;
        try {
            Future<NTA> future = executor.submit(this.learner::learn);
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
        return new RunResult(this.teacher.consumeQueryCount(), duration, finalHypothesis);
    }

    public static Pair<Triplet<Integer, Integer, Integer>, Long> runCase(String path) {
        File exampleDirectory = new File(path);
        String[] taPaths = Arrays.stream(Objects.requireNonNull(exampleDirectory.listFiles()))
                .filter(file -> file.getName().endsWith(".json"))
                .filter(file -> file.getName().startsWith("P"))
                .map(File::getPath)
                .toArray(String[]::new);
        Arrays.sort(taPaths);
        MultiClocksTableMain exp = new MultiClocksTableMain(taPaths);
        return exp.runAndCountTimeWithStats();
    }

    public static String defaultCsvName() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")) + ".csv";
    }
}
