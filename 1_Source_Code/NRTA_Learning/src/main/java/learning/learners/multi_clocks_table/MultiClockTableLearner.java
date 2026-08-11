package learning.learners.multi_clocks_table;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import exception.ValueExceptions;
import language.actions.LogicalTimedAction;
import language.actions.RegionTimedAction;
import language.actions.ResetLogicalTimedAction;
import language.words.*;
import learning.learners.Learner;
import learning.teachers.Teacher;
import learning.LearningSetting;
import lombok.Setter;
import run.ClockValuation;
import ta.*;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;
import tools.Tuple.Pair;
import tools.Tuple.Reply;
import tools.Tuple.Triplet;
import verify.GlobalSetting;
import tools.FileIO.TAUtil;
import tools.FormatOutput.LearningConsole;

import java.io.File;
import java.util.*;

public class MultiClockTableLearner extends Learner implements TableLearner {
    private static final int GREEN_BANNER_WIDTH = "********* Start Equivalent Query! *********".length();
    private static final int RESET_UN_CONSISTENT = 0;
    private static final int E_UN_CONSISTENT = 1;
    public static final int ADD_ZERO = 0;
    public static final int ADD_VALID = 1;

    private final Set<ResetLogicalTimedWord> s_area;
    private final Set<ResetLogicalTimedWord> r_area;
    private final List<RegionTimedWord> e_area;
    private final Map<ResetLogicalTimedWord,TableRow> s_rowMap;
    private final Map<ResetLogicalTimedWord,TableRow> r_rowMap;
    private final Map<Clock,Clock> clockMap_th;
    private final Set<Clock> hypothesis_clocks;
    private final Set<Clock> target_clocks;
    private final Map<Clock, Value> K_map;
    private final Context ctx;
    private final Map<RegionTimedAction,Set<ResetLogicalTimedWord>> last_action_map;
    private final Map<String, ResetLogicalTimedWord> cacheOfResetInfoQuery;
    @Setter
    private File hypothesisSaveDir;

    private ResetLogicalTimedWord keyWord;
    private final TableRow[] unConsistentCouple;
    private final ResetLogicalTimedWord[] unConsistentBasePrefix;
    private final ResetLogicalTimedWord[] unConsistentPrefix;
    private ResetLogicalTimedWord counterExamplePrefix;
    private final Set<ResetLogicalTimedWord> addedCounterExamplePrefixes;
    private ResetLogicalTimedWord unClosedPrefix;
    private ResetLogicalTimedWord closedAddedSRow;
    private final Set<ResetLogicalTimedWord> closedAddedRRows;
    private int consistentAddedEIndex;
    private boolean consistentColumnOnlyHighlightMode;
    private RegionTimedAction unConsistentKey;
    private int unConsistentCase;
    @Setter
    private int closedMethod = MultiClockTableLearner.ADD_ZERO;


    public MultiClockTableLearner(Teacher teacher, Set<Clock> target_clockSet, Set<Action> actionSet, String hypothesis_name) {
        super(teacher, target_clockSet, actionSet, hypothesis_name);
        this.ctx = teacher.getCtx();
        this.s_area = new HashSet<>();
        this.r_area = new HashSet<>();
        this.e_area= new LinkedList<>();
        this.s_area.add(new ResetLogicalTimedWord());
        this.e_area.add(new RegionTimedWord());

        this.s_rowMap = new HashMap<>();
        this.r_rowMap = new HashMap<>();
        this.prepared = false;
        this.unConsistentCouple = new TableRow[2];
        this.unConsistentBasePrefix = new ResetLogicalTimedWord[2];
        this.unConsistentPrefix = new ResetLogicalTimedWord[2];
        this.addedCounterExamplePrefixes = new HashSet<>();
        this.closedAddedRRows = new HashSet<>();
        this.consistentAddedEIndex = -1;
        this.consistentColumnOnlyHighlightMode = false;
        this.last_action_map = new HashMap<>();
        this.cacheOfResetInfoQuery = new HashMap<>();
        this.clockMap_th = new HashMap<>();
        this.hypothesis_clocks = new HashSet<>();
        this.target_clocks = new HashSet<>();
        this.K_map = teacher.get_K_Map();
        for(Clock target_clock : target_clockSet){
            String target_name = target_clock.getName();
            String h_name = "h_" + target_name;
            RealExpr h_smt_exp = ctx.mkRealConst(h_name);
            Clock h_clock = new Clock(h_name, hypothesis_name, h_smt_exp);
            this.clockMap_th.put(target_clock, h_clock);
            this.hypothesis_clocks.add(h_clock);
            this.target_clocks.add(target_clock);
        }

        for(Action action : this.actionSet){
            LogicalTimedAction logicalTimedAction = new LogicalTimedAction(action, new ClockValuation(this.clockSet));
            LogicalTimedWord logicalTimedWord = new LogicalTimedWord();
            logicalTimedWord.attend(logicalTimedAction);
            ResetLogicalTimedWord rlw = this.raiseResetInformationQuery(logicalTimedWord);
            this.r_area.add(rlw);
            this.updateLastActionMap(rlw);
        }
    }

