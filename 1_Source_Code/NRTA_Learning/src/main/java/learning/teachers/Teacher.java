package learning.teachers;

import com.microsoft.z3.Context;
import exception.CheckingExceptions;
import language.words.LogicalTimedWord;
import language.words.ResetDelayTimedWord;
import language.words.ResetLogicalTimedWord;
import lombok.Getter;
import lombok.Setter;
import run.Trace;
import ta.Action;
import ta.Clock;
import ta.NTA;
import ta.TA;
import ta.Value;
import tools.FormatOutput.Color;
import tools.FormatOutput.ColoredFront;
import tools.FormatOutput.Type;
import tools.Tuple.Reply;
import tools.Tuple.Triplet;
import verify.Verifiers.ZoneVerifier;

import java.util.Map;

public class Teacher {
    public static final int POSITIVE_FIRST = 0;
    public static final int SHORTEST_FIRST = 1;

    private final ZoneVerifier dbmVerifier;
    @Getter
    private final NTA targetNTA;
    @Getter
    private final Context ctx;
    @Setter
    private int equivalence_strategy = SHORTEST_FIRST;

    private volatile int rqCnt = 0, mqCnt = 0, eqCnt = 0;

    public Teacher(NTA target_nta, Context ctx) {
        this.targetNTA = target_nta.makeComplete(ctx);
        this.dbmVerifier = new ZoneVerifier(ctx);
        this.ctx = ctx;
    }

    public Map<Clock,Value> get_K_Map(){
        return this.targetNTA.getK_map();
    }

    /**
     * false indicates that the word is illegal, i.e., time reversal occurs.
     * @param wl the logical timed word
     * @return a reply containing whether the word is legal and the reset logical timed word
     */
    public Reply<ResetLogicalTimedWord> ResetInformationQuery(LogicalTimedWord wl) {
        rqCnt++;
        return new Reply<>(true, this.targetNTA.addResetInformationSimple(wl));
    }

    public boolean MembershipQuery(LogicalTimedWord rl) {
        mqCnt++;
        return this.targetNTA.ifContain(rl).p0();
    }

    /**
     * p2 is false, indicating a negative counterexample that needs to be removed.
     * p2 is true, indicating a positive counterexample that needs to be added.
     * The counterexample is always the case running in the target.
     * @param hypothesis the hypothesis automaton
     * @return a triplet containing whether equivalent, the counterexample, and whether positive
     */
    public Triplet<Boolean,ResetLogicalTimedWord,Boolean> EquivalenceQuery(NTA hypothesis) {
        eqCnt++;
        if(this.equivalence_strategy == Teacher.SHORTEST_FIRST) {
            Triplet<Boolean, Trace, Boolean> outcome = dbmVerifier.checkLanguageEquivalence(this.targetNTA, hypothesis);
            if (outcome.p0()) {
                return new Triplet<>(true, null, null);
            }
            ResetDelayTimedWord rdw = outcome.p1().getResetDelayTimedWord(ctx);
            ResetLogicalTimedWord rlw = rdw.toResetLogicalTimedWord(this.targetNTA.getAllClocks());
            return new Triplet<>(false, rlw, outcome.p2());
        } else if(this.equivalence_strategy == Teacher.POSITIVE_FIRST) {
            Triplet<Boolean, Trace, Boolean> missingPositive = dbmVerifier.checkLanguageInclude(hypothesis, this.targetNTA);
            if (!missingPositive.p0()) {
                // include(hyp, target) violated => target accepts but hyp rejects => positive counterexample.
                ResetDelayTimedWord rdw = missingPositive.p1().getResetDelayTimedWord(ctx);
                ResetLogicalTimedWord rlw = rdw.toResetLogicalTimedWord(this.targetNTA.getAllClocks());
                return new Triplet<>(false, rlw, true);
            }

            Triplet<Boolean, Trace, Boolean> extraNegative = dbmVerifier.checkLanguageInclude(this.targetNTA, hypothesis);
            if (extraNegative.p0()) {
                return new Triplet<>(true, null, null);
            }
            // include(target, hyp) violated => target rejects but hyp accepts => negative counterexample.
            ResetDelayTimedWord rdw = extraNegative.p1().getResetDelayTimedWord(ctx);
            ResetLogicalTimedWord rlw = rdw.toResetLogicalTimedWord(this.targetNTA.getAllClocks());
            return new Triplet<>(false, rlw, false);
        } else {
            throw new RuntimeException(CheckingExceptions.UNSUPPORTED_EQUIVALENCE_CHECKING_METHOD);
        }
    }

    public void print_target(){
        System.out.println(
                ColoredFront.dealtString(
                        "****************** LEARNING TARGET ******************",
                        Color.Cyan,
                        Type.Common)
        );
        for(TA ta : this.targetNTA.getTA_list())
            System.out.println(ta.getMainDetail(true));
    }

    public Triplet<Integer, Integer, Integer> consumeQueryCount() {
        Triplet<Integer, Integer, Integer> result = new Triplet<>(this.rqCnt, this.mqCnt, this.eqCnt);
        this.rqCnt = 0;
        this.mqCnt = 0;
        this.eqCnt = 0;
        return result;
    }

    /** Return the current counters without resetting them. */
    public Triplet<Integer, Integer, Integer> snapshotQueryCount() {
        return new Triplet<>(this.rqCnt, this.mqCnt, this.eqCnt);
    }
}
