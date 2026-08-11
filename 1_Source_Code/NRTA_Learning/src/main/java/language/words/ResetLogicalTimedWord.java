package language.words;

import exception.TypeExceptions;
import language.actions.DelayTimedAction;
import language.actions.LogicalTimedAction;
import language.actions.ResetLogicalTimedAction;
import run.ClockValuation;
import run.Region;
import ta.Action;
import ta.Clock;
import ta.Value;
import tools.Tuple.Pair;

import java.util.*;

public class ResetLogicalTimedWord extends TimedWord<ResetLogicalTimedAction, ResetLogicalTimedWord>{
    public static Map<Clock, Value> Kmap = null;
    public ResetLogicalTimedWord(){
        super();
    }
    public ResetLogicalTimedWord(List<ResetLogicalTimedAction> list){
        super(list);
    }

    public static void setKMap(Map<Clock, Value> Kmap){
        ResetLogicalTimedWord.Kmap = Kmap;
//        ResetLogicalTimedWord.Kmap = new HashMap<>();
//        for (Clock clock : Kmap.keySet()) {
//            ResetLogicalTimedWord.Kmap.put(clock, Value.INFINITE);
//        }
    }
    public static void addKMap(Clock hypClock, Value hypClockV) {
        ResetLogicalTimedWord.Kmap.put(hypClock, hypClockV);
//        ResetLogicalTimedWord.Kmap.put(hypClock, Value.INFINITE);
    }


    @Override
    public ResetLogicalTimedWord projection(Set<Action> target_alphabet) {
        ResetLogicalTimedWord outcome = new ResetLogicalTimedWord();
        for(ResetLogicalTimedAction resetLogicalTimedAction : this.timedActions){
            Action action = resetLogicalTimedAction.getAction();
            if(target_alphabet.contains(action)){
                outcome.attend(
                        new ResetLogicalTimedAction(
                                action,
                                resetLogicalTimedAction.getClockValuation(),
                                resetLogicalTimedAction.getReset_clocks()
                        )
                );
            }
        }
        return outcome;
    }

    public LogicalTimedWord toLogicalTimedWord(){
        return this.toLogicalTimedWord(false);
    }

    public LogicalTimedWord toLogicalTimedWord(boolean if_rebuild){
        LogicalTimedWord outcome = new LogicalTimedWord();
        for(ResetLogicalTimedAction resetLogicalTimedAction : this.timedActions)
            outcome.attend(resetLogicalTimedAction.toLogicalTimedAction(if_rebuild));
        return outcome;
    }

    public boolean regionEqual(ResetLogicalTimedWord resetLogicalTimedWord){
        if(this.length != resetLogicalTimedWord.length)
            return false;
        for(int i=0;i<this.length;i++){
            ResetLogicalTimedAction action1 = this.getTimedAction(i);
            ResetLogicalTimedAction action2 = resetLogicalTimedWord.getTimedAction(i);
            if(!action1.getReset_clocks().equals(action2.getReset_clocks()))
                return false;
            if(!action1.regionEquals(action2, ResetLogicalTimedWord.Kmap))
                return false;
        }
        return true;
    }

    /***********
     * Used in composition learning to check if new counterexample can correct reset info of S and R prefix
     */
    public boolean equalsWithoutResetInfo(ResetLogicalTimedWord resetLogicalTimedWord){
        if (this.length != resetLogicalTimedWord.length)
            return false;
        for(int i=0;i<this.length;i++){
            ResetLogicalTimedAction action1 = this.getTimedAction(i);
            ResetLogicalTimedAction action2 = resetLogicalTimedWord.getTimedAction(i);
//            if(!action1.getReset_clocks().equals(action2.getReset_clocks()))
//                return false;
            if(!action1.regionEquals(action2))
                return false;
        }
        return true;
    }

