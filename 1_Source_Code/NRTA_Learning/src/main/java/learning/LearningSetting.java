package learning;

public final class LearningSetting {
    private LearningSetting() {}

    public enum LearningAlgorithm {
        CL,
        TOT,
        TCT
    }

    public enum LearningLogLevel {
        QUIET,
        NORMAL,
        VERBOSE,
        PRESENTATION
    }

    public enum ConsoleColorTheme {
        DARK,
        LIGHT
    }

    public static LearningAlgorithm LEARNING_ALGORITHM = LearningAlgorithm.CL;
    public static LearningLogLevel LEARNING_LOG_LEVEL = LearningLogLevel.VERBOSE;
    public static ConsoleColorTheme CONSOLE_COLOR_THEME = ConsoleColorTheme.DARK;
    public static int RUN_CASE_TIMEOUT_SECONDS = 300;
    public static boolean SAVE_HYPOTHESIS = false;
    public static boolean SHOW_SINK_TRANSITIONS = false;

    // only used for compositional learning
    public static boolean USE_STRICT_EQUAL = false;
    public static int PREFIX_EXPANSION_PRUNE_LEVEL = 1;

    public static boolean isLearningQuiet() {
        return LEARNING_LOG_LEVEL == LearningLogLevel.QUIET;
    }

    public static boolean isLearningNormalOrVerbose() {
        return LEARNING_LOG_LEVEL == LearningLogLevel.NORMAL
                || LEARNING_LOG_LEVEL == LearningLogLevel.VERBOSE
                || LEARNING_LOG_LEVEL == LearningLogLevel.PRESENTATION;
    }

    public static boolean isLearningVerbose() {
        return LEARNING_LOG_LEVEL == LearningLogLevel.VERBOSE
                || LEARNING_LOG_LEVEL == LearningLogLevel.PRESENTATION;
    }

    public static boolean isLearningPresentation() {
        return LEARNING_LOG_LEVEL == LearningLogLevel.PRESENTATION;
    }
}