    private ResetLogicalTimedWord raiseResetInformationQuery(LogicalTimedWord logicalTimedWord){
        String cacheKey = logicalTimedWord.toString();
        ResetLogicalTimedWord cached = this.cacheOfResetInfoQuery.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ResetLogicalTimedWord resetWord = this.teacher.ResetInformationQuery(logicalTimedWord).evidence();
        this.cacheOfResetInfoQuery.put(cacheKey, resetWord);
        return resetWord;
    }

    private void updateLastActionMap(ResetLogicalTimedWord rlw){
        if (rlw.isEmpty())
            return;
        ResetLogicalTimedAction last = rlw.getLast();
        RegionTimedAction ra = new RegionTimedAction(last, this.K_map);
        if(this.last_action_map.containsKey(ra))
            this.last_action_map.get(ra).add(rlw);
        else{
            Set<ResetLogicalTimedWord> new_set = new HashSet<>();
            new_set.add(rlw);
            this.last_action_map.put(ra,new_set);
        }
    }

    /****
     * Compute valid successor. Prefix validity check has been moved outside.
     * Returns the entire word directly.
     * Returns null if not found.
     * @param prefix
     * @param suffix
     * @return
     */
    public ResetLogicalTimedWord findValidSuccessorV1(ResetLogicalTimedWord prefix, RegionTimedWord suffix){
        if(suffix.isEmpty())
            return prefix;
        ClockValuation init_v = prefix.getLastValuation();
        if(init_v == null)
            init_v = new ClockValuation(this.clockSet);
        for(int i=0;i<suffix.getLength();i++){
            LogicalTimedWord prefix_lw = prefix.toLogicalTimedWord();
            RegionTimedAction rta = suffix.getTimedAction(i);
            Reply<LogicalTimedAction> reply = rta.buildLogicalTimedAction(this.ctx, init_v);
            if(reply.valid()){
                prefix_lw.attend(reply.evidence());
                prefix = this.raiseResetInformationQuery(prefix_lw);
                assert prefix != null;
            } else
                return null;

            init_v = prefix.getLastValuation();
        }
        return prefix;
    }
    public ResetLogicalTimedWord findValidSuccessor(ResetLogicalTimedWord prefix, RegionTimedWord suffix){
        if (suffix.isEmpty()) { return prefix; }
        LogicalTimedWord prefixLogical = prefix.toLogicalTimedWord(true);
        for (int i = 0; i < suffix.getLength(); i++) {
            RegionTimedAction rta = suffix.getTimedAction(i);
            LogicalTimedAction la = new LogicalTimedAction(rta.getAction(), rta.getRegion().getSelfClockValuation());
            prefixLogical.attend(la);
        }
        return this.raiseResetInformationQuery(prefixLogical);
    }

    /****
     * Check if word is composed of prefix + e\in suffix
     * @param prefix
     * @param suffix
     * @param word
     * @return
     */
    public boolean isValidSuccessor(ResetLogicalTimedWord prefix, RegionTimedWord suffix, ResetLogicalTimedWord word){
        if(word.getLength() != prefix.getLength() + suffix.getLength())
            return false;
        if(!word.isPrefix(prefix))
            return false;
        ClockValuation current;
        if(prefix.isEmpty())
            current = new ClockValuation(this.clockSet);
        else
            current = prefix.getLastValuation();
        for(int i=0;i<suffix.getLength();i++){
            ResetLogicalTimedAction next_act = word.getTimedAction(i+prefix.getLength());
            RegionTimedAction next_reg = suffix.getTimedAction(i);
            if(!current.delayEquals(next_act.getClockValuation()))
                return false;
            if(!next_reg.isCase(next_act))
                return false;
            current = next_act.getNextClockValuation();
        }
        return true;
    }

    /*****
     * Perform membership query
     * @param rlw
     * @return
     */
    public boolean raiseMembershipQuery(ResetLogicalTimedWord rlw) {
        return this.teacher.MembershipQuery(rlw.toLogicalTimedWord());
    }


    public Triplet<Boolean,ResetLogicalTimedWord,Boolean> raiseEquivalenceQuery(NTA hypothesis) {
        return this.teacher.EquivalenceQuery(hypothesis);
    }

    @Override
    public void refine(ResetLogicalTimedWord ctx) {
        this.counterExamplePrefix = ctx;
        this.addedCounterExamplePrefixes.clear();
        List<ResetLogicalTimedWord> prefixList = ctx.getAllPrefixes();
        for(ResetLogicalTimedWord new_r : prefixList){
            if(s_area.contains(new_r)) continue;
            if(r_area.contains(new_r)) continue;
            this.r_area.add(new_r);
            this.addedCounterExamplePrefixes.add(new_r);
            this.updateLastActionMap(new_r);
        }
        fillTable(r_area,r_rowMap);
        if (GlobalSetting.SHOW_TABLE_CHANGE) {
            this.show();
            this.counterExamplePrefix = null;
            this.addedCounterExamplePrefixes.clear();
        }
        prepareTable();
    }

