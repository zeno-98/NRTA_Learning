import learning.LearningSetting;
import ta.Action;
import ta.NTA;
import ta.TA;
import tools.ExtendSolver;
import tools.FileIO.TAParallel;
import tools.FileIO.TAUtil;
import tools.Tuple.Triplet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class SelectedCaseExperimentMain {
    private static final int MAX_COMPONENTS_FOR_PARALLEL_STATS = 8;

    private SelectedCaseExperimentMain() {}

    private record ModelStats(
            int componentCount,
            String componentLocations,
            String componentTransitions,
            int totalLocations,
            int totalTransitions,
            int parallelLocations,
            int parallelTransitions
    ) {}

    private static String[] caseFiles(String casePath) {
        File directory = new File(casePath);
        String[] paths = Arrays.stream(Objects.requireNonNull(directory.listFiles()))
                .filter(file -> file.getName().startsWith("P") && file.getName().endsWith(".json"))
                .map(File::getPath)
                .sorted()
                .toArray(String[]::new);
        if (paths.length == 0) {
            throw new IllegalArgumentException("no P*.json files under " + casePath);
        }
        return paths;
    }

    private static TA[] loadComponents(String casePath, ExtendSolver solver) {
        return Arrays.stream(caseFiles(casePath))
                .map(path -> TAUtil.buildTAFromJson(path, solver))
                .toArray(TA[]::new);
    }

    private static ModelStats collectStats(TA[] components) {
        if (components == null || components.length == 0) {
            return new ModelStats(0, "", "", 0, 0, 0, 0);
        }
        String locations = String.join(";",
                Arrays.stream(components).map(ta -> Integer.toString(ta.getQ().size())).toList());
        String transitions = String.join(";",
                Arrays.stream(components).map(ta -> Integer.toString(ta.getDelta().size())).toList());
        int totalLocations = Arrays.stream(components).mapToInt(ta -> ta.getQ().size()).sum();
        int totalTransitions = Arrays.stream(components).mapToInt(ta -> ta.getDelta().size()).sum();
        TA parallel = components.length <= MAX_COMPONENTS_FOR_PARALLEL_STATS
                ? TAParallel.parallel(components)
                : null;
        return new ModelStats(
                components.length,
                locations,
                transitions,
                totalLocations,
                totalTransitions,
                parallel == null ? -1 : parallel.getQ().size(),
                parallel == null ? -1 : parallel.getDelta().size()
        );
    }

    private static ModelStats[] targetStats(String casePath) {
        Action.GLOBAL_ACTION_MAP.clear();
        ExtendSolver solver = new ExtendSolver();
        TA[] components = loadComponents(casePath, solver);
        ModelStats raw = collectStats(components);
        for (TA component : components) {
            component.makeComplete(solver);
        }
        TA completedParallel = components.length <= MAX_COMPONENTS_FOR_PARALLEL_STATS
                ? TAParallel.parallel(components)
                : null;
        if (completedParallel != null) {
            completedParallel.makeComplete(solver);
        }
        ModelStats completed = new ModelStats(
                components.length,
                String.join(";",
                        Arrays.stream(components).map(ta -> Integer.toString(ta.getQ().size())).toList()),
                String.join(";",
                        Arrays.stream(components).map(ta -> Integer.toString(ta.getDelta().size())).toList()),
                Arrays.stream(components).mapToInt(ta -> ta.getQ().size()).sum(),
                Arrays.stream(components).mapToInt(ta -> ta.getDelta().size()).sum(),
                completedParallel == null ? -1 : completedParallel.getQ().size(),
                completedParallel == null ? -1 : completedParallel.getDelta().size()
        );
        return new ModelStats[]{raw, completed};
    }

    private static TA[] componentsOf(NTA nta) {
        if (nta == null) return null;
        List<TA> components = new ArrayList<>();
        Iterator<TA> iterator = nta.iterator();
        while (iterator.hasNext()) {
            components.add(iterator.next());
        }
        return components.toArray(TA[]::new);
    }

    private static String cleanError(Throwable throwable) {
        if (throwable == null) return "";
        String message = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        return message.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    private static void appendModelStats(StringBuilder row, ModelStats stats) {
        row.append(',').append(stats.componentCount())
                .append(',').append(stats.componentLocations())
                .append(',').append(stats.componentTransitions())
                .append(',').append(stats.totalLocations())
                .append(',').append(stats.totalTransitions())
                .append(',').append(stats.parallelLocations())
                .append(',').append(stats.parallelTransitions());
    }

    public static void main(String[] args) {
        if (args.length != 4 && args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: <group-label> <case-name> <case-path> <CL|TOT|TCT> [timeout-seconds]");
        }
        String group = args[0];
        String caseName = args[1];
        String casePath = args[2];
        LearningSetting.LearningAlgorithm algorithm =
                LearningSetting.LearningAlgorithm.valueOf(args[3]);

        LearningSetting.LEARNING_ALGORITHM = algorithm;
        LearningSetting.LEARNING_LOG_LEVEL = LearningSetting.LearningLogLevel.QUIET;
        LearningSetting.RUN_CASE_TIMEOUT_SECONDS = args.length == 5
                ? Integer.parseInt(args[4])
                : 300;
        LearningSetting.SAVE_HYPOTHESIS = false;
        LearningSetting.USE_STRICT_EQUAL = false;

        String method = switch (algorithm) {
            case CL -> "comp";
            case TOT -> "mct";
            case TCT -> "ttt";
            default -> throw new IllegalArgumentException("unsupported algorithm " + algorithm);
        };

        String status = "OK";
        String error = "";
        String timeMs = "";
        String rq = "";
        String mq = "";
        String eq = "";
        String stage1 = "";
        String stage2 = "";
        String stage3 = "";
        String uncertain = "";
        String rollback = "";
        String noSolution = "";
        TA[] learned = null;

        try {
            switch (algorithm) {
                case CL -> {
                    CompositionLearningMain.ExperimentStats stats =
                            CompositionLearningMain.runCase(casePath);
                    timeMs = Long.toString(stats.timeConsumed());
                    rq = Integer.toString(stats.rqCnt());
                    mq = Integer.toString(stats.mqCnt());
                    eq = Integer.toString(stats.eqCnt());
                    stage1 = Integer.toString(stats.mqStage1SuccessCnt());
                    stage2 = Integer.toString(stats.mqStage2SuccessCnt());
                    stage3 = Integer.toString(stats.mqStage3SuccessCnt());
                    uncertain = Integer.toString(stats.uncertainCtxCnt());
                    rollback = Integer.toString(stats.rollbackCnt());
                    noSolution = Integer.toString(stats.hypNoSolution());
                    learned = stats.finalHypotheses();
                }
                case TOT -> {
                    MultiClocksTableMain runner = new MultiClocksTableMain(caseFiles(casePath));
                    MultiClocksTableMain.RunResult stats = runner.runAndCollectStats();
                    Triplet<Integer, Integer, Integer> queries = stats.queryCount();
                    rq = Integer.toString(queries.p0());
                    mq = Integer.toString(queries.p1());
                    eq = Integer.toString(queries.p2());
                    if (stats.timeMs() == -1) {
                        status = "TIMEOUT";
                        error = "internal timeout";
                    } else if (stats.timeMs() < 0) {
                        status = "FAIL";
                        error = "internal learner failure";
                    } else {
                        timeMs = Long.toString(stats.timeMs());
                        learned = componentsOf(stats.finalHypothesis());
                    }
                }
                case TCT -> {
                    MultiClocksTreeMain runner = new MultiClocksTreeMain(caseFiles(casePath));
                    MultiClocksTreeMain.RunResult stats = runner.runAndCollectStats();
                    Triplet<Integer, Integer, Integer> queries = stats.queryCount();
                    rq = Integer.toString(queries.p0());
                    mq = Integer.toString(queries.p1());
                    eq = Integer.toString(queries.p2());
                    if (stats.timeMs() == -1) {
                        status = "TIMEOUT";
                        error = "internal timeout";
                    } else if (stats.timeMs() < 0) {
                        status = "FAIL";
                        error = "internal learner failure";
                    } else {
                        timeMs = Long.toString(stats.timeMs());
                        learned = componentsOf(stats.finalHypothesis());
                    }
                }
                default -> throw new IllegalArgumentException("unsupported algorithm " + algorithm);
            }
        } catch (CompositionLearningMain.ExperimentFailure failure) {
            CompositionLearningMain.ExperimentStats stats = failure.stats();
            timeMs = Long.toString(stats.timeConsumed());
            rq = Integer.toString(stats.rqCnt());
            mq = Integer.toString(stats.mqCnt());
            eq = Integer.toString(stats.eqCnt());
            stage1 = Integer.toString(stats.mqStage1SuccessCnt());
            stage2 = Integer.toString(stats.mqStage2SuccessCnt());
            stage3 = Integer.toString(stats.mqStage3SuccessCnt());
            uncertain = Integer.toString(stats.uncertainCtxCnt());
            rollback = Integer.toString(stats.rollbackCnt());
            noSolution = Integer.toString(stats.hypNoSolution());
            learned = stats.finalHypotheses();
            error = cleanError(failure);
            String lowerError = error.toLowerCase();
            status = lowerError.contains("timeout")
                    ? "TIMEOUT"
                    : lowerError.contains("oom") || lowerError.contains("outofmemory")
                            ? "OOM"
                            : "FAIL";
        } catch (Throwable throwable) {
            error = cleanError(throwable);
            String lowerError = error.toLowerCase();
            status = lowerError.contains("timeout")
                    ? "TIMEOUT"
                    : lowerError.contains("oom") || lowerError.contains("outofmemory")
                            ? "OOM"
                            : "FAIL";
        }

        ModelStats[] target;
        try {
            target = targetStats(casePath);
        } catch (Throwable statsFailure) {
            target = new ModelStats[]{
                    new ModelStats(0, "", "", 0, 0, 0, 0),
                    new ModelStats(0, "", "", 0, 0, 0, 0)
            };
            String statsError = "target statistics unavailable: " + cleanError(statsFailure);
            error = error.isEmpty() ? statsError : error + "; " + statsError;
        }
        ModelStats learnedStats = collectStats(learned);
        StringBuilder row = new StringBuilder();
        row.append(group).append(',').append(caseName).append(',').append(method)
                .append(',').append(status).append(',').append(error)
                .append(',').append(timeMs).append(',').append(rq).append(',').append(mq)
                .append(',').append(eq).append(',').append(stage1).append(',').append(stage2)
                .append(',').append(stage3).append(',').append(uncertain)
                .append(',').append(rollback).append(',').append(noSolution);
        appendModelStats(row, target[0]);
        appendModelStats(row, target[1]);
        appendModelStats(row, learnedStats);
        System.out.println("CSV_ROW:" + row);
    }
}
