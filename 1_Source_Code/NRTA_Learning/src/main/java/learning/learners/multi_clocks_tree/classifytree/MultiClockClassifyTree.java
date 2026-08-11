package learning.learners.multi_clocks_tree.classifytree;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import exception.ValueExceptions;
import hu.webarticum.treeprinter.printer.listing.ListingTreePrinter;
import language.actions.LogicalTimedAction;
import language.actions.ResetLogicalTimedAction;
import language.words.LogicalTimedWord;
import language.words.RegionTimedWord;
import language.words.ResetLogicalTimedWord;
import learning.learners.multi_clocks_tree.BoolMatrix;
import learning.learners.multi_clocks_tree.TreeHelper;
import lombok.Getter;
import run.ClockValuation;
import ta.*;
import tools.Tuple.Triplet;

import java.util.*;

public class MultiClockClassifyTree {
    private final TreeHelper helper;
    private final List<Clock> clockSet;
    private final List<Action> actionSet;
    private final String hypName;
    private final List<Clock> h_clockSet;
    private final Map<Clock,Clock> clockMap_th;
    private final TreeNode root;
    private final Context ctx;

    private final Map<TreeNodeLeaf, Map<ResetLogicalTimedAction, TreeNodeLeaf>> leafTransMap;
    @Getter
    private Map<TreeNodeLeaf, Location> leafLocationMap;
    @Getter
    private Map<Location, TreeNodeLeaf> locationLeafMap;
    @Getter
    private TA internalHypothesis;

    @Getter
    private final ResetLogicalTimedWord emptyResetLogicalWord = new ResetLogicalTimedWord();
    @Getter
    private final LogicalTimedWord emptyLogicalWord = new LogicalTimedWord();
    @Getter
    private final RegionTimedWord emptyRegionWord = new RegionTimedWord();

    public MultiClockClassifyTree(List<Clock> clockSet, List<Action> actionSet, String hypName, Map<Clock, Value> kMap, List<Clock> h_clockSet, Map<Clock, Clock> clockMap_th, Context ctx, TreeHelper helper) {
        this.clockSet = clockSet;
        this.actionSet = actionSet;
        this.hypName = hypName;
        this.h_clockSet = h_clockSet;
        this.clockMap_th = clockMap_th;
        this.ctx = ctx;
        this.helper = helper;
        this.root = new TreeNodeInside(emptyRegionWord, null, BoolMatrix.EMPTY);
        this.leafTransMap = new HashMap<>();
    }

    public TreeNodeLeaf sift(ResetLogicalTimedWord resetLogicalWord) {
        TreeNodeInside parent;
        TreeNode curNode = this.root;
        BoolMatrix boolMatrix;
        do {
            parent = (TreeNodeInside) curNode;
            boolMatrix = this.helper.getResetAndAccept(resetLogicalWord, parent.getRegionWord());
            curNode = parent.findChildren(boolMatrix);
        } while (curNode instanceof TreeNodeInside);
        if (curNode instanceof TreeNodeLeaf) {
            return (TreeNodeLeaf) curNode;
        } else {
            assert curNode == null;
            TreeNodeLeaf newLeaf = parent.addLeaf(boolMatrix, resetLogicalWord);
            this.nto(newLeaf);
            return newLeaf;
        }
    }

    public LeafTrans tran(TreeNodeLeaf leafNode, Action action, ClockValuation clockValuation) {
        ResetLogicalTimedWord leafWord = leafNode.getResetLogicalWord();
        LogicalTimedWord logicalWord = leafWord.toLogicalTimedWord(true);
        logicalWord.attend(new LogicalTimedAction(action, clockValuation));
        ResetLogicalTimedWord resetAddedWord = this.helper.raiseResetInformationQuery(logicalWord);
        assert resetAddedWord.getLength() == leafWord.getLength() + 1;
        for (int i = 0; i < leafWord.getLength(); i++) assert resetAddedWord.getTimedAction(i).equals(resetAddedWord.getTimedAction(i));

        TreeNodeLeaf targetNode = this.sift(resetAddedWord);
        return new LeafTrans(leafNode, targetNode, resetAddedWord.getLast());
    }

