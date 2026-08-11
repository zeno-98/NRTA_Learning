package learning.learners.composition_table;

import lombok.Getter;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.util.LinkedList;
import java.util.List;

@Getter
public enum TriBool {
    tri_true("+"),
    tri_false("-"),
    unknown("?");

    private final String description;

    TriBool(String description) {
        this.description = description;
    }

    public static TriBool fromBoolean(boolean b) {
        return b ? tri_true : tri_false;
    }

    @Override
    public String toString() {
        return this.description;
    }
    public List<Boolean> toBooleanList() {
        if (this == TriBool.tri_true) return List.of(true);
        if (this == TriBool.tri_false) return List.of(false);
        return List.of(true, false);
    }

    public Boolean toBoolean(){
        if (this == TriBool.tri_true) return true;
        if (this == TriBool.tri_false) return false;
        throw new RuntimeException("Try to convert unknown to boolean");
    }
    public static List<List<Boolean>> generateAllCombinations(List<TriBool> triBoolList) {
        List<List<Boolean>> result = new LinkedList<>();
        generateCombinationsHelper(triBoolList, 0, new LinkedList<>(), result);
        return result;
    }

    private static void generateCombinationsHelper(List<TriBool> triBoolList, int index, List<Boolean> current, List<List<Boolean>> result) {
        if (index == triBoolList.size()) {
            result.add(new LinkedList<>(current));
            return;
        }
        for (Boolean value : triBoolList.get(index).toBooleanList()) {
            current.add(value);
            generateCombinationsHelper(triBoolList, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    public static List<Boolean> replaceUnknown(List<TriBool> triBoolList, boolean value){
        return triBoolList.stream()
                .map(triBool -> triBool == TriBool.unknown ? value : triBool.toBoolean())
                .toList();
    }

    public boolean strictEqual(TriBool other){
        return this == other;
    }

    public boolean directionStrictEqual(TriBool s){
        if (LearningSetting.USE_STRICT_EQUAL) {
            if(this == TriBool.unknown)
                return true;
            if(this == TriBool.tri_true && s == TriBool.tri_true)
                return true;
            if(this == TriBool.tri_false && s == TriBool.tri_false)
                return true;
            return false;
        } else {
            return this.looseEqual(s);
        }
    }

    public boolean looseEqual(TriBool other){
        if(this == TriBool.tri_true && other == TriBool.tri_false)
            return false;
        if(this == TriBool.tri_false && other == TriBool.tri_true)
            return false;
        return true;
    }
}
