package tools.FormatOutput;

import tools.Tuple.Triplet;

public final class LearningConsole {
    private LearningConsole() {}
    public static final class QuietSpinner {
        private volatile boolean running;
        private Thread worker;

        public void start() {
            if (this.running) return;
            this.running = true;
            this.worker = new Thread(() -> {
                final String[] frames = new String[]{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
                int i = 0;
                while (this.running) {
                    System.err.print("\r" + ColoredFront.dealtString("[ LEARNING ] Running " + frames[i % frames.length], Color.Orange, Type.Common));
                    i++;
                    try {
                        Thread.sleep(90);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.err.print("\r" + ColoredFront.dealtString("[ LEARNING ] Finished", Color.Orange, Type.Common) + "\n");
                System.err.flush();
            }, "learning-quiet-spinner");
            this.worker.setDaemon(true);
            this.worker.start();
        }

        public void stop() {
            this.running = false;
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

    public static void showTurnHeader(int turn) {
        String title = String.format("[ TURN %04d ]", turn);
        String line = "=".repeat(18);
        System.out.println(ColoredFront.dealtString(line + " " + title + " " + line, Color.Cyan, Type.Common));
    }

    public static void showLearnerHeader(int learnerIndex, int total, String phase) {
        String title = String.format("[ LEARNER %02d/%02d ] %-14s", learnerIndex + 1, total, phase);
        System.out.println(ColoredFront.dealtString(title, Color.Purple, Type.Common));
    }

    public static void showStageHeader(String stage) {
        String title = String.format("[ STAGE ] %s", stage);
        System.out.println(ColoredFront.dealtString(title, Color.Green, Type.Common));
    }

    public static void printExperimentStats(
            String title,
            Triplet<Integer, Integer, Integer> queryStats,
            long timeMs
    ) {
        System.out.println(ColoredFront.dealtString("[ EXPERIMENT RESULT ] " + title, Color.Cyan, Type.Bold));
        System.out.printf("  %-14s : %d%n", "RQ_CNT", queryStats.p0());
        System.out.printf("  %-14s : %d%n", "MQ_CNT", queryStats.p1());
        System.out.printf("  %-14s : %d%n", "EQ_CNT", queryStats.p2());
        System.out.printf("  %-14s : %d%n", "TIME_MS", timeMs);
    }

    public static void printFullExperimentStats(
            int mqCnt,
            int mqStage1SuccessCnt,
            int mqStage2SuccessCnt,
            int mqStage3SuccessCnt,
            int eqCnt,
            int uncertainCtxCnt,
            int rollbackCnt,
            int hypNoSolution,
            long timeMs
    ) {
        String[] labels = new String[]{
                "MQ_CNT",
                "MQ_STAGE1_SUCCESS_CNT",
                "MQ_STAGE2_SUCCESS_CNT",
                "MQ_STAGE3_SUCCESS_CNT",
                "EQ_CNT",
                "UNCERTAIN_CTX_CNT",
                "ROLLBACK_CNT",
                "HYP_NO_SOLUTION",
                "TIME_MS"
        };
        int labelWidth = 0;
        for (String label : labels) {
            labelWidth = Math.max(labelWidth, label.length());
        }
        System.out.println(ColoredFront.dealtString("[ EXPERIMENT RESULT ]", Color.Cyan, Type.Bold));
        System.out.printf("  %-" + labelWidth + "s : %d%n", "MQ_CNT", mqCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "MQ_STAGE1_SUCCESS_CNT", mqStage1SuccessCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "MQ_STAGE2_SUCCESS_CNT", mqStage2SuccessCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "MQ_STAGE3_SUCCESS_CNT", mqStage3SuccessCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "EQ_CNT", eqCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "UNCERTAIN_CTX_CNT", uncertainCtxCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "ROLLBACK_CNT", rollbackCnt);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "HYP_NO_SOLUTION", hypNoSolution);
        System.out.printf("  %-" + labelWidth + "s : %d%n", "TIME_MS", timeMs);
    }

    public static void printPrettyJsonStats(
            int rqCnt,
            int mqCnt,
            int eqCnt,
            int rollbackCnt,
            int hypNoSolution,
            long timeMs
    ) {
        String json = String.format(
                "{%n" +
                "  \"RQ_CNT\": %d,%n" +
                "  \"MQ_CNT\": %d,%n" +
                "  \"EQ_CNT\": %d,%n" +
                "  \"ROLLBACK_CNT\": %d,%n" +
                "  \"Hyp_No_Solution\": %d,%n" +
                "  \"timeConsumed\": %d%n" +
                "}%n",
                rqCnt, mqCnt, eqCnt, rollbackCnt, hypNoSolution, timeMs
        );
        System.out.print(json);
    }
}
