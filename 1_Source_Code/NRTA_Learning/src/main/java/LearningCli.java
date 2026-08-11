import learning.LearningSetting;
import learning.teachers.Teacher;
import learning.teachers.TeacherSetting;
import tools.FormatOutput.LearningConsole;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;

import java.nio.file.Path;

public final class LearningCli {
    private static String DEFAULT_CASE_PATH = "../Illustrative_Example";

    private LearningCli() {}

    public static void run(String[] args) {
        String casePath = null;
        String caseSetPath = null;
        String csvOutputPath = null;
        LearningSetting.LearningLogLevel logOverride = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help" -> {
                    printHelp();
                    return;
                }
                case "--case" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --case");
                    casePath = args[++i];
                }
                case "--caseset" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --caseset");
                    caseSetPath = args[++i];
                }
                case "--csv" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --csv");
                    csvOutputPath = args[++i];
                }
                case "--log" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --log");
                    logOverride = parseLogLevel(args[++i]);
                }
                case "--algo" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --algo");
                    LearningSetting.LEARNING_ALGORITHM = parseAlgorithm(args[++i]);
                }
                case "--timeout" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --timeout");
                    LearningSetting.RUN_CASE_TIMEOUT_SECONDS = Integer.parseInt(args[++i]);
                }
                case "--save-hyp" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --save-hyp");
                    LearningSetting.SAVE_HYPOTHESIS = Boolean.parseBoolean(args[++i]);
                }
                case "--strict-eq" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for --strict-eq");
                    LearningSetting.USE_STRICT_EQUAL = Boolean.parseBoolean(args[++i]);
                }
                default -> throw new IllegalArgumentException("unknown argument: " + arg + " (use --help)");
            }
        }

        if (casePath != null && caseSetPath != null) {
            throw new IllegalArgumentException("--case and --caseset are mutually exclusive");
        }
        if (casePath == null && caseSetPath == null) {
            casePath = DEFAULT_CASE_PATH;
        }
        if (csvOutputPath != null && caseSetPath == null) {
            throw new IllegalArgumentException("--csv is only valid with --caseset");
        }

        // TOT has a dedicated presentation-style verbose output.
        if (LearningSetting.LEARNING_ALGORITHM == LearningSetting.LearningAlgorithm.TOT
                && LearningSetting.LEARNING_LOG_LEVEL == LearningSetting.LearningLogLevel.VERBOSE) {
            LearningSetting.LEARNING_LOG_LEVEL = LearningSetting.LearningLogLevel.PRESENTATION;
        }

        if (caseSetPath != null) {
            LearningSetting.LEARNING_LOG_LEVEL = LearningSetting.LearningLogLevel.QUIET;
            if (logOverride != null) {
                LearningSetting.LEARNING_LOG_LEVEL = logOverride;
            }
            if (LearningSetting.LEARNING_ALGORITHM == LearningSetting.LearningAlgorithm.TOT
                    && LearningSetting.LEARNING_LOG_LEVEL == LearningSetting.LearningLogLevel.VERBOSE) {
                LearningSetting.LEARNING_LOG_LEVEL = LearningSetting.LearningLogLevel.PRESENTATION;
            }
            Path csv = csvOutputPath == null
                    ? CaseSetRunner.runCaseSet(caseSetPath, LearningSetting.LEARNING_ALGORITHM)
                    : CaseSetRunner.runCaseSet(caseSetPath, csvOutputPath, LearningSetting.LEARNING_ALGORITHM);
            System.out.println("CSV_SAVED: " + csv);
            return;
        }

        if (logOverride != null) {
            LearningSetting.LEARNING_LOG_LEVEL = logOverride;
            if (LearningSetting.LEARNING_ALGORITHM == LearningSetting.LearningAlgorithm.TOT
                    && LearningSetting.LEARNING_LOG_LEVEL == LearningSetting.LearningLogLevel.VERBOSE) {
                LearningSetting.LEARNING_LOG_LEVEL = LearningSetting.LearningLogLevel.PRESENTATION;
            }
        }

        switch (LearningSetting.LEARNING_ALGORITHM) {
            case CL -> {
                CompositionLearningMain.ExperimentStats stats = CompositionLearningMain.runCase(casePath);
                LearningConsole.printFullExperimentStats(
                        stats.mqCnt(),
                        stats.mqStage1SuccessCnt(),
                        stats.mqStage2SuccessCnt(),
                        stats.mqStage3SuccessCnt(),
                        stats.eqCnt(),
                        stats.uncertainCtxCnt(),
                        stats.rollbackCnt(),
                        stats.hypNoSolution(),
                        stats.timeConsumed()
                );
            }
            case TOT -> {
                Pair<Triplet<Integer, Integer, Integer>, Long> stats = MultiClocksTableMain.runCase(casePath);
                LearningConsole.printExperimentStats("MULTI_CLOCK_TABLE", stats.p0(), stats.p1());
            }
            case TCT -> {
                Pair<Triplet<Integer, Integer, Integer>, Long> stats = MultiClocksTreeMain.runCase(casePath);
                LearningConsole.printExperimentStats("TTT", stats.p0(), stats.p1());
            }
        }
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  --case <path>                       Run a single case (file or case directory). default: " + DEFAULT_CASE_PATH);
        System.out.println("  --caseset <dir>                     Run all immediate subdirs named case*. default: off");
        System.out.println("  --algo <CL|TOT|TCT>                 Learning algorithm. default: CL");
        System.out.println("  --csv <output.csv>                  CSV output path for caseset. default: <caseset>/yyyy_MM_dd_HH_mm_ss.csv");
        System.out.println("  --log <quiet|normal|verbose>");
        System.out.println("                                      Override learning log level. default: case=verbose, caseset=quiet");
        System.out.println("  --timeout <seconds>                 Per-case timeout; <=0 means no timeout. default: 300");
        System.out.println("  --save-hyp <true|false>             SAVE_HYPOTHESIS. default: false");
        System.out.println("  --strict-eq <true|false>            USE_STRICT_EQUAL. default: false");
        System.out.println("  --help                              Show this help. default: off");
        System.out.println("Defaults:");
        System.out.println("  no args: run default single case with built-in settings");
    }

    private static LearningSetting.LearningLogLevel parseLogLevel(String raw) {
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "quiet" -> LearningSetting.LearningLogLevel.QUIET;
            case "normal" -> LearningSetting.LearningLogLevel.NORMAL;
            case "verbose" -> LearningSetting.LearningLogLevel.VERBOSE;
            case "presentation" -> LearningSetting.LearningLogLevel.PRESENTATION;
            default -> throw new IllegalArgumentException("invalid --log value: " + raw);
        };
    }

    private static LearningSetting.LearningAlgorithm parseAlgorithm(String raw) {
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "CL" -> LearningSetting.LearningAlgorithm.CL;
            case "TOT" -> LearningSetting.LearningAlgorithm.TOT;
            case "TCT" -> LearningSetting.LearningAlgorithm.TCT;
            default -> throw new IllegalArgumentException("invalid --algo value: " + raw);
        };
    }

    public static void main(String[] args) {
        LearningSetting.LEARNING_ALGORITHM = LearningSetting.LearningAlgorithm.CL;
        LearningSetting.LEARNING_LOG_LEVEL = LearningSetting.LearningLogLevel.VERBOSE;
        LearningSetting.SAVE_HYPOTHESIS = false;
        LearningSetting.RUN_CASE_TIMEOUT_SECONDS = 300;
        LearningSetting.CONSOLE_COLOR_THEME = LearningSetting.ConsoleColorTheme.DARK;

        LearningCli.DEFAULT_CASE_PATH = "../Illustrative_Example";
        LearningCli.run(args);
    }
}
