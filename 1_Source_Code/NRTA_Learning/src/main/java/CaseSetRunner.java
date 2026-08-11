import learning.LearningSetting;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CaseSetRunner {
    private CaseSetRunner() {}

    public static Path runCaseSet(String caseSetDirPath, LearningSetting.LearningAlgorithm algorithm) {
        Path caseSetDir = Path.of(caseSetDirPath);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
        Path csvPath = caseSetDir.resolve(timestamp + ".csv");
        return runCaseSet(caseSetDir, csvPath, algorithm);
    }

    public static Path runCaseSet(String caseSetDirPath, String csvOutputPath, LearningSetting.LearningAlgorithm algorithm) {
        Path caseSetDir = Path.of(caseSetDirPath);
        Path csvPath = Path.of(csvOutputPath);
        return runCaseSet(caseSetDir, csvPath, algorithm);
    }

    private static Path runCaseSet(Path caseSetDir, Path csvPath, LearningSetting.LearningAlgorithm algorithm) {
        if (!Files.isDirectory(caseSetDir)) {
            throw new IllegalArgumentException("error: case set path is not a directory: " + caseSetDir);
        }
        List<Path> caseDirs;
        try (var stream = Files.list(caseSetDir)) {
            caseDirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("case"))
                    .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("error: failed to list case directories in " + caseSetDir, e);
        }
        if (caseDirs.isEmpty()) {
            throw new IllegalArgumentException("error: no subdirectories with prefix 'case' under: " + caseSetDir);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("case,algorithm,rq,mq,eq,timeMs,status\n");
        for (Path caseDir : caseDirs) {
            String caseName = caseDir.getFileName().toString();
            try {
                switch (algorithm) {
                    case CL -> {
                        CompositionLearningMain.ExperimentStats stats = CompositionLearningMain.runCase(caseDir.toString());
                        sb.append(caseName).append(",")
                                .append(algorithm).append(",")
                                .append("").append(",")
                                .append(stats.mqCnt()).append(",")
                                .append(stats.eqCnt()).append(",")
                                .append(stats.timeConsumed()).append(",")
                                .append("OK")
                                .append("\n");
                    }
                    case TOT -> {
                        Pair<Triplet<Integer, Integer, Integer>, Long> stats = MultiClocksTableMain.runCase(caseDir.toString());
                        sb.append(caseName).append(",")
                                .append(algorithm).append(",")
                                .append(stats.p0().p0()).append(",")
                                .append(stats.p0().p1()).append(",")
                                .append(stats.p0().p2()).append(",")
                                .append(stats.p1()).append(",")
                                .append("OK")
                                .append("\n");
                    }
                    case TCT -> {
                        Pair<Triplet<Integer, Integer, Integer>, Long> stats = MultiClocksTreeMain.runCase(caseDir.toString());
                        sb.append(caseName).append(",")
                                .append(algorithm).append(",")
                                .append(stats.p0().p0()).append(",")
                                .append(stats.p0().p1()).append(",")
                                .append(stats.p0().p2()).append(",")
                                .append(stats.p1()).append(",")
                                .append("OK")
                                .append("\n");
                    }
                }
            } catch (Exception e) {
                sb.append(caseName).append(",").append(algorithm).append(",,,,,")
                        .append("FAIL:")
                        .append(e.getClass().getSimpleName())
                        .append("\n");
            }
        }

        try {
            Files.writeString(csvPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("error: failed to write csv: " + csvPath, e);
        }
        return csvPath;
    }
}