    public void nto(TreeNodeLeaf newLeaf) {
        assert !this.leafTransMap.containsKey(newLeaf);
        Map<ResetLogicalTimedAction, TreeNodeLeaf> newAction2LeafNodeMap = new HashMap<>();
        ClockValuation nextClockValuation = new ClockValuation(this.clockSet);
        for (Action action : this.actionSet) {
            LeafTrans leafTrans = this.tran(newLeaf, action, nextClockValuation);
            newAction2LeafNodeMap.put(leafTrans.getEdgeValue(), leafTrans.getTargetNode());
        }
        this.leafTransMap.put(newLeaf, newAction2LeafNodeMap);
    }

    public void split(TreeNodeLeaf rGammaNode, ResetLogicalTimedWord rBeta, RegionTimedWord d) {
        ResetLogicalTimedWord rGamma = rGammaNode.getResetLogicalWord();
        BoolMatrix rGammaBoolK = this.helper.getResetAndAccept(rGamma, d);
        BoolMatrix rBetaBoolK = this.helper.getResetAndAccept(rBeta, d);
        assert !rGammaBoolK.equals(rBetaBoolK);

        TreeNodeInside parent = rGammaNode.getParent();
        TreeNodeInside newInsideNode = new TreeNodeInside(d, parent, rGammaNode.getLastBoolK());
        parent.replaceChild(rGammaNode, newInsideNode);

        newInsideNode.addLeaf(rGammaBoolK, rGammaNode);
        TreeNodeLeaf newLeaf = newInsideNode.addLeaf(rBetaBoolK, rBeta);
        this.nto(newLeaf);
        this.refreshLeafNodeTrans(rGammaNode);
    }

    public void refreshLeafNodeTrans(TreeNodeLeaf triggerLeaf) {
        List<LeafTrans> transToRefresh = new ArrayList<>();
        for (TreeNodeLeaf sourceLeafNode : this.leafTransMap.keySet()) {
            Map<ResetLogicalTimedAction, TreeNodeLeaf> singleLeafTrans = this.leafTransMap.get(sourceLeafNode);
            for (ResetLogicalTimedAction resetLogicalAction : singleLeafTrans.keySet()) {
                TreeNodeLeaf targetLeafNode = singleLeafTrans.get(resetLogicalAction);
                if (targetLeafNode.equals(triggerLeaf)) {
                    transToRefresh.add(new LeafTrans(sourceLeafNode, targetLeafNode, resetLogicalAction));
                }
            }
        }
        transToRefresh.sort(Comparator.comparing(TreeNodeTransition::toString));
        for (LeafTrans newTrans : transToRefresh) {
            TreeNodeLeaf sourceLeafNode = newTrans.getSourceNode();
            ResetLogicalTimedAction resetLogicalAction = newTrans.getEdgeValue();
            ResetLogicalTimedWord wordToSift = sourceLeafNode.getResetLogicalWord().commonClone();
            wordToSift.attend(resetLogicalAction);
            TreeNodeLeaf newTargetLeafNode = this.sift(wordToSift);
            this.leafTransMap.get(newTrans.getSourceNode()).put(resetLogicalAction, newTargetLeafNode);
        }
    }

    public void addLeafTrans(TreeNodeLeaf sourceLeaf, ResetLogicalTimedAction newEdgeValue) {
        ResetLogicalTimedWord wordToSift = sourceLeaf.getResetLogicalWord().commonClone();
        wordToSift.attend(newEdgeValue);
        TreeNodeLeaf targetLeaf = this.sift(wordToSift);
        this.addLeafTrans(sourceLeaf, newEdgeValue, targetLeaf);
    }
    public void addLeafTrans(TreeNodeLeaf sourceLeaf, ResetLogicalTimedAction newEdgeValue, TreeNodeLeaf targetLeaf) {
        this.leafTransMap.get(sourceLeaf).put(newEdgeValue, targetLeaf);
    }

