package tools.FileIO;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import exception.ModelFileExceptions;
import language.actions.ResetLogicalTimedAction;
import language.words.ResetLogicalTimedWord;
import run.ClockValuation;
import ta.*;
import tools.FormatOutput.JsonFormat;
import tools.Tuple.Pair;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class TAUtil {
    public static TA buildTAFromJson(String file_path, Context ctx){
        TA outcome = null;
        try {
            outcome = buildTAFromJsonCore(file_path, ctx);
        } catch (IOException e) {
            throw new RuntimeException(ModelFileExceptions.FILE_NOT_EXIST);
        }
        return outcome;
    }

    public static TA buildTAFromJsonObject(JSONObject json_TA, Context ctx) {
        // Build TA
        String TA_name = json_TA.getString("name");
        TA ta;
        if (json_TA.getJSONArray("clocks").size() == 1) {
            ta = new RTA(TA_name);
        } else {
            ta = new TA(TA_name);
        }

        // Build clocks
        JSONArray json_clock_names = json_TA.getJSONArray("clocks");
        TAUtil.buildClocks(json_clock_names, ta, ctx);

        // Build actions
        JSONArray json_action_names = json_TA.getJSONArray("actions");
        TAUtil.buildActions(json_action_names, ta);

        // Build locations
        JSONArray json_locations = json_TA.getJSONArray("locations");
        TAUtil.buildLocations(json_locations, ta);

        // Build transitions
        JSONArray json_transitions = sortTransitionsForLoad(json_TA.getJSONArray("transitions"));
        TAUtil.buildTransitions(json_transitions, ta);

        // Set initial location
        String init_loc_name = json_TA.getString("init_location");
        ta.setQ0(ta.getLocation(init_loc_name));

        return ta;
    }

    protected static void buildClocks(JSONArray json_clock_names, TA ta, Context ctx){
        for(int i=0;i<json_clock_names.size();i++){
            String clock_name = json_clock_names.getString(i);
            RealExpr smt_exp = ctx.mkRealConst(ta.getName() + '.' + clock_name);
            Clock clock = new Clock(clock_name, ta.getName(), smt_exp);
            ta.addClock(clock);
            ta.getK().put(clock, Value.ZERO);
        }
    }

    protected static void buildActions(JSONArray json_action_names, TA ta){
        for(int i=0;i<json_action_names.size();i++){
            String action_name = json_action_names.getString(i);
            Action action = Action.buildAction(action_name);
            ta.addAction(action);
        }
        Action blank_action = Action.buildEpsilonAction(ta.getName() + "_anonymous");
        ta.setBlankAction(blank_action);
    }

    protected static void buildLocations(JSONArray json_locations, TA ta){
        for(int i=0;i<json_locations.size();i++){
            JSONObject json_location = json_locations.getJSONObject(i);
            String location_name = json_location.getString("name");
            boolean if_accepting = json_location.getBoolean("accepting");
            JSONObject invariant_expr = json_location.getJSONObject("invariant");
            Constraint invariant = expr2Constraint(ta, invariant_expr);
            Location location = new Location(location_name, if_accepting, invariant, ta);
            ta.addLocation(location);
        }
    }

    protected static void buildTransitions(JSONArray json_transitions, TA ta){
        Map<String,Action> actual_sigma = new HashMap<>();
        for(int i=0;i<json_transitions.size();i++){
            JSONObject json_transition = json_transitions.getJSONObject(i);
            Location source = ta.getLocation(json_transition.getString("source"));
            Action action = ta.getAction(json_transition.getString("action"));
            if(action==null) action = ta.getBlank_action();
            Constraint guard = expr2Constraint(ta, json_transition.getJSONObject("guard"));
            Set<Clock> reset = array2Reset(ta, json_transition.getJSONArray("reset"));
            Location target = ta.getLocation(json_transition.getString("target"));
            Transition transition = new Transition(source,action,guard,reset,target);
            ta.addTransition(transition);
            actual_sigma.put(action.getName(),action);
        }
        // Set actual alphabet
//        ta.setSigma(actual_sigma);
        if (actual_sigma.containsKey(ta.getBlank_action().getName())) {
            throw new RuntimeException("should not contain blank action");
        }
        ta.removeAction(ta.getBlank_action());
    }

    private static JSONArray sortTransitionsForLoad(JSONArray jsonTransitions) {
        List<JSONObject> transitions = new ArrayList<>();
        for (int i = 0; i < jsonTransitions.size(); i++) {
            transitions.add(jsonTransitions.getJSONObject(i));
        }
        transitions.sort(
                Comparator.comparing((JSONObject t) -> nullToEmpty(t.getString("source")))
                        .thenComparing(t -> nullToEmpty(t.getString("action")))
                        .thenComparing(t -> guardLowerForSort(t.getJSONObject("guard")))
                        .thenComparing(t -> guardCanonicalForSort(t.getJSONObject("guard")))
                        .thenComparing(t -> nullToEmpty(t.getString("target")))
        );
        JSONArray sorted = new JSONArray();
        sorted.addAll(transitions);
        return sorted;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Value guardLowerForSort(JSONObject guardExpr) {
        if (guardExpr == null || guardExpr.isEmpty()) {
            return Value.MINUS_INFINITE;
        }
        Value lower = Value.INFINITE;
        for (Map.Entry<String, Object> entry : guardExpr.entrySet()) {
            Object valueObj = entry.getValue();
            if (!(valueObj instanceof String)) {
                continue;
            }
            String interval = (String) valueObj;
            String[] intervalParts = interval.split(",");
            if (intervalParts.length < 2) {
                continue;
            }
            String lowerExpr = intervalParts[0];
            Value currentLower;
            if (lowerExpr.charAt(lowerExpr.length() - 1) == '-') {
                currentLower = Value.MINUS_INFINITE;
            } else {
                currentLower = new Value(Double.parseDouble(lowerExpr.substring(1)));
            }
            if (Value.lt(currentLower, lower)) {
                lower = currentLower;
            }
        }
        if (lower == Value.INFINITE) {
            return Value.MINUS_INFINITE;
        }
        return lower;
    }

    private static String guardCanonicalForSort(JSONObject guardExpr) {
        if (guardExpr == null || guardExpr.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : guardExpr.entrySet()) {
            parts.add(entry.getKey() + ":" + entry.getValue());
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    protected static TA buildTAFromJsonCore(String file_path, Context ctx) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file_path)));
        String str;
        StringBuilder json = new StringBuilder();
        while ((str = reader.readLine()) != null) {
            json.append(str);
        }
        JSONObject json_TA = JSON.parseObject(json.toString());
        return buildTAFromJsonObject(json_TA, ctx);
    }

    public static Set<Clock> array2Reset(TA ta, JSONArray array){
        Set<Clock> outcome = new HashSet<>();
        for(int i=0;i<array.size();i++){
            Clock clock = ta.getClock(array.getString(i));
            outcome.add(clock);
        }
        return outcome;
    }

    public static Constraint expr2Constraint(TA ta, JSONObject expr){
        Constraint outcome = new Constraint();
        for(Map.Entry<String,Object> entry : expr.entrySet()) {
            String left = entry.getKey();
            String[] clock_names = left.split("-");
            Clock clock1;
            Clock clock2;
            if(clock_names.length == 1){
                clock1 = ta.getClock(clock_names[0]);
                clock2 = Clock.getZeroClock();
            } else if (clock_names.length == 2){
                clock1 = ta.getClock(clock_names[0]);
                clock2 = ta.getClock(clock_names[1]);
            } else
                throw new RuntimeException(ModelFileExceptions.INVALID_CONSTRAINT_EXPR);

            String interval = (String) entry.getValue();
            String[] interval_parts = interval.split(",");

            String lower_expr = interval_parts[0];
            boolean lower_closed = false;
            Value lower_value;
            if(lower_expr.charAt(lower_expr.length()-1)=='-'){
                lower_value = Value.MINUS_INFINITE;
            } else {
                if(lower_expr.charAt(0) == '[')
                    lower_closed = true;
                lower_value = parseFiniteValue(lower_expr.substring(1));
            }

            boolean lower_bound_needed = true;
            if(clock2.isZeroClock()){
                if(lower_value.isMinus_infinite())
                    lower_bound_needed = false;
                else if(Value.lt(lower_value, Value.ZERO))
                    lower_bound_needed = false;
                else if(Value.eq(lower_value, Value.ZERO) && lower_closed)
                    lower_bound_needed = false;
            } else {
                if(lower_value.isMinus_infinite())
                    lower_bound_needed = false;
            }

            if(lower_bound_needed) {
                AtomConstraint lower_atomConstraint = new AtomConstraint(clock2, clock1, lower_closed, Value.negative(lower_value));
                outcome.addAtomConstraint(lower_atomConstraint);
            }

            String upper_expr = interval_parts[1];
            boolean upper_closed = false;
            Value upper_value;
            if(upper_expr.charAt(0) == '+'){
                upper_value = Value.INFINITE;
            } else {
                if(upper_expr.charAt(upper_expr.length()-1) == ']')
                    upper_closed = true;
                upper_value = parseFiniteValue(upper_expr.substring(0,upper_expr.length()-1));
            }

            boolean upper_bound_needed = true;

            if(upper_value.isInfinite())
                upper_bound_needed = false;

            if(upper_bound_needed) {
                AtomConstraint upper_atomConstraint = new AtomConstraint(clock1, clock2, upper_closed, upper_value);
                outcome.addAtomConstraint(upper_atomConstraint);
            }
        }
        return outcome;
    }

    private static Value parseFiniteValue(String valueText) {
        return new Value(Double.parseDouble(valueText));
    }

    public static TA buildTAFromResetLogicalTimedWord(ResetLogicalTimedWord rlw, Set<Clock> clockSet, Map<Clock, Value> Kmap){
        TA outcome = new TA(TeacherSetting.VERIFY_WORD_TA_NAME);
        for(Clock clock : clockSet)
            outcome.addClock(clock);

        int length = rlw.getLength();
        List<Location> locations = new LinkedList<>();
        for(int i=0;i<length;i++){
            Location location = new Location("q"+i, false, outcome);
            outcome.addLocation(location);
            locations.add(location);
        }
        Location location = new Location("q"+length, true, outcome);
        outcome.addLocation(location);
        locations.add(location);
        outcome.setQ0(locations.get(0));

        for(int i=0;i<length;i++){
            Location source = locations.get(i);
            Location target = locations.get(i+1);
            ResetLogicalTimedAction rla = rlw.getTimedAction(i);
            Action action = rla.getAction();
            ClockValuation clockValuation = rla.getClockValuation();
            Set<Clock> reset = rla.getReset_clocks();
            Constraint guard = generateGuard(clockValuation, Kmap);

            Transition transition = new Transition(source, action, guard, reset, target);

            outcome.addTransition(transition);
            outcome.addAction(action);
        }
        return outcome;
    }

    private static Constraint generateGuard(ClockValuation clockValuation, Map<Clock, Value> Kmap){
        Constraint outcome = new Constraint();
        for(Map.Entry<Clock, Value> entry : clockValuation){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value k = Kmap.get(clock);
            if(Value.gt(value, k)){
                AtomConstraint atomConstraint = new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS, k.negative());
                outcome.addAtomConstraint(atomConstraint);
            } else {
                if(value.isInteger()){
                    AtomConstraint atomConstraint1 = new AtomConstraint(clock, Clock.getZeroClock(), AtomConstraint.LESS_EQ, value);
                    AtomConstraint atomConstraint2 = new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS_EQ, value.negative());
                    outcome.addAtomConstraint(atomConstraint1);
                    outcome.addAtomConstraint(atomConstraint2);
                } else {
                    Value integerPart = value.getIntegerPart();
                    AtomConstraint atomConstraint1 = new AtomConstraint(clock, Clock.getZeroClock(), AtomConstraint.LESS_EQ, Value.add(integerPart, Value.ONE));
                    AtomConstraint atomConstraint2 = new AtomConstraint(Clock.getZeroClock(), clock, AtomConstraint.LESS_EQ, integerPart.negative());
                    outcome.addAtomConstraint(atomConstraint1);
                    outcome.addAtomConstraint(atomConstraint2);
                }
            }
        }
        return outcome;
    }

    public static void buildJsonFromTA(String file_path, TA ta){
        JSONObject ta_json = buildJsonObjectFromTA(ta);
        try {
            File file = new File(file_path);
            if(!file.exists())
                Files.createDirectories(file.getParentFile().toPath());
            FileWriter fileWriter = new FileWriter(file_path);
            String content = ta_json.toJSONString();
            content = JsonFormat.formatJson(content);
            fileWriter.write(content);
            fileWriter.flush();
            fileWriter.close();
        } catch(IOException e) {
            throw new RuntimeException(ModelFileExceptions.WRITE_TO_FILE_FAILED);
        }
    }

    public static JSONObject buildJsonObjectFromTA(TA ta) {
        JSONObject ta_json = new JSONObject();
        ta_json.put("name",ta.getName());
        ta_json.put("clocks",ta.getX().keySet());
        ta_json.put("actions",ta.getSigma().keySet());

        // Build locations
        ta_json.put("init_location",ta.getQ0().getName());
        JSONArray locations = new JSONArray();
        for(Location location : ta.getQ().values()){
            JSONObject loc_json = new JSONObject();
            loc_json.put("name",location.getName());
            loc_json.put("accepting",location.is_accepting());
            JSONObject cons_json = constraint2Json(location.getInvariant());
            loc_json.put("invariant",cons_json);
            locations.add(loc_json);
        }
        ta_json.put("locations", locations);

        // Build transitions
        JSONArray transitions = new JSONArray();
        for(Transition transition : getSortedTransitionsForOutput(ta)){
            JSONObject trans_json = transition2Json(transition);
            transitions.add(trans_json);
        }
        ta_json.put("transitions", transitions);
        return ta_json;
    }

    protected static JSONObject transition2Json(Transition transition){
        JSONObject trans_json = new JSONObject();
        trans_json.put("source", transition.getSource().getName());
        Action action = transition.getAction();
        if(!action.isEpsilon()){
            trans_json.put("action", action.getName());
        }
        trans_json.put("guard", constraint2Json(transition.getGuard()));
        Set<String> reset_name = new HashSet<>();
        for(Clock clock : transition.getReset())
            reset_name.add(clock.getName());
        trans_json.put("reset", reset_name);
        trans_json.put("target", transition.getTarget().getName());
        return trans_json;
    }

    protected static JSONObject constraint2Json(Constraint constraint){
        JSONObject cons_Json = new JSONObject();
        Map<String, ConstraintJsonInterval> data = new HashMap<>();
        for(AtomConstraint atomConstraint : constraint.getAtomConstraints()){
            Clock c1 = atomConstraint.getClock1();
            Clock c2 = atomConstraint.getClock2();
            boolean closed = atomConstraint.isClosed();
            Value value = atomConstraint.getUpperbound();
            String left;
            boolean is_upper = true;
            boolean diagonal = false;
            if(c2.isZeroClock()) {
                left = c1.getName();
            } else if (c1.isZeroClock()) {
                left = c2.getName();
                value = value.negative();
                is_upper = false;
            } else {
                diagonal = true;
                if(data.containsKey(c1.getName() + '-' + c2.getName())){
                    left = c1.getName() + '-' + c2.getName();
                } else if(data.containsKey(c2.getName() + '-' + c1.getName())) {
                    left = c2.getName() + '-' + c1.getName();
                    value = value.negative();
                    is_upper = false;
                } else {
                    left = c1.getName() + '-' + c2.getName();
                }
            }
            ConstraintJsonInterval right = data.get(left);
            if(right == null) {
                right = new ConstraintJsonInterval(diagonal);
                data.put(left, right);
            }
            if(is_upper){
                right.includeUpperBound(value, closed);
            } else {
                right.includeLowerBound(value, closed);
            }
        }
        for(Map.Entry<String,ConstraintJsonInterval> entry : data.entrySet()){
            String left = entry.getKey();
            String right_s = entry.getValue().format();
//            right_s = right_s.replace(".0","");
            right_s = right_s.replaceAll("\\.0+", "");
            cons_Json.put(left, right_s);
        }
        return cons_Json;
    }

    private static final class ConstraintJsonInterval {
        private Value lower;
        private boolean lowerClosed;
        private Value upper;
        private boolean upperClosed;

        private ConstraintJsonInterval(boolean diagonal) {
            this.lower = diagonal ? Value.MINUS_INFINITE : Value.ZERO;
            this.lowerClosed = true;
            this.upper = Value.INFINITE;
            this.upperClosed = false;
        }

        private void includeLowerBound(Value value, boolean closed) {
            if (this.lower.isMinus_infinite()
                    || Value.gt(value, this.lower)
                    || (Value.eq(value, this.lower) && this.lowerClosed && !closed)) {
                this.lower = value;
                this.lowerClosed = closed;
            }
        }

        private void includeUpperBound(Value value, boolean closed) {
            if (this.upper.isInfinite()
                    || Value.lt(value, this.upper)
                    || (Value.eq(value, this.upper) && this.upperClosed && !closed)) {
                this.upper = value;
                this.upperClosed = closed;
            }
        }

        private String format() {
            String lowerText = this.lower.isMinus_infinite()
                    ? "(-"
                    : (this.lowerClosed ? "[" : "(") + this.lower;
            String upperText = this.upper.isInfinite()
                    ? "+)"
                    : this.upper + (this.upperClosed ? "]" : ")");
            return lowerText + "," + upperText;
        }
    }

    public static void buildOldJsonFromTA(String file_path, TA ta){
        JSONObject ta_json = new JSONObject();
        ta_json.put("name", ta.getName());

        JSONObject trans = new JSONObject();
        List<Clock> clockList = new LinkedList<>(ta.getAllClocks());
        int i = 0;
        for(Transition transition : getSortedTransitionsForOutput(ta)){
            trans.put("" + i, TAUtil.transition2OldJson(transition, clockList));
            i++;
        }

        ta_json.put("tran", trans);
        ta_json.put("init", TAUtil.location2OldJson(ta.getQ0()));

        Set<String> location_IDs = new HashSet<>();
        Set<String> accepting_location_IDs = new HashSet<>();
        for(Location location : ta.getQ().values()){
            String id = TAUtil.location2OldJson(location);
            location_IDs.add(id);
            if(location.is_accepting())
                accepting_location_IDs.add(id);
        }
        ta_json.put("accept", accepting_location_IDs);
        ta_json.put("l", location_IDs);
        ta_json.put("sigma",ta.getSigma().keySet());

        try {
            File file = new File(file_path);
            if(!file.exists())
                Files.createDirectories(file.getParentFile().toPath());
            FileWriter fileWriter = new FileWriter(file_path);
            String content = ta_json.toJSONString();
            content = JsonFormat.formatJson(content);
            fileWriter.write(content);
            fileWriter.flush();
            fileWriter.close();
        } catch(IOException e) {
            throw new RuntimeException(ModelFileExceptions.WRITE_TO_FILE_FAILED);
        }
    }

    protected static String location2OldJson(Location location){
        return "" + (location.getId() + 1);
    }

    protected static List<String> transition2OldJson(Transition transition, List<Clock> clockList){
        List<String> outcome = new LinkedList<>();
        outcome.add(TAUtil.location2OldJson(transition.getSource()));
        outcome.add(transition.getAction().getName());
        outcome.addAll(TAUtil.guard2OldJson(transition.getGuard(), clockList));
        outcome.addAll(TAUtil.reset2OldJson(transition.getReset(), clockList));
        outcome.add(TAUtil.location2OldJson(transition.getTarget()));
        return outcome;
    }

    protected static List<String> guard2OldJson(Constraint guard, List<Clock> clockList){
        List<String> outcome = new LinkedList<>();
        Map<Clock, Pair<Boolean, String>> l_map = new HashMap<>();
        Map<Clock, Pair<Boolean, String>> u_map = new HashMap<>();
        for(AtomConstraint atom : guard.getAtomConstraints()){
            Clock clock1 = atom.getClock1();
            Clock clock2 = atom.getClock2();
            Value value = atom.getUpperbound();
            boolean closed = atom.isClosed();
            if(clock1.isZeroClock()){
                value = Value.negative(value);
                String l = value.toString().replaceAll("\\.0+", "");
                l_map.put(clock2, new Pair<>(closed, l));
            } else {
                assert clock2.isZeroClock();
                String u = value.toString().replaceAll("\\.0+", "");
                u_map.put(clock1, new Pair<>(closed, u));
            }
        }

        for(Clock clock : clockList){
            StringBuilder exp = new StringBuilder();
            if(l_map.containsKey(clock)){
                Pair<Boolean, String> data = l_map.get(clock);
                if(data.p0() == AtomConstraint.LESS_EQ)
                    exp.append("[");
                else
                    exp.append("(");
                exp.append(data.p1());
            } else {
                exp.append("[0");
            }
            exp.append(",");
            if(u_map.containsKey(clock)){
                Pair<Boolean, String> data = u_map.get(clock);
                exp.append(data.p1());
                if(data.p0() == AtomConstraint.LESS_EQ)
                    exp.append("]");
                else
                    exp.append(")");
            } else {
                exp.append("+)");
            }
            outcome.add(exp.toString());
        }
        return outcome;
    }

    protected static List<String> reset2OldJson(Set<Clock> reset, List<Clock> clockList){
        List<String> outcome = new LinkedList<>();
        for(Clock clock : clockList){
            if(reset.contains(clock))
                outcome.add("r");
            else
                outcome.add("n");
        }
        return outcome;
    }

    private static List<Transition> getSortedTransitionsForOutput(TA ta) {
        List<Transition> ordered = new ArrayList<>(ta.getDelta().values());
        ordered.sort(
                Comparator.comparing((Transition t) -> nullToEmpty(t.getSource().getName()))
                        .thenComparing(t -> nullToEmpty(t.getAction().getName()))
                        .thenComparing(t -> guardLowerForSort(constraint2Json(t.getGuard())))
                        .thenComparing(t -> guardCanonicalForSort(constraint2Json(t.getGuard())))
                        .thenComparing(t -> nullToEmpty(t.getTarget().getName()))
        );
        return ordered;
    }
}
