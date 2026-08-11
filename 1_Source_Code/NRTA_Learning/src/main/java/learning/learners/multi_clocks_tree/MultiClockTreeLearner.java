package learning.learners.multi_clocks_tree;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import language.actions.LogicalTimedAction;
import language.actions.RegionTimedAction;
import language.actions.ResetLogicalTimedAction;
import language.words.LogicalTimedWord;
import language.words.RegionTimedWord;
import language.words.ResetLogicalTimedWord;
import learning.learners.multi_clocks_tree.classifytree.MultiClockClassifyTree;
import learning.learners.multi_clocks_tree.classifytree.TreeNodeLeaf;
import learning.teachers.Teacher;
import run.Trace;
import ta.*;
import tools.Tuple.Triplet;

import java.util.*;

public class MultiClockTreeLearner implements TreeHelper {
    private final List<Clock> tarClockSet;
    private final List<Action> actionSet;
    private final String hypName;
    private final Map<Clock, Value> kMap;
    private final Context ctx;
    private final MultiClockClassifyTree classifyTree;
    private final Teacher teacher;
    private TA internalHyp;
    private final List<Clock> hypClockSet;
    private final Map<Clock, Clock> clockMap_th;

    private final Map<String, Map<RegionTimedWord, BoolMatrix>> cacheOfGetResetAndAccept = new HashMap<>();
    private final Map<String, ResetLogicalTimedWord> cacheOfResetInfoQuery = new HashMap<>();

    public MultiClockTreeLearner(List<Clock> tarClockSet, List<Action> actionSet, String hypName, Map<Clock, Value> kMap, Teacher teacher, Context ctx) {
        this.tarClockSet = tarClockSet;
        this.actionSet = actionSet;
        this.hypName = hypName;
        this.kMap = kMap;
        this.ctx = ctx;
        this.hypClockSet = new ArrayList<>();
        this.clockMap_th = new HashMap<>();
        for (Clock targetClock : tarClockSet) {
            String h_name = "h_" + targetClock.getName();
            RealExpr h_smt_exp = this.ctx.mkRealConst(h_name);
            Clock h_clock = new Clock(h_name, hypName, h_smt_exp);
            this.hypClockSet.add(h_clock);
            this.clockMap_th.put(targetClock, h_clock);
        }
        this.classifyTree = new MultiClockClassifyTree(this.tarClockSet, this.actionSet, this.hypName, this.kMap, this.hypClockSet, this.clockMap_th, this.ctx, this);
        this.teacher = teacher;
    }

    public void initTree() {
        this.classifyTree.sift(this.classifyTree.getEmptyResetLogicalWord());
        this.generateHypothesis();
    }

    @Override
    public BoolMatrix getResetAndAccept(ResetLogicalTimedWord rGamma, RegionTimedWord d) {
        LogicalTimedWord rLogicalWord = rGamma.toLogicalTimedWord(true);
        String cacheKey = rLogicalWord.toString();
        Map<RegionTimedWord, BoolMatrix> tmpCacheMap = this.cacheOfGetResetAndAccept.get(cacheKey);
        if (tmpCacheMap != null) {
            BoolMatrix cacheResult = tmpCacheMap.get(d);
            if (cacheResult != null) {
                return cacheResult;
            }
        } else {
            tmpCacheMap = new HashMap<>();
            this.cacheOfGetResetAndAccept.put(rGamma.toString(), tmpCacheMap);
        }
        BoolMatrix boolK = this.getResetAndAcceptCore(rGamma, d);
        tmpCacheMap.put(d, boolK);
        return boolK;
    }

    public BoolMatrix getResetAndAcceptCore(ResetLogicalTimedWord rGamma, RegionTimedWord d) {
        LogicalTimedWord dLogicalWord = d.buildLogicalTimedWord();
        LogicalTimedWord tmpLogicalWord = rGamma.toLogicalTimedWord(true);
        tmpLogicalWord.connect(dLogicalWord);
        ResetLogicalTimedWord finalResetWord = this.raiseResetInformationQuery(tmpLogicalWord);
        boolean accepted = this.raiseMembershipQuery(finalResetWord);
        assert finalResetWord.getLength() == rGamma.getLength() + d.getLength();
        BoolMatrix boolMatrix = new BoolMatrix(finalResetWord.subWord(rGamma.getLength(), finalResetWord.getLength()), this.tarClockSet, d.getLength(), accepted);
        return boolMatrix;
    }

    public ResetLogicalTimedWord getValidSuccessor(ResetLogicalTimedWord rGamma, LogicalTimedWord dLogical) {
        RegionTimedWord dRegion = new RegionTimedWord(dLogical);
        BoolMatrix boolMatrix = this.getResetAndAccept(rGamma, dRegion);
        ResetLogicalTimedWord validSuccessor = new ResetLogicalTimedWord();
        for (int i = 0; i < dLogical.getLength(); i++) {
            validSuccessor.attend(dLogical.getTimedAction(i), boolMatrix.getResetClockSet(i, this.tarClockSet));
        }
        return validSuccessor;
    }
    public ResetLogicalTimedAction getValidSuccessor(ResetLogicalTimedWord rGamma, LogicalTimedAction dLogical) {
        RegionTimedAction dRegion = new RegionTimedAction(dLogical);
        RegionTimedWord regionTimedWord = new RegionTimedWord();
        regionTimedWord.attend(dRegion);
        BoolMatrix boolMatrix = this.getResetAndAccept(rGamma, regionTimedWord);
        return new ResetLogicalTimedAction(dLogical, boolMatrix.getResetClockSet(0, this.tarClockSet));
    }