    @Override
    public boolean isPrepared() {
        return false;
    }

    @Override
    public NTA learn() {
        if (LearningSetting.isLearningNormalOrVerbose()) {
            LearningConsole.showTurnHeader(1);
        }
        fillTable();
        if (GlobalSetting.SHOW_TABLE_CHANGE) {
            this.show();
        }
        prepareTable();
        int i = 0;
        for(;;i++) {
            if (Thread.currentThread().isInterrupted())
                return null;
            printGreenBanner("Start Hypothesis Build!");
            NTA hypothesis = this.generateHypothesis();
            if(GlobalSetting.SHOW_HYPOTHESIS)
                System.out.println(hypothesis);
            if (LearningSetting.SAVE_HYPOTHESIS && this.hypothesisSaveDir != null) {
                File out = new File(this.hypothesisSaveDir, String.format("table_hyp_T%04d.json", i));
                TAUtil.buildJsonFromTA(out.getPath(), hypothesis.getTA_list().get(0));
            }
            if (LearningSetting.isLearningNormalOrVerbose()) {
                System.out.println(
                        ColoredFront.dealtString(
                                "********* Start Equivalent Query! *********",
                                Color.Green,
                                Type.Common));
            }
            Triplet<Boolean, ResetLogicalTimedWord, Boolean> reply = this.raiseEquivalenceQuery(hypothesis);
            if(reply.p0()) {
                printPass();
                return hypothesis;
            }
            ResetLogicalTimedWord ctx = reply.p1();

            if(GlobalSetting.SHOW_COUNTER_EXAMPLES){
                if(reply.p2())
                    System.out.println("positive ctx: ");
                else
                    System.out.println("negative ctx: ");
                System.out.println(
                        ColoredFront.dealtString(
                                ctx.toString(),
                                Color.Yellow,
                                Type.Common)
                );
            }
            if (LearningSetting.isLearningNormalOrVerbose()) {
                LearningConsole.showTurnHeader(i + 2);
            }

            if(ctx.toString().equals("(c,<x0:1.7,x1:1.7>,[x1])(a,<x0:8.3,x1:6.7>,[x0])")){
                System.out.println("ctx");
                LogicalTimedWord replaced_lw = ctx.toLogicalTimedWord().replaceClock(this.clockMap_th);
                TA ta = hypothesis.getTA_list().get(0);
                List<List<Transition>> temp = ta.runWord(replaced_lw);
                System.out.println("ctx");
            }

            this.refine(ctx);
        }
    }

    public void prepareTable(){
        if(GlobalSetting.SHOW_TABLE_CHANGE){
            do {
                this.prepared = true;
                printGreenBanner("Start Closed Check!");
                if (!isClosed()) {
                    this.prepared = false;
                    this.show();
                    makeClosed();
                    printGreenBanner("After makeClosed Table");
                    this.show();
                    clearClosedHighlight();
                    this.unClosedPrefix = null;
                } else {
                    printPass();
                }
                printGreenBanner("Start Consistent Check!");
                if (!isConsistent()) {
                    this.prepared = false;
                    this.show();
                    makeConsistent();
                    printGreenBanner("After makeConsistent Table");
                    this.consistentColumnOnlyHighlightMode = true;
                    this.show();
                    this.consistentColumnOnlyHighlightMode = false;
                    clearConsistentColumnHighlight();
                    this.unConsistentBasePrefix[0] = null;
                    this.unConsistentBasePrefix[1] = null;
                    this.unConsistentPrefix[0] = null;
                    this.unConsistentPrefix[1] = null;
                } else {
                    printPass();
                }
            } while (!this.prepared);
        } else {
            do {
                this.prepared = true;
                printGreenBanner("Start Closed Check!");
                if (!isClosed()) {
                    this.prepared = false;
                    makeClosed();
                } else {
                    printPass();
                }
                printGreenBanner("Start Consistent Check!");
                if (!isConsistent()) {
                    this.prepared = false;
                    makeConsistent();
                } else {
                    printPass();
                }
            } while (!this.prepared);
        }
    }

    private void printGreenBanner(String message) {
        int available = Math.max(2, GREEN_BANNER_WIDTH - message.length() - 2);
        int leftStars = available / 2;
        int rightStars = available - leftStars;
        String banner = "*".repeat(leftStars) + " " + message + " " + "*".repeat(rightStars);
        System.out.println(ColoredFront.dealtString(banner, Color.Green, Type.Common));
    }

    private void printPass() {
        System.out.println(ColoredFront.dealtString("pass!", Color.Green, Type.Common));
    }

    private void clearClosedHighlight() {
        this.closedAddedSRow = null;
        this.closedAddedRRows.clear();
    }

    private void clearConsistentColumnHighlight() {
        this.consistentAddedEIndex = -1;
    }

