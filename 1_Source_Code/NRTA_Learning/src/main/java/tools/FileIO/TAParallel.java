package tools.FileIO;

import com.microsoft.z3.Context;
import ta.*;

import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

// Parallel composition of two accepting TAs
public class TAParallel {
    public static TA parallel(TA... TAs) {
        if (TAs.length == 0)
            return null;
        else if (TAs.length == 1) {
            return TAs[0];
        } else {
            TA outcome = TAs[0];
            for (int i = 1; i < TAs.length; i++) {
                outcome = TAParallel.parallel(outcome, TAs[i]);
            }
            return outcome;
        }
    }

    public static TA parallel(TA ta1, TA ta2) {
        assert TAParallel.checkAccept(ta1) && TAParallel.checkAccept(ta2);
        // id will be auto-generated
        // name
        TA outcome = new TA(ta1.getName() + "||" + ta2.getName());
        // Action set
        Set<Action> sigma = new HashSet<>();
        sigma.addAll(ta1.getAllActions());
        sigma.addAll(ta2.getAllActions());
        outcome.setSigma(sigma);
        // Clock set
        Map<String, Clock> X = new HashMap<>();
        X.putAll(ta1.getX());
        X.putAll(ta2.getX());
        outcome.setX(X);
        // Initial location
        String q0_name = TAParallel.locationParallelName(ta1.getQ0(), ta2.getQ0());
        Location q0 = new Location(q0_name, true, outcome);
        outcome.addLocation(q0);
        outcome.setQ0(q0);
        TAParallel.transitionGenerateCore(q0, ta1.getQ0(), ta2.getQ0(), ta1.getSigma(), ta2.getSigma(), outcome);

        outcome.getK().putAll(ta1.getK());
        outcome.getK().putAll(ta2.getK());
        return outcome;
    }

    // Add reached locations to the parallel TA
    private static void transitionGenerateCore(Location source,
                                               Location location1,
                                               Location location2,
                                               Map<String, Action> sigma1,
                                               Map<String, Action> sigma2,
                                               TA parallelTA) {
        Set<Action> sigma = parallelTA.getAllActions();
        Map<Action, List<Transition>> edges_out_grouped_1 = location1.getEdges_out_grouped();
        Map<Action, List<Transition>> edges_out_grouped_2 = location2.getEdges_out_grouped();
        for (Action action : sigma) {
            List<Transition> transList1 = edges_out_grouped_1.get(action);
            List<Transition> transList2 = edges_out_grouped_2.get(action);
            if (transList1 == null && transList2 == null) {
                continue;
            } else if (transList1 != null && transList2 == null) {
                if (!sigma2.containsKey(action.getName()))
                    TAParallel.dealSingleStep(source, action, transList1, location2, sigma1, sigma2, parallelTA, true);
            } else if (transList1 == null && transList2 != null) {
                if (!sigma1.containsKey(action.getName()))
                    TAParallel.dealSingleStep(source, action, transList2, location1, sigma1, sigma2, parallelTA, false);
            } else {
                TAParallel.dealBothStep(source, action, transList1, transList2, sigma1, sigma2, parallelTA);
            }
        }
    }

    private static void dealSingleStep(Location source,
                                       Action action,
                                       List<Transition> transList,
                                       Location otherLocation,
                                       Map<String, Action> sigma1,
                                       Map<String, Action> sigma2,
                                       TA parallelTA,
                                       boolean first) {
        for (Transition trans : transList) {
            Location nextLoc = trans.getTarget();
            String target_name;
            if (first)
                target_name = TAParallel.locationParallelName(nextLoc, otherLocation);
            else
                target_name = TAParallel.locationParallelName(otherLocation, nextLoc);
            Location target = parallelTA.getLocation(target_name);
            if (target == null) {
                target = new Location(target_name, true, parallelTA);
                parallelTA.addLocation(target);
                // Only need to explore new locations, existing ones have already been generated
//                TAParallel.transitionGenerateCore(target, nextLoc, otherLocation, sigma1, sigma2, parallelTA);
                if (first)
                    TAParallel.transitionGenerateCore(target, nextLoc, otherLocation, sigma1, sigma2, parallelTA);
                else
                    TAParallel.transitionGenerateCore(target, otherLocation, nextLoc, sigma1, sigma2, parallelTA);
            }
            Constraint guard = trans.getGuard();
            Set<Clock> reset = trans.getReset();
            Transition transition = new Transition(source, action, guard, reset, target);
            parallelTA.addTransition(transition);
        }
    }