    @Override
    public String toString() {
        String treeString = new ListingTreePrinter().stringify(this.root);
        String leafTransString = "";
        try {
            leafTransString = this.getLeafTransStr();
        } catch (Exception ignored) {

        }
        return treeString + leafTransString;
    }

    public String getLeafTransStr() {
        StringBuilder sb = new StringBuilder();
        for (TreeNodeLeaf sourceNode : this.leafTransMap.keySet()) {
            Map<ResetLogicalTimedAction, TreeNodeLeaf> outEdges = this.leafTransMap.get(sourceNode);
            for (ResetLogicalTimedAction edgeValue : outEdges.keySet()) {
                TreeNodeLeaf targetNode = outEdges.get(edgeValue);
                sb.append(this.leafLocationMap.get(sourceNode).getName()).append(String.format("{ %s }", sourceNode))
                  .append(" == ").append(edgeValue).append(" ==> ")
                  .append(this.leafLocationMap.get(targetNode).getName()).append(String.format("{ %s }", targetNode))
                  .append('\n');
            }
        }
        return sb.toString();
    }

    public TA generateHypothesis() {
        TA hypothesis = new TA(this.hypName);
        for (Clock clock : this.h_clockSet) hypothesis.addClock(clock);
        for (Action action : this.actionSet) hypothesis.addAction(action);
        Map<TreeNodeLeaf, Location> leafLocationMap = this.generateLeaf2Location(hypothesis, this.leafTransMap.keySet());
        Map<Location,Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>>> originalTransInfo = this.getOriginalTransInfo(leafLocationMap);
        this.generateTransitions(hypothesis, originalTransInfo);
        this.internalHypothesis = hypothesis;
        return hypothesis;
    }

    public Map<TreeNodeLeaf, Location> generateLeaf2Location(TA hyp, Set<TreeNodeLeaf> leafSet) {
        int locIdx = 0;
        boolean isAccepted;
        Map<TreeNodeLeaf, Location> leafLocationMap = new HashMap<>();
        Map<Location, TreeNodeLeaf> locationLeafMap = new HashMap<>();
        for (TreeNodeLeaf treeNodeLeaf : leafSet) {
            isAccepted = this.helper.getResetAndAccept(treeNodeLeaf.getResetLogicalWord(), this.emptyRegionWord).getLast();
            Location location = new Location("q" + locIdx, isAccepted, Constraint.TRUE, hyp);
            leafLocationMap.put(treeNodeLeaf, location);
            locationLeafMap.put(location, treeNodeLeaf);
            if (treeNodeLeaf.getResetLogicalWord().isEmpty()) {
                hyp.setQ0(location);
            }
            locIdx++;
        }
        this.leafLocationMap = leafLocationMap;
        this.locationLeafMap = locationLeafMap;
        return leafLocationMap;
    }