    @Override
    public void show() {
        List<String> stringList = new LinkedList<>();
        List<String> suffixStringList = new LinkedList<>();
        List<String> suffixLabelList = new LinkedList<>();
        List<Integer> suffixColWidths = new LinkedList<>();
        List<ResetLogicalTimedWord> prefixList = getPrefixList();
        int num = prefixList.size();
        int num_length = String.valueOf(num).length()+2;
        int maxLen = 0;
        for (ResetLogicalTimedWord word : prefixList) {
            String s = word.toString();
            stringList.add(s);
            maxLen = Math.max(maxLen, s.length());
        }
        StringBuilder areaSb = new StringBuilder();
        areaSb.append("E-AREA:\n");
        List<String> suffixAreaValueList = new LinkedList<>();
        int maxSuffixLabelLength = 0;
        int nonEmptySuffixIndex = 1;
        for (int i = 0; i < this.e_area.size(); i++) {
            RegionTimedWord words = this.e_area.get(i);
            String label;
            if (words.isEmpty()) {
                label = "E";
            } else {
                label = "E" + nonEmptySuffixIndex;
                nonEmptySuffixIndex++;
            }
            suffixLabelList.add(label);
            suffixStringList.add(label);
            suffixAreaValueList.add(words.toString());
            maxSuffixLabelLength = Math.max(maxSuffixLabelLength, label.length());
        }
        for (int i = 0; i < suffixLabelList.size(); i++) {
            String label = suffixLabelList.get(i);
            String areaLine = new StringBuilder()
                    .append(label)
                    .append(" ".repeat(Math.max(0, maxSuffixLabelLength - label.length())))
                    .append(" = ")
                    .append(suffixAreaValueList.get(i))
                    .toString();
            if (this.consistentAddedEIndex == i) {
                areaSb.append(ColoredFront.dealtString(areaLine, Color.Yellow, Type.Common));
            } else {
                areaSb.append(areaLine);
            }
            areaSb.append('\n');
        }
        for (int j = 0; j < this.e_area.size(); j++) {
            int width = suffixLabelList.get(j).length();
            for (ResetLogicalTimedWord prefix : prefixList) {
                TableRow row = this.seekBothArea(prefix);
                String cell = row.get(j).toString();
                width = Math.max(width, cell.length());
            }
            suffixColWidths.add(width);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(areaSb).append('\n');

        sb.append(" ".repeat(Math.max(0, maxLen + num_length + 1)));
        sb.append("|");
        for (int j = 0; j < suffixStringList.size(); j++) {
            String s = suffixStringList.get(j);
            int width = suffixColWidths.get(j);
            String headerCell = leftPadOne(s, width);
            if (this.consistentAddedEIndex == j) {
                sb.append(ColoredFront.dealtString(headerCell, Color.Orange, Type.Common));
            } else {
                sb.append(headerCell);
            }
            sb.append("|");
        }
        sb.append("\n");
        int order = 1;
        for (int i = 0; i < prefixList.size(); i++) {
            StringBuilder rowSb = new StringBuilder();
            rowSb.append(order);
            rowSb.append(" ".repeat(Math.max(0, num_length - String.valueOf(order).length())));
            order++;
            rowSb.append('|');
            ResetLogicalTimedWord prefix = prefixList.get(i);
            TableRow row = this.seekBothArea(prefix);

            String prefixString = stringList.get(i);
            rowSb.append(prefixString);
            int slen = this.s_area.size();
            rowSb.append(" ".repeat(Math.max(0, maxLen - prefixString.length())));
            rowSb.append("|");

            for (int j = 0; j < this.e_area.size(); j++) {
                TableElement element = row.get(j);
                String element_data = element.toString();
                int width = suffixColWidths.get(j);
                String cellText = element_data + " ".repeat(Math.max(0, width - element_data.length()));
                if (this.consistentAddedEIndex == j) {
                    rowSb.append(ColoredFront.dealtString(cellText, Color.Orange, Type.Common));
                } else {
                    rowSb.append(cellText);
                }
                rowSb.append("|");
            }
            String rowString = rowSb.toString();
            Color rowColor = null;
            if (!this.consistentColumnOnlyHighlightMode) {
                if (this.closedAddedSRow != null && this.closedAddedSRow.equals(prefix)) {
                    rowColor = Color.Yellow;
                } else if (this.closedAddedRRows.contains(prefix)) {
                    rowColor = Color.Orange;
                } else if (this.counterExamplePrefix != null && this.counterExamplePrefix.equals(prefix)) {
                    rowColor = Color.Yellow;
                } else if (this.addedCounterExamplePrefixes.contains(prefix)) {
                    rowColor = Color.Orange;
                } else if ((this.unConsistentPrefix[0] != null && this.unConsistentPrefix[0].equals(prefix))
                        || (this.unConsistentPrefix[1] != null && this.unConsistentPrefix[1].equals(prefix))
                        || (this.unClosedPrefix != null && this.unClosedPrefix.equals(prefix))) {
                    rowColor = Color.Yellow;
                } else if ((this.unConsistentBasePrefix[0] != null && this.unConsistentBasePrefix[0].equals(prefix))
                        || (this.unConsistentBasePrefix[1] != null && this.unConsistentBasePrefix[1].equals(prefix))) {
                    rowColor = Color.Orange;
                }
            }
            if (rowColor != null) {
                sb.append(ColoredFront.dealtString(rowString, rowColor, Type.Common));
            } else {
                sb.append(rowString);
            }
            sb.append("\n");

            if (i == slen - 1) {
                sb.append("-".repeat(Math.max(0, maxLen + num_length + 1)));
                order = 1;
                sb.append("|");
                for (int j = 0; j < suffixStringList.size(); j++) {
                    int width = suffixColWidths.get(j);
                    sb.append("-".repeat(width));
                    sb.append("|");
                }
                sb.append("\n");
            }
        }
        System.out.println(sb);
    }

    private static String leftPadOne(String text, int width) {
        int rightPadding = Math.max(0, width - text.length() - 1);
        return " " + text + " ".repeat(rightPadding);
    }

    @Override
    public void fillTable() {
        this.fillTable(this.s_area, this.s_rowMap);
        this.fillTable(this.r_area, this.r_rowMap);
    }

    public void fillTable(Collection<ResetLogicalTimedWord> set, Map<ResetLogicalTimedWord,TableRow> rows){
        int start;
        TableRow current_row;
        for (ResetLogicalTimedWord prefix : set) {
            if(!rows.containsKey(prefix)){
                current_row = new TableRow();
                rows.put(prefix,current_row);
            }
            current_row = rows.get(prefix);
            start = current_row.size();

            for (int i = start; i < this.e_area.size(); i++) {
                RegionTimedWord suffix = this.e_area.get(i);
                ResetLogicalTimedWord rlw = this.findValidSuccessor(prefix, suffix);
                current_row.add(this.raiseMembershipQuery(rlw), rlw, prefix);
            }
        }
    }


    @Override
    public boolean isClosed() {
        Set<TableRow> sRows = new HashSet<>(s_rowMap.values());
        List<ResetLogicalTimedWord> rList = new ArrayList<>(this.r_area);
        rList.sort(Comparator.comparingInt(TimedWord::getLength));
        for (ResetLogicalTimedWord rPrefix : rList) {
            TableRow rRow = this.r_rowMap.get(rPrefix);
            if (!sRows.contains(rRow)) {
                this.unClosedPrefix = rPrefix;
                return false;
            }
        }
        this.unClosedPrefix = null;
        return true;
    }

    @Override
    public void makeClosed() {
        clearClosedHighlight();
        Set<TableRow> sRows = new HashSet<>(s_rowMap.values());
        List<ResetLogicalTimedWord> r_list = new ArrayList<>(this.r_area);
        r_list.sort(Comparator.comparingInt(TimedWord::getLength));
        for(ResetLogicalTimedWord w : r_list){
            TableRow row = this.r_rowMap.get(w);
            if(!sRows.contains(row)){
                this.r_area.remove(w);
                this.r_rowMap.remove(w);
                this.s_area.add(w);
                this.closedAddedSRow = w;
                this.s_rowMap.put(w,row);
                sRows.add(row);
                for(Action action : actionSet){
                    ResetLogicalTimedWord resetLogicalTimedWord = null;
                    if(this.closedMethod == MultiClockTableLearner.ADD_ZERO) {
                        LogicalTimedAction logicalTimedAction = new LogicalTimedAction(action, new ClockValuation(this.target_clocks));
                        LogicalTimedWord logicalTimedWord = w.toLogicalTimedWord();
                        logicalTimedWord.attend(logicalTimedAction);
                        resetLogicalTimedWord = this.raiseResetInformationQuery(logicalTimedWord);
                    } else if (this.closedMethod == MultiClockTableLearner.ADD_VALID) {
                        LogicalTimedAction logicalTimedAction = new LogicalTimedAction(action,w.getLastValuation());
                        LogicalTimedWord logicalTimedWord = w.toLogicalTimedWord();
                        logicalTimedWord.attend(logicalTimedAction);
                        resetLogicalTimedWord = this.raiseResetInformationQuery(logicalTimedWord);
                    }

                    if(!this.s_area.contains(resetLogicalTimedWord) && !this.r_area.contains(resetLogicalTimedWord)) {
                        this.r_area.add(resetLogicalTimedWord);
                        this.closedAddedRRows.add(resetLogicalTimedWord);
                        this.r_rowMap.put(resetLogicalTimedWord, new TableRow());
                        this.updateLastActionMap(resetLogicalTimedWord);
                    }
                }
            }
        }
        fillTable(this.r_area,this.r_rowMap);
    }

    /****
     * Search for a prefix in both S and R areas
     * @param w
     * @return
     */
    private TableRow seekBothArea(ResetLogicalTimedWord w){
        TableRow outcome = this.s_rowMap.get(w);
        if(outcome==null)
            outcome = this.r_rowMap.get(w);
        return outcome;
    }

    public boolean isConsistentNew(){
        List<ResetLogicalTimedWord> sr = this.getPrefixList();
        Set<Integer> delt_index_set = new HashSet<>();
        ResetLogicalTimedWord prefix1;
        ResetLogicalTimedWord prefix2;

        return true;
    }

    @Override
    public boolean isConsistent() {
        this.unConsistentBasePrefix[0] = null;
        this.unConsistentBasePrefix[1] = null;
        this.unConsistentPrefix[0] = null;
        this.unConsistentPrefix[1] = null;
        List<ResetLogicalTimedWord> sr = this.getPrefixList();
        ResetLogicalTimedWord prefix1;
        ResetLogicalTimedWord prefix2;
        for(int i=0;i<sr.size();i++){
            prefix1 = sr.get(i);
            TableRow row1 = this.seekBothArea(prefix1);
            for(int j=i+1;j<sr.size();j++){
                prefix2 = sr.get(j);
                TableRow row2 = this.seekBothArea(prefix2);
                if(row1.equals(row2)){

                    for(Map.Entry<RegionTimedAction,Set<ResetLogicalTimedWord>> entry : this.last_action_map.entrySet()){
                        RegionTimedAction last = entry.getKey();
                        Set<ResetLogicalTimedWord> corresponding_words = entry.getValue();
                        Set<ResetLogicalTimedWord> corresponding_prefix1 = new HashSet<>();
                        Set<ResetLogicalTimedWord> corresponding_prefix2 = new HashSet<>();
                        for(ResetLogicalTimedWord word : corresponding_words){
                            boolean flagR1 = word.isLongestPrefixViaRegionK(prefix1, this.K_map);
                            boolean flagR2 = word.isLongestPrefixViaRegionK(prefix2, this.K_map);

                            if(flagR1)
                                corresponding_prefix1.add(word);
                            if(flagR2)
                                corresponding_prefix2.add(word);
                        }

                        if(corresponding_prefix1.isEmpty()) continue;
                        if(corresponding_prefix2.isEmpty()) continue;

                        for(ResetLogicalTimedWord resetWord1 : corresponding_prefix1){
                            for(ResetLogicalTimedWord resetWord2 : corresponding_prefix2){
                                ResetLogicalTimedAction last1 = resetWord1.getLast();
                                ResetLogicalTimedAction last2 = resetWord2.getLast();
                                TableRow newRow1 = this.seekBothArea(resetWord1);
                                TableRow newRow2 = this.seekBothArea(resetWord2);
                                if(!last1.getReset_clocks().equals(last2.getReset_clocks())){
                                    this.unConsistentBasePrefix[0] = prefix1;
                                    this.unConsistentBasePrefix[1] = prefix2;
                                    this.unConsistentPrefix[0] = resetWord1;
                                    this.unConsistentPrefix[1] = resetWord2;
                                    this.unConsistentCouple[0] = newRow1;
                                    this.unConsistentCouple[1] = newRow2;
                                    this.unConsistentKey = last;
                                    this.unConsistentCase = MultiClockTableLearner.RESET_UN_CONSISTENT;
                                    return false;
                                }
                                if (!newRow1.equals(newRow2)) {
                                    this.unConsistentBasePrefix[0] = prefix1;
                                    this.unConsistentBasePrefix[1] = prefix2;
                                    this.unConsistentPrefix[0] = resetWord1;
                                    this.unConsistentPrefix[1] = resetWord2;
                                    this.unConsistentCouple[0] = newRow1;
                                    this.unConsistentCouple[1] = newRow2;
                                    this.unConsistentKey = last;
                                    this.unConsistentCase = MultiClockTableLearner.E_UN_CONSISTENT;
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /*********
     * Get all prefix set. This function can be optimized away.
     * @return
     */
    private List<ResetLogicalTimedWord> getPrefixList(){
        List<ResetLogicalTimedWord> sr = new ArrayList<>();
        List<ResetLogicalTimedWord> s = new ArrayList<>(this.s_area);
        s.sort(Comparator.comparingInt(TimedWord::getLength));
        List<ResetLogicalTimedWord> r = new ArrayList<>(this.r_area);
        r.sort(Comparator.comparingInt(TimedWord::getLength));
        sr.addAll(s);
        sr.addAll(r);
        return sr;
    }
    @Override
    public void makeConsistent() {
        clearConsistentColumnHighlight();
        if(this.unConsistentCase == MultiClockTableLearner.RESET_UN_CONSISTENT){
            int newIndex = this.e_area.size();
            RegionTimedWord word = new RegionTimedWord();
            word.attend(this.unConsistentKey);
            this.e_area.add(word);
            this.consistentAddedEIndex = newIndex;
        } else if (this.unConsistentCase == MultiClockTableLearner.E_UN_CONSISTENT) {
            TableRow row1 = unConsistentCouple[0];
            TableRow row2 = unConsistentCouple[1];
            for(int i=0;i<this.e_area.size();i++) {
                RegionTimedWord w = this.e_area.get(i);
                TableElement element1 = row1.get(i);
                TableElement element2 = row2.get(i);
                if (!element1.equals(element2)) {
                    int newIndex = this.e_area.size();
                    RegionTimedWord word = new RegionTimedWord();
                    word.attend(this.unConsistentKey);
                    word = word.concat(w);
                    this.e_area.add(word);
                    this.consistentAddedEIndex = newIndex;
                    break;
                }
            }
        }
        fillTable();
    }

    private Set<Clock> replace(Set<Clock> reset){
        Set<Clock> outcome = new HashSet<>();
        for(Clock clock : reset){
            Clock new_clock = this.clockMap_th.get(clock);
            outcome.add(new_clock);
        }
        return outcome;
    }

    /******
     * Generate hypothesis automaton
     * @return
     */
    @Override
    public NTA generateHypothesis() {
        TA hypothesis = new TA(this.hypothesis_name);
        for(Clock clock : this.hypothesis_clocks)
            hypothesis.addClock(clock);
        for(Action action : this.actionSet)
            hypothesis.addAction(action);
        Map<TableRow,Location> rowLocationMap = new HashMap<>();
        int id = 0;
        List<ResetLogicalTimedWord> s_list = new ArrayList<>(this.s_area);
        Collections.sort(s_list, Comparator.comparingInt(TimedWord::getLength));
        for(ResetLogicalTimedWord prefix : s_list){
            TableRow row = this.s_rowMap.get(prefix);
            boolean accepted = row.get(0).isAccept();
            Location location = new Location("q"+id, accepted, Constraint.TRUE, hypothesis);
            hypothesis.addLocation(location);
            rowLocationMap.put(row, location);
            if(prefix.isEmpty()){
                hypothesis.setQ0(location);
            }
            id++;
        }
        Set<ResetLogicalTimedWord> sr = getPrefixSet();
        /****
         * location(source) --> actions --> list of Triplet(clock valuation, target, reset clocks)
         * every actions' outgoing transitions' guard together should be true.
         * in fact every clock valuation should map to one transition
         * but due to partition function, the guard may be concave,
         * that means we need to use more than one guards to cover one clock valuation
         */
        Map<Location,Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>>> OriginalTransInfo = new HashMap<>();
        for (ResetLogicalTimedWord word : sr) {
            if (word.isEmpty()) {
                continue;
            }
            ResetLogicalTimedWord pre = word.subWord(0,word.getLength()-1);

            if (sr.contains(pre)) {
                ResetLogicalTimedAction resetLogicalTimedAction = word.getLast();
                TableRow source_row = this.seekBothArea(pre);
                TableRow target_row = this.seekBothArea(word);
                Location source = rowLocationMap.get(source_row);
                Location target = rowLocationMap.get(target_row);
                Action action = resetLogicalTimedAction.getAction();
                Set<Clock> reset = this.replace(resetLogicalTimedAction.getReset_clocks());
                ClockValuation clockValuation = resetLogicalTimedAction.getClockValuation();

                Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>> actionListMap = OriginalTransInfo.get(source);
                if(actionListMap==null){
                    actionListMap = new HashMap<>();
                    OriginalTransInfo.put(source,actionListMap);
                }
                List<Triplet<ClockValuation,Location,Set<Clock>>> tripletList = actionListMap.get(action);
                if(tripletList==null){
                    tripletList = new LinkedList<>();
                    actionListMap.put(action,tripletList);
                }
                tripletList.add(new Triplet<>(clockValuation,target,reset));
            }
        }
        this.generateTransitions(hypothesis, OriginalTransInfo);
        return new NTA(hypothesis);
    }

    public void generateTransitions(TA hypothesis, Map<Location,Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>>> OriginalTransInfo){
        for(Map.Entry<Location,Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>>> entry : OriginalTransInfo.entrySet()){
            Location source = entry.getKey();

            Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>> actionListMap = entry.getValue();
            for(Map.Entry<Action,List<Triplet<ClockValuation,Location,Set<Clock>>>> entry1 : actionListMap.entrySet()){
                Action action = entry1.getKey();
                List<Triplet<ClockValuation,Location,Set<Clock>>> tripletList = entry1.getValue();
                List<Triplet<Part,Location,Set<Clock>>> partition_data = this.partitionFunction(tripletList);
                for(Triplet<Part,Location,Set<Clock>> triplet : partition_data){
                    Part partition = triplet.p0();
                    Location target = triplet.p1();
                    Set<Clock> reset = triplet.p2();
                    for(Constraint guard : partition.getConvex()){
                        Constraint new_guard = guard.replace(this.clockMap_th);
                        Transition transition = new Transition(source, action, new_guard, reset, target);
                        hypothesis.addTransition(transition);
                    }
                }
            }
        }
    }

    private Set<ResetLogicalTimedWord> getPrefixSet(){
        Set<ResetLogicalTimedWord> sr = new HashSet<>();
        sr.addAll(this.s_area);
        sr.addAll(this.r_area);
        return sr;
    }

    /******
     * This partition function can only be used for zero rule
     * @param tripletList
     * @return
     */
    public List<Triplet<Part,Location,Set<Clock>>> partitionFunction(List<Triplet<ClockValuation,Location,Set<Clock>>> tripletList) {
        assert this.closedMethod == MultiClockTableLearner.ADD_ZERO;
        if(tripletList.isEmpty())
            throw new RuntimeException(ValueExceptions.UNABLE_TO_PARTITION_NULL_VALUE);

        List<Triplet<Part,Location,Set<Clock>>> outcome = new LinkedList<>();
        tripletList.sort(Comparator.comparing(Triplet::p0));
        List<Triplet<ClockValuation,Location,Set<Clock>>> newList = new LinkedList<>();
        ClockValuation lastV = tripletList.get(0).getP0();
        newList.add(tripletList.get(0));
        for(int i=1;i<tripletList.size();i++){
            ClockValuation currentV = tripletList.get(i).getP0();
            if(!lastV.regionEqual(currentV, this.K_map)){
                lastV = currentV;
                newList.add(tripletList.get(i));
            }
        }

        if(tripletList.size() != newList.size())
            tripletList = newList;
        if(tripletList.size()==1) {
            Part one = new Part();
            one.simpleAdd(Constraint.trueConstraint(this.clockSet));
            outcome.add(new Triplet<>(one,tripletList.get(0).p1(),tripletList.get(0).p2()));
            return outcome;
        }
        List<Constraint> A_list = new LinkedList<>();
        List<ClockValuation> v_list = new LinkedList<>();
        A_list.add(null);
        v_list.add(null);
        Part U_0 = new Part();
        for(Triplet<ClockValuation,Location,Set<Clock>> triplet : tripletList) {
            ClockValuation v = triplet.getP0();
            Constraint A = this.generateA(v, this.K_map);
            A_list.add(A);
            v_list.add(v);
            if(A != null)
                U_0.simpleAdd(A);
        }
        U_0.simplify();
        List<Part> U_list = new LinkedList<>();
        List<Constraint> U_constraint_list = new LinkedList<>();
        U_list.add(U_0);
        U_constraint_list.add(null);
        for(int i=0;i<tripletList.size();i++){
            Part U_i = new Part();
            ClockValuation v = tripletList.get(i).p0();
            Constraint U_constraint = v.toBaseConstraint();
            U_i.simpleAdd(U_constraint);
            U_list.add(U_i);
            U_constraint_list.add(U_constraint);
        }
        List<Part> W_list = new LinkedList<>();
        W_list.add(null);
        for(int i=1;i<U_list.size();i++){
            Part W_i = U_list.get(i).copy();
            for(int j=i+1;j<U_list.size();j++){
                W_i.simpleMinusViaFloyd(U_list.get(j));
            }
            W_i.simpleMinusViaFloyd(U_0);
            W_list.add(W_i);
        }
        Map<Clock, RealExpr> expr_map = new HashMap<>();
        for(Clock clock : this.clockSet){
            expr_map.put(clock, this.ctx.mkRealConst(clock.getName()));
        }

        List<Part> I_list = new LinkedList<>();
        I_list.add(null);
        for(int i=1;i<W_list.size();i++){
            Part I_i = W_list.get(i).copy();
            if(A_list.get(i)!=null)
                I_i.simpleAdd(A_list.get(i));
            I_list.add(I_i);
        }

        for (int i = 1; i < I_list.size(); i++) {
            if (A_list.get(i) != null) {
                continue;
            }
            ClockValuation v_i = v_list.get(i);
            Constraint region_i = v_i.toRegionConstraint(this.K_map);
            for (int j = i + 1; j < I_list.size(); j++) {
                if (A_list.get(j) != null) {
                    continue;
                }
                ClockValuation v_j = v_list.get(j);
                if (!U_constraint_list.get(i).verbatimEquals(U_constraint_list.get(j))) {
                    continue;
                }
                if (v_i.regionEqual(v_j, this.K_map)) {
                    continue;
                }
                Part I_i = I_list.get(i);
                if (I_i.isLogicalEmpty(expr_map, ctx)) {
                    I_i.simpleAdd(region_i);
                }
                I_list.get(j).simpleMinusViaFloyd(region_i);
            }
        }

        for(int i=1;i<I_list.size();i++){
            outcome.add(new Triplet<>(I_list.get(i),tripletList.get(i-1).p1(),tripletList.get(i-1).p2()));
        }

        return outcome;
    }


    private Constraint generateA(ClockValuation v, Map<Clock,Value> K_map){
        for(Map.Entry<Clock, Value> entry : v){
            Clock clock = entry.getKey();
            Value value = entry.getValue();
            Value k = K_map.get(clock);
            if(Value.gt(value, k))
                return v.toRegionConstraint(K_map);
        }
        return null;
    }
}