    public boolean ifHasUnknown() {
        for (int i = 0; i < this.length; i++) {
            ResetLogicalTimedAction rla = this.getTimedAction(i);
            if (!rla.ifKnowReset) {
                return false;
            }
        }
        return true;
    }

    public ResetLogicalTimedWord normalization(Value fracPart, Map<Clock,Value> Kmap){
        ResetLogicalTimedWord outcome = new ResetLogicalTimedWord();
        for(ResetLogicalTimedAction rla : this){
            Action action = rla.getAction();
            ClockValuation clockValuation = rla.getClockValuation().normalization(fracPart, Kmap);
            Set<Clock> reset = rla.getReset_clocks();
            outcome.attend(new ResetLogicalTimedAction(action, clockValuation, reset));
        }
        return outcome;
    }

    public boolean equals(Object o){
        if(o == null)
            return false;
        if(!(o instanceof ResetLogicalTimedWord))
            return false;
        return this.regionEqual((ResetLogicalTimedWord) o);
    }

    public void attend(Action action, Value delay, Set<Clock> reset_clocks){
        if(this.length==0)
            throw new RuntimeException(TypeExceptions.CANNOT_ATTACH_TO_NULL);
        else{
            ResetLogicalTimedAction arl = this.getLast();
            ClockValuation next_v = arl.getNextClockValuation();
            ClockValuation delayed_v = next_v.delay(delay);
            ResetLogicalTimedAction next_arl = new ResetLogicalTimedAction(action, delayed_v, reset_clocks);
            this.attend(next_arl);
        }
    }

    public void attend(Action action, ClockValuation valuation, Set<Clock> reset_clocks){
        ResetLogicalTimedAction rla = new ResetLogicalTimedAction(action, valuation, reset_clocks);
        this.attend(rla);
    }

    public void attend(LogicalTimedAction la, Set<Clock> reset_clocks){
        ResetLogicalTimedAction rla = new ResetLogicalTimedAction(la.getAction(), la.getClockValuation(), reset_clocks);
        this.attend(rla);
    }

    /*******
     * Return last state clock valuation, including last transition reset
     * @return
     */
    public ClockValuation getLastValuation(){
        if(this.length==0)
            return null;
        else
            return this.getLast().getNextClockValuation();
    }


    public ResetLogicalTimedWord replaceHypothesisClocks(Map<Clock, Clock> clockMap){
        ResetLogicalTimedWord newWord = new ResetLogicalTimedWord();
        for (int i = 0; i < length; i++) {
            ResetLogicalTimedAction action = this.timedActions.get(i);

            // ClockValuation
            ClockValuation oldClockValuation = action.getClockValuation();
            Set<Clock> oldClockSet = oldClockValuation.getAllClocks();
            ClockValuation newClockValuation = new ClockValuation();
            for (Clock oldClock : oldClockSet) {
                Clock newClock = clockMap.getOrDefault(oldClock, oldClock);
                newClockValuation.put(newClock, oldClockValuation.getValue(oldClock));
            }

            // reset list
            Set<Clock> oldResetSet = action.getReset_clocks();
            Set<Clock> newResetSet = new HashSet<>();
            for (Clock oldReset : oldResetSet) {
                Clock newReset = clockMap.getOrDefault(oldReset, oldReset);
                newResetSet.add(newReset);
            }

            ResetLogicalTimedAction newAction = new ResetLogicalTimedAction(action.getAction(), newClockValuation, newResetSet);
            newWord.attend(newAction);
        }
        return newWord;
    }

    /********
     * Generates new object, safe to use
     * @return
     */
    public DelayTimedWord toDelayTimedWord(){
        DelayTimedWord outcome = new DelayTimedWord();
        if (this.length <= 0) {
            return outcome;
        }
        ClockValuation last = new ClockValuation(this.getTimedAction(0).getClockValuation().getAllClocks());
        Set<Clock> last_reset = new HashSet<>();
        for (ResetLogicalTimedAction rla : this) {
            Action action = rla.getAction();
            ClockValuation current = rla.getClockValuation();
            Value delay = last.delayTime(current, last_reset);
            DelayTimedAction da = new DelayTimedAction(action, delay);
            outcome.attend(da);
            last = current;
            last_reset = rla.getReset_clocks();
        }
        return outcome;
    }

