package tools;

import com.microsoft.z3.*;
import lombok.Getter;

import java.math.BigInteger;

public class ExtendSolver extends Context{
    private static final IdCounter id_counter = new IdCounter();
    public static int Z3_RANDOM_SEED = 1;
    @Getter
    private final int id;
    public final BitVecExpr bvTrue;
    public final BitVecExpr bvFalse;
    public final IntExpr intOne;
    public final IntExpr intZero;

    public ExtendSolver(){
        // super(Map.of("model", "true"));
        super();
        this.id = id_counter.createId();
        this.bvTrue = this.mkBV(1, 1);
        this.bvFalse = this.mkBV(0, 1);
        this.intOne = this.mkInt(1);
        this.intZero = this.mkInt(0);
    }

    /*****
     * OR all elements in the array together
     * @param array
     * @return
     */
    public BoolExpr mkArrayOr(BoolExpr[] array){
        assert array != null;
        BoolExpr outcome;
        if(array.length==1)
            outcome = array[0];
        else
            outcome = this.mkOr(array);
        return outcome;
    }

    /*****
     * AND all elements in the array together
     * @param array
     * @return
     */
    public BoolExpr mkArrayAnd(BoolExpr[] array){
        assert array != null;
        BoolExpr outcome;
        if(array.length==1)
            outcome = array[0];
        else
            outcome = this.mkAnd(array);
        return outcome;
    }

    /****
     * Extract value corresponding to bvExpr from model
     * @param bvExpr
     * @param model
     * @return
     */
    public BigInteger getBvValue(BitVecExpr bvExpr, Model model){
        Expr xVal = model.evaluate(bvExpr, true); // true means allow free assignment, so try-catch is not needed
        BitVecNum bitVecNum = (BitVecNum) xVal;
        return bitVecNum.getBigInteger();
    }

    /*********
     * Non-zero truth value is false, otherwise true
     * @param bvExpr
     * @param model
     * @return
     */
    public boolean getBvTruthValue(BitVecExpr bvExpr, Model model){
        assert bvExpr.getSortSize() == 1;
        Expr xVal = model.evaluate(bvExpr, false);
        BitVecNum bitVecNum = (BitVecNum) xVal;
        return bitVecNum.getBigInteger().intValue() != 0;
    }

    public static void applyRandomSeed(Context ctx, Solver solver) {
        try {
            Params params = ctx.mkParams();
            params.add("random_seed", Z3_RANDOM_SEED);
            solver.setParameters(params);
        } catch (Z3Exception ignored) {
            // Some Z3 builds do not expose random_seed on solver params.
        }
    }

    public static void applyRandomSeed(Context ctx, Optimize optimize) {
        try {
            Params params = ctx.mkParams();
            params.add("priority", "lex");
            optimize.setParameters(params);
        } catch (Z3Exception ignored) {
            // Some Z3 builds do not expose optimize priority params.
        }
    }
}
