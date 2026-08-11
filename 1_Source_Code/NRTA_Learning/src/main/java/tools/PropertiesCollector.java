package tools;

import ta.TA;
import tools.FileIO.TAParallel;
import tools.FileIO.TAUtil;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class PropertiesCollector {

    private static final Pattern CONFIG_PATTERN = Pattern.compile("\\d_TA\\d_ACT\\d_LOC\\d_TRANS\\d+_U\\d+.*$");

    static class CaseStats {
        double avgLocations;
        double avgTransitions;
        int totalLocations;
        int totalTransitions;

        CaseStats(double avgLoc, double avgTrans, int totalLoc, int totalTrans) {
            this.avgLocations = avgLoc;
            this.avgTransitions = avgTrans;
            this.totalLocations = totalLoc;
            this.totalTransitions = totalTrans;
        }

        @Override
        public String toString() {
            return String.format("(%s,  %s,  %s,  %s)", avgLocations, avgTransitions, totalLocations, totalTransitions);
        }
    }

    private static final HashMap<String, CaseStats> caseStatsMap = new HashMap<>();

    public static class ConfigStats {
        double avgLocations;
        double avgTransitions;
        double avgTotalLocations;
        double avgTotalTransitions;

        ConfigStats(double avgLoc, double avgTrans, double avgTotalLoc, double avgTotalTrans) {
            this.avgLocations = avgLoc;
            this.avgTransitions = avgTrans;
            this.avgTotalLocations = avgTotalLoc;
            this.avgTotalTransitions = avgTotalTrans;
        }

        @Override
        public String toString() {
            return String.format("(%s,%s,%s,%s)", avgLocations, avgTransitions, avgTotalLocations, avgTotalTransitions);
        }
    }

    private static CaseStats processCaseFolder(File caseFolder, ExtendSolver slv) {
        File[] jsonFiles = caseFolder.listFiles(f -> f.isFile() && f.getName().startsWith("P") && f.getName().endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            throw new RuntimeException("No P*.json files found in " + caseFolder.getPath());
        }
        Arrays.sort(jsonFiles, Comparator.comparing(File::getName));

        TA[] tas = new TA[jsonFiles.length];
        int totalLocations = 0;
        int totalTransitions = 0;

        for (int i = 0; i < jsonFiles.length; i++) {
            tas[i] = TAUtil.buildTAFromJson(jsonFiles[i].getPath(), slv);
            tas[i].makeComplete(slv);
            totalLocations += tas[i].getQ().size();
            totalTransitions += tas[i].getDelta().size();
        }

        double avgLocations = (double) totalLocations / jsonFiles.length;
        double avgTransitions = (double) totalTransitions / jsonFiles.length;

        TA parallelTA = TAParallel.parallel(tas);
        parallelTA.makeComplete(slv);
        int parallelLocations = parallelTA.getQ().size();
        int parallelTransitions = parallelTA.getDelta().size();
        CaseStats caseStats = new CaseStats(avgLocations, avgTransitions, parallelLocations, parallelTransitions);

        return caseStats;
    }

    private static ConfigStats processConfigFolder(File configFolder, ExtendSolver slv) {
        File[] caseFolders = configFolder.listFiles(f -> f.isDirectory());
        if (caseFolders == null || caseFolders.length == 0) {
            throw new RuntimeException("No case* folders found in " + configFolder.getPath());
        }

        Arrays.sort(caseFolders, Comparator.comparing(File::getName));

        double sumAvgLocations = 0;
        double sumAvgTransitions = 0;
        double sumTotalLocations = 0;
        double sumTotalTransitions = 0;

        for (File caseFolder : caseFolders) {
            CaseStats stats = processCaseFolder(caseFolder, slv);
            sumAvgLocations += stats.avgLocations;
            sumAvgTransitions += stats.avgTransitions;
            sumTotalLocations += stats.totalLocations;
            sumTotalTransitions += stats.totalTransitions;
        }

        int caseCount = caseFolders.length;
        return new ConfigStats(
                sumAvgLocations / caseCount,
                sumAvgTransitions / caseCount,
                sumTotalLocations / caseCount,
                sumTotalTransitions / caseCount
        );
    }

    public static void collectRandomCases() {
        String CASES_PATH = "../../cases/random_cases";
        ExtendSolver slv = new ExtendSolver();
        File casesFmDir = new File(CASES_PATH);

        if (!casesFmDir.exists() || !casesFmDir.isDirectory()) {
            throw new RuntimeException("cases directory not found: " + CASES_PATH);
        }

         File[] configFolders = casesFmDir.listFiles(f -> f.isDirectory() && CONFIG_PATTERN.matcher(f.getName()).matches());
        if (configFolders == null || configFolders.length == 0) {
            throw new RuntimeException("No config folders matching pattern found in " + CASES_PATH);
        }

        Arrays.sort(configFolders, Comparator.comparing(File::getName));

        System.out.printf("%-25s   %18s   %15s   %15s   %15s   %18s   %18s%n",
                "Config", "AvgLoc(|Q|_i)", "AvgTrans(|delta_i|)", "|delta_i|/|Q|_i", "TotalLoc(|Q|)", "TotalTrans(|delta|)", "|delta|/|Q|");
        System.out.println("=".repeat(200));

        for (File configFolder : configFolders) {
            String configName = configFolder.getName();
            ConfigStats stats = processConfigFolder(configFolder, slv);

            double avgRatio = stats.avgTransitions / stats.avgLocations;
            double totalRatio = stats.avgTotalTransitions / stats.avgTotalLocations;

            System.out.printf("%-25s   %18.2f   %15.2f   %15.2f   %15.2f   %18.2f   %18.2f%n",
                    configName,
                    stats.avgLocations,
                    stats.avgTransitions,
                    avgRatio,
                    stats.avgTotalLocations,
                    stats.avgTotalTransitions,
                    totalRatio);
        }
    }

    public static void collectRealCases() {
        String CASES_PATH = "../../cases/real_cases";
        ExtendSolver slv = new ExtendSolver();
        File casesFmDir = new File(CASES_PATH);

        if (!casesFmDir.exists() || !casesFmDir.isDirectory()) {
            throw new RuntimeException("cases directory not found: " + CASES_PATH);
        }
        File[] configFolders = casesFmDir.listFiles(f -> f.isDirectory());

        System.out.printf("%-40s   %18s   %15s   %15s   %15s   %18s   %18s%n",
                "Config", "AvgLoc(|Q|_i)", "AvgTrans(|delta_i|)", "|delta_i|/|Q|_i", "TotalLoc(|Q|)", "TotalTrans(|delta|)", "|delta|/|Q|");
        System.out.println("=".repeat(200));

        Arrays.sort(Objects.requireNonNull(configFolders));
        for (File configFolder : configFolders) {
            String configName = configFolder.getName();
            File[] caseFolders = configFolder.listFiles(f -> f.isDirectory() && !f.getName().startsWith("unbalanced"));

            Arrays.sort(Objects.requireNonNull(caseFolders));
            for (File caseFolder : caseFolders) {

                CaseStats stats = processCaseFolder(caseFolder, slv);
                double avgRatio = stats.avgTransitions / stats.avgLocations;
                double totalRatio = (double) stats.totalTransitions / stats.totalLocations;
                System.out.printf("%-40s   %18.2f   %15.2f   %15.2f   %15d   %18d   %18.2f%n",
                        configFolder.getName() + '/' + caseFolder.getName(),
                        stats.avgLocations,
                        stats.avgTransitions,
                        avgRatio,
                        stats.totalLocations, // after composition
                        stats.totalTransitions, // after composition
                        totalRatio);
            }
        }
    }

    public static void main(String[] args) {
        collectRandomCases();
        collectRealCases();
    }
}