    @Override
    public boolean raiseMembershipQuery(LogicalTimedWord logicalTimedWord) {
        return this.teacher.MembershipQuery(logicalTimedWord);
    }

    public boolean raiseMembershipQuery(ResetLogicalTimedWord resetLogicalTimedWord) {
        return this.teacher.MembershipQuery(resetLogicalTimedWord.toLogicalTimedWord());
    }

    @Override
    public ResetLogicalTimedWord raiseResetInformationQuery(LogicalTimedWord logicalWord) {
        String cacheKey = logicalWord.toString();
        ResetLogicalTimedWord cacheResult = this.cacheOfResetInfoQuery.getOrDefault(cacheKey, null);
        if (cacheResult != null) {
            return cacheResult;
        } else {
            ResetLogicalTimedWord resetLogicalWord = this.teacher.ResetInformationQuery(logicalWord).evidence();
            this.cacheOfResetInfoQuery.put(cacheKey, resetLogicalWord);
            return resetLogicalWord;
        }
    }

    public void refineCounterExample(ResetLogicalTimedWord counterExample, boolean isPosOrNeg) {
        boolean refined = this.refineHypothesisInternal(counterExample, isPosOrNeg);
        while (!refined) {
            this.generateHypothesis();
            refined = this.refineHypothesisInternal(counterExample, isPosOrNeg);
        }
    }

    public boolean refineHypothesisInternal(ResetLogicalTimedWord ctx, boolean isPosOrNeg) {
        ResetLogicalTimedWord ctxClockReplaced = ctx.replaceHypothesisClocks(this.clockMap_th);
        LogicalTimedWord logicalCtx = ctx.toLogicalTimedWord(true);
        LogicalTimedWord hypLogicalCtx = ctxClockReplaced.toLogicalTimedWord(true);
        NTA ntaHyp = new NTA(this.internalHyp);
        for (int idx = 0; idx < ctx.getLength(); idx++) {
            LogicalTimedWord prefixLogicalCtx = hypLogicalCtx.subWord(0, idx + 1);
            ResetLogicalTimedWord prefixResetLogicalCtx = ctxClockReplaced.subWord(0, idx + 1);
            Triplet<Boolean, ResetLogicalTimedWord, Trace> execResult = ntaHyp.addResetInformation(prefixLogicalCtx);
            assert execResult.getP1().getLength() == prefixLogicalCtx.getLength();
            Trace trace = execResult.getP2();
            Transition lastTrans = trace.getSyncTrans(trace.getLength() - 1).getTransitions().get(this.internalHyp);
            Location lastLoc = lastTrans.getTarget(), last2Loc = lastTrans.getSource();
            ResetLogicalTimedWord execWord = execResult.getP1();
            TreeNodeLeaf leafNodeOfLast2Loc = this.classifyTree.getLocationLeafMap().get(last2Loc);
            if (!execWord.equals(prefixResetLogicalCtx)) {
                ResetLogicalTimedAction validSuccessor = this.getValidSuccessor(leafNodeOfLast2Loc.getResetLogicalWord(), ctx.getTimedAction(idx));
                this.classifyTree.addLeafTrans(leafNodeOfLast2Loc, validSuccessor);
                return false;
            }
            LogicalTimedWord suffixLogicalCtx = logicalCtx.subWord(idx + 1, logicalCtx.getLength());
            RegionTimedWord suffixRegionCtx = new RegionTimedWord(suffixLogicalCtx);
            ResetLogicalTimedWord suffixResetLogicalCtx = ctx.subWord(idx + 1, ctx.getLength());
            ResetLogicalTimedWord leafPrefix = this.classifyTree.getLocationLeafMap().get(lastLoc).getResetLogicalWord();
            BoolMatrix prefixBoolK = this.getResetAndAccept(leafPrefix, suffixRegionCtx);
            BoolMatrix originBoolK = new BoolMatrix(suffixResetLogicalCtx, this.tarClockSet, suffixResetLogicalCtx.getLength(), isPosOrNeg);
            if (!prefixBoolK.equals(originBoolK)) {
                ResetLogicalTimedWord wordToSift = leafNodeOfLast2Loc.getResetLogicalWord().commonClone();
                ResetLogicalTimedAction validSuccessor = getValidSuccessor(wordToSift, ctx.getTimedAction(idx));
                wordToSift.attend(validSuccessor);
                TreeNodeLeaf siftResultOfLast2LocAttendLastAction = this.classifyTree.sift(wordToSift);
                if (!siftResultOfLast2LocAttendLastAction.equals(this.classifyTree.getLocationLeafMap().get(lastLoc))) {
                    this.classifyTree.addLeafTrans(leafNodeOfLast2Loc, validSuccessor, siftResultOfLast2LocAttendLastAction);
                } else {
                    this.classifyTree.split(siftResultOfLast2LocAttendLastAction, wordToSift, suffixRegionCtx);
                }
                return false;
            }
        }
        return true;
    }

    public TA generateHypothesis() {
        this.internalHyp = this.classifyTree.generateHypothesis();
        for (Map.Entry<Clock, Value> entry : this.internalHyp.getK().entrySet()) {
            ResetLogicalTimedWord.addKMap(entry.getKey(), entry.getValue());
        }
        return this.internalHyp;
    }

    public void printClassifyTree() {
        System.out.println(this.classifyTree);
    }
}