    /*********
     * Check reset validity
     */
    public Pair<Boolean, ResetLogicalTimedWord> checkDelayTime(boolean flag) {
        ResetLogicalTimedWord outcome = new ResetLogicalTimedWord();
        if (this.length <= 0) {
            return new Pair<>(flag, outcome);
        }
        Set<Clock> allClockSet = this.getTimedAction(0).getClockValuation().getAllClocks();
        ClockValuation last = new ClockValuation(allClockSet);
        Set<Clock> last_reset = new HashSet<>();
        for (ResetLogicalTimedAction rla : this) {
            ClockValuation current = rla.getClockValuation();
            try {
                last.delayTime(current, last_reset);
            } catch (Exception ignore) {
                break; // This ResetLogicalTimedWord is doomed (illegal); MQ returned extra info
            }
            outcome.attend(rla);
            last = current;
            last_reset = rla.getReset_clocks();
        }
        if (outcome.length == this.length) {
            return new Pair<>(flag, outcome);
        } else {
            for (int i = outcome.length; i < this.length; i++) {
                outcome.attend(this.getTimedAction(i).toLogicalTimedAction(true), allClockSet);
            }
            return new Pair<>(false, outcome);
        }
    }

    /*********
     * Up to last non-reset Action
     * @return
     */
    public ResetLogicalTimedWord subWordToLastNotReset(){
        List<ResetLogicalTimedAction> outcomeList = new LinkedList<>();
        List<ResetLogicalTimedAction> cache = new LinkedList<>();
        for(ResetLogicalTimedAction rla : this){
            if(!rla.getReset_clocks().isEmpty())
                cache.add(rla);
            else{
                outcomeList.addAll(cache);
                outcomeList.add(rla);
                cache.clear();
            }
        }
        return new ResetLogicalTimedWord(outcomeList);
    }

    /*******
     * Check if word is legal; may be implemented elsewhere but reimplementing here
     * @return
     */
    public boolean isLegal(){
        if(this.isEmpty()) {
            return true;    // Empty is naturally legal
        } else {
            ClockValuation v = this.getTimedAction(0).getClockValuation();
            Set<Clock> reset = this.getTimedAction(0).getReset_clocks();
            ClockValuation v_next;
            Set<Clock> reset_next;
            for(int i=1;i<this.length;i++){
                if(!v.geZero())
                    return false;
                v_next = this.getTimedAction(i).getClockValuation();
                reset_next = this.getTimedAction(i).getReset_clocks();
                if(!v.isLegalDelay(v_next, reset))
                    return false;
                v = v_next;
                reset = reset_next;
            }
            return true;
        }
    }

    /**********
     * Check if longest prefix from Region perspective
     * @param Kmap
     * @return
     */
    public boolean isLongestPrefixViaRegionK(ResetLogicalTimedWord other, Map<Clock, Value> Kmap){
        if(this.length==0) return false;
        if(this.length-1 != other.length) return false;
        for(int i=0;i<other.length;i++){
            ResetLogicalTimedAction A = this.getTimedAction(i);
            ResetLogicalTimedAction B = other.getTimedAction(i);
            if(A.getAction() != B.getAction())
                return false;
            if(A.getReset_clocks().size() != B.getReset_clocks().size())
                return false;
            if(!A.getReset_clocks().containsAll(B.getReset_clocks()))
                return false;
            Region A_region = new Region(A.getClockValuation(), Kmap);
            Region B_region = new Region(B.getClockValuation(), Kmap);
            if(!A_region.equals(B_region))
                return false;
        }
        return true;
    }
}