    private static void dealBothStep(Location source,
                                     Action action,
                                     List<Transition> transList1,
                                     List<Transition> transList2,
                                     Map<String, Action> sigma1,
                                     Map<String, Action> sigma2,
                                     TA parallelTA) {
        for (Transition trans1 : transList1) {
            Location target1 = trans1.getTarget();
            Set<Clock> reset1 = trans1.getReset();
            Constraint guard1 = trans1.getGuard();
            for (Transition trans2 : transList2) {
                Location target2 = trans2.getTarget();
                Set<Clock> reset2 = trans2.getReset();
                Constraint guard2 = trans2.getGuard();

                String target_name = TAParallel.locationParallelName(target1, target2);
                Location target = parallelTA.getLocation(target_name);
                if (target == null) {
                    target = new Location(target_name, true, parallelTA);
                    parallelTA.addLocation(target);
                    // Only need to explore new locations, existing ones have already been generated
                    TAParallel.transitionGenerateCore(target, target1, target2, sigma1, sigma2, parallelTA);
                }

                Constraint guard = new Constraint();
                guard.addAll(guard1);
                guard.addAll(guard2);

                Set<Clock> reset = new HashSet<>();
                reset.addAll(reset1);
                reset.addAll(reset2);

                Transition transition = new Transition(source, action, guard, reset, target);
                parallelTA.addTransition(transition);
            }
        }
    }

    private static String locationParallelName(Location location1, Location location2) {
        String name = location1.getName() + "," + location2.getName();
        name = name.replaceAll("<", "").replaceAll(">", "");
        name = "<" + name + ">";
        return name;
    }

    private static boolean checkAccept(TA ta) {
        for (Map.Entry<String, Location> entry : ta.getQ().entrySet()) {
            Location q = entry.getValue();
            if (!q.is_accepting())
                return false;
        }
        return true;
    }

    public static void parallelEnd2End(String[] TA_paths, String output_path) {
        TA[] TAs = new TA[TA_paths.length];
        Context ctx = new Context();
        
        System.out.println(ColoredFront.dealtString("****************** Row TAs ******************", Color.Cyan, Type.Common));
        
        // Read all TAs from JSON files
        for (int i = 0; i < TA_paths.length; i++) {
            TAs[i] = TAUtil.buildTAFromJson(TA_paths[i], ctx);
            System.out.println(TAs[i].getMainDetail());
        }
        
        System.out.println(ColoredFront.dealtString("****************** Paralleled ******************", Color.Cyan, Type.Common));
        
        // Perform parallel composition
        TA paralleled = TAParallel.parallel(TAs);
        System.out.println(paralleled.getMainDetail());
        
        // Save result to JSON file
        TAUtil.buildJsonFromTA(output_path, paralleled);
        
        // Reset ID counter and generate old format JSON file
        Location.resetIDCounter();
        TA temp = TAUtil.buildTAFromJson(output_path, ctx);
        String output_path_old = output_path.replace(".json", "_old.json");
        TAUtil.buildOldJsonFromTA(output_path_old, temp);
    }

    public static void main(String[] args) {
        String case_group = "TA3_SIGMA5_LAMBDA20";
        String base_path = "../../cases/real_cases/" + case_group + "/";
        
        // Get all case folders under case_group directory
        java.io.File groupDir = new java.io.File(base_path);
        if (!groupDir.exists() || !groupDir.isDirectory()) {
            System.err.println("Directory does not exist: " + base_path);
            return;
        }
        
//        java.io.File[] caseDirs = groupDir.listFiles(file ->
//            file.isDirectory() && file.getName().startsWith("case")
//        );
//
//        if (caseDirs == null || caseDirs.length == 0) {
//            System.err.println("No case folders found");
//            return;
//        }
//        java.util.Arrays.sort(caseDirs);
        java.io.File[] caseDirs = new File[]{groupDir};
        
        // Process each case folder
        for (java.io.File caseDir : caseDirs) {
            String case_name = caseDir.getName();
            System.out.println(ColoredFront.dealtString("================ Processing " + case_name + " ================", Color.Green, Type.Common));
            
            // Get all P*.json files in this case folder
            java.io.File[] pFiles = caseDir.listFiles(file -> 
                file.isFile() && file.getName().startsWith("P") && file.getName().endsWith(".json")
            );
            
            if (pFiles == null || pFiles.length == 0) {
                System.out.println("No P*.json files found in " + case_name + ", skipping");
                continue;
            }
            
            // Sort P files alphabetically by filename
            Arrays.sort(pFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            
            // Build TA file path array
            String[] TA_paths = new String[pFiles.length];
            for (int i = 0; i < pFiles.length; i++) {
                TA_paths[i] = pFiles[i].getAbsolutePath();
            }
            
            // Output path
            String output_path = caseDir.toPath().resolve("parallel.json").toString();
            
            try {
                // Call parallelEnd2End function to perform parallel composition
                TAParallel.parallelEnd2End(TA_paths, output_path);
                System.out.println(ColoredFront.dealtString("Successfully processed " + case_name, Color.Green, Type.Common));
            } catch (Exception e) {
                System.err.println("Error processing " + case_name + ": " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println(); // Empty line separator
        }
        
        System.out.println(ColoredFront.dealtString("================ Batch processing completed ================", Color.Cyan, Type.Common));
    }
}