    private Set<Clock> replace(Set<Clock> reset){
        Set<Clock> outcome = new HashSet<>();
        for(Clock clock : reset){
            Clock new_clock = this.clockMap_th.get(clock);
            outcome.add(new_clock);
        }
        return outcome;
    }
    private Map<Location, Map<Action, List<Triplet<ClockValuation, Location, Set<Clock>>>>> getOriginalTransInfo(Map<TreeNodeLeaf, Location> leafLocationMap) {
        Map<Location, Map<Action, List<Triplet<ClockValuation, Location, Set<Clock>>>>> originalTransInfo = new HashMap<>();
        for (Map.Entry<TreeNodeLeaf, Map<ResetLogicalTimedAction, TreeNodeLeaf>> entry : this.leafTransMap.entrySet()) {
            Location source = leafLocationMap.get(entry.getKey());
            assert !originalTransInfo.containsKey(source);
            Map<Action, List<Triplet<ClockValuation, Location, Set<Clock>>>> newOutTransMap = new HashMap<>();
            Map<ResetLogicalTimedAction, TreeNodeLeaf> oldOutTransMap = entry.getValue();
            for (Map.Entry<ResetLogicalTimedAction, TreeNodeLeaf> oldTransEntry : oldOutTransMap.entrySet()) {
                ResetLogicalTimedAction resetLogicalAction = oldTransEntry.getKey();
                Action action = resetLogicalAction.getAction();
                if (!newOutTransMap.containsKey(action)) {
                    newOutTransMap.put(action, new ArrayList<>());
                }
                List<Triplet<ClockValuation, Location, Set<Clock>>> tripletList = newOutTransMap.get(action);
                tripletList.add(new Triplet<>(
                        resetLogicalAction.getClockValuation(),
                        leafLocationMap.get(oldTransEntry.getValue()),
                        this.replace(resetLogicalAction.getReset_clocks())
                ));
            }
            originalTransInfo.put(source, newOutTransMap);
        }
        return originalTransInfo;
    }
    public void generateTransitions(TA hypothesis, Map<Location,Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>>> OriginalTransInfo){
        for(Map.Entry<Location,Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>>> entry : OriginalTransInfo.entrySet()){
            Location source = entry.getKey();
            Map<Action, List<Triplet<ClockValuation,Location,Set<Clock>>>> actionListMap = entry.getValue();
            for(Map.Entry<Action,List<Triplet<ClockValuation,Location,Set<Clock>>>> entry1 : actionListMap.entrySet()){
                Action action = entry1.getKey();
                List<Triplet<ClockValuation,Location,Set<Clock>>> tripletList = entry1.getValue();
                List<Triplet<Part,Location,Set<Clock>>> partition_data = this.partition_function(tripletList);
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
    public List<Triplet<Part,Location,Set<Clock>>> partition_function(List<Triplet<ClockValuation,Location,Set<Clock>>> tripletList) {
        if(tripletList.isEmpty())
            throw new RuntimeException(ValueExceptions.UNABLE_TO_PARTITION_NULL_VALUE);

        List<Triplet<Part,Location,Set<Clock>>> outcome = new LinkedList<>();
        tripletList.sort(Comparator.comparing(Triplet::p0));
        List<Triplet<ClockValuation,Location,Set<Clock>>> newList = new LinkedList<>();
        ClockValuation lastV = tripletList.get(0).getP0();
        newList.add(tripletList.get(0));
        for(int i=1;i<tripletList.size();i++){
            ClockValuation currentV = tripletList.get(i).getP0();
            if(!lastV.regionEqual(currentV, ResetLogicalTimedWord.Kmap)){
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
            Constraint A = this.generate_A(v, ResetLogicalTimedWord.Kmap);
            A_list.add(A);
            v_list.add(v);
            if(A != null)
                U_0.simpleAdd(A);
        }
        U_0.simplify();
        List<Part> U_list = new LinkedList<>();
        U_list.add(U_0);
        for(int i=0;i<tripletList.size();i++){
            Part U_i = new Part();
            ClockValuation v = tripletList.get(i).p0();
            U_i.simpleAdd(v.toBaseConstraint());
            U_list.add(U_i);
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
        List<Constraint> empty_list = new LinkedList<>();
        Set<Integer> empty_index_set = new HashSet<>();
        Part I_i;
        for (int i = 1; i < I_list.size(); i++) {
            I_i = I_list.get(i);
            if (I_i.isLogicalEmpty(expr_map, ctx)) {
                ClockValuation v_i = v_list.get(i);
                Constraint region_v = v_i.toRegionConstraint(ResetLogicalTimedWord.Kmap);
                I_i.simpleAdd(region_v);
                empty_list.add(region_v);
                empty_index_set.add(i);
            }
        }
        for (int i = 1; i < I_list.size(); i++){
            if (!empty_index_set.contains(i)){
                I_i = I_list.get(i);
                for(Constraint region : empty_list){
                    I_i.simpleMinusViaFloyd(region);
                }
            }
        }
        for(int i=1;i<I_list.size();i++){
            outcome.add(new Triplet<>(I_list.get(i),tripletList.get(i-1).p1(),tripletList.get(i-1).p2()));
        }

        return outcome;
    }


    private Constraint generate_A(ClockValuation v, Map<Clock,Value> K_map){
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
