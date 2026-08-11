package language.words;

import exception.OtherExceptions;
import exception.ValueExceptions;
import language.actions.TimedAction;
import lombok.Getter;
import ta.Action;

import java.util.*;

public abstract class TimedWord<T extends TimedAction, W extends TimedWord<T,W>> implements Cloneable, Iterable<T>{
    protected List<T> timedActions;
    @Getter
    protected int length;

    public TimedWord(){
        this.timedActions = new LinkedList<>();
        this.length = 0;
    }

    public TimedWord(List<T> list){
        this.timedActions = list;
        this.length = list.size();
    }

    public TimedWord(T... actions){
        this.timedActions = new LinkedList<>();
        this.timedActions.addAll(Arrays.asList(actions));
        this.length = this.timedActions.size();
    }

    /**
     * Appends an action to this word.
     * @param timedAction the timed action to append
     */
    public void attend(T timedAction){
        this.timedActions.add(timedAction);
        this.length++;
    }

    public void attendAll(TimedWord<T,W> timedWord){
        this.timedActions.addAll(timedWord.timedActions);
        this.length += timedWord.getLength();
    }

    public boolean isEmpty(){
        return this.length == 0;
    }

    public W subWord(int fromIndex, int toIndex){
        if(toIndex < 0)
            return null;
        W outcome = this.clone();
        outcome.timedActions = outcome.timedActions.subList(fromIndex, toIndex);
        outcome.length = outcome.timedActions.size();
        return outcome;
    }

    public abstract W projection(Set<Action> target_alphabet);

    public String toString(){
        if(length == 0)
            return "epsilon";
        else{
            StringBuilder outcome = new StringBuilder();
            for(TimedAction timedAction : timedActions){
                outcome.append(timedAction.toString());
            }
            return outcome.toString();
        }
    }

    public String actionWord(){
        if(length == 0)
            return "epsilon";
        else{
            int i = 0;
            StringBuilder outcome = new StringBuilder();
            for(TimedAction timedAction : timedActions) {
                if(i>0)
                    outcome.append('-');
                outcome.append(timedAction.getAction().getName());
                i++;
            }
            return outcome.toString();
        }
    }

    public T getTimedAction(int index){
        if(index >= this.length || index < 0)
            throw new RuntimeException(ValueExceptions.LIST_OUT_OF_BOUND);
        else
            return this.timedActions.get(index);
    }

    /**
     * Concatenates a suffix, creating a new object without affecting this one.
     * @param suffix the suffix to concatenate
     * @return a new word with the suffix concatenated
     */
    public W concat(W suffix){
        W outcome = this.commonClone();
        outcome.timedActions.addAll(suffix.timedActions);
        outcome.length += suffix.length;
        return outcome;
    }

    /**
     * Connects a suffix without creating a new object, modifying this one.
     * @param suffix the suffix to connect
     */
    public void connect(W suffix){
        this.timedActions.addAll(suffix.timedActions);
        this.length += suffix.length;
    }

    /**
     * Creates a copy and replaces the first num actions of the word with the first num actions of prefix.
     * Must ensure no out-of-bounds access.
     * @param prefix the prefix word
     * @param num the number of actions to replace
     * @return a new word with replaced actions
     */
    public W replace(W prefix, int num){
        W outcome = this.commonClone();
        for(int i=0; i<num; i++){
            outcome.timedActions.set(i,prefix.getTimedAction(i));
        }
        return outcome;
    }

    /**
     * Performs a shallow copy.
     * @return a shallow copy of this word
     */
    public W clone(){
        try {
            return (W) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(OtherExceptions.WORD_CLONE_WRONG);
        }
    }

    /**
     * Performs a common copy, i.e., the list is a new object, but the elements in the list are the same.
     * So it cannot be said to be a pure deep copy.
     * @return a common copy of this word
     */
    public W commonClone(){
        W outcome = this.clone();
        outcome.timedActions = new LinkedList<>();
        outcome.timedActions.addAll(this.timedActions);
        return outcome;
    }

    public T getLast(){
        if(this.length==0)
            throw new RuntimeException(OtherExceptions.GETTING_FROM_EPSILON);
        else
            return this.timedActions.get(this.length-1);
    }

    private T get(int index){
        return this.timedActions.get(index);
    }

    /**
     * Gets all prefixes including itself and the empty string.
     * Sorted by length in ascending order, i.e., position 0 is the empty string.
     * @return a list of all prefixes
     */
    public List<W> getAllPrefixes(){
        List<W> prefixes = new LinkedList<>();
        for(int i=0;i<=this.length;i++){
            prefixes.add(this.subWord(0,i));
        }
        return prefixes;
    }

    /**
     * Gets the longest prefix.
     * @return the longest prefix (excluding itself)
     */
    public W getLongestPrefix(){
        return this.subWord(0,this.length-1);
    }

    /**
     * Determines if o is the longest prefix of this (excluding itself).
     * @param o the other word
     * @return true if o is the longest prefix, false otherwise
     */
    public boolean isLongestPrefix(TimedWord<T,W> o){
        if(this.length==0) return false;
        if(this.length-1 != o.length) return false;
        for(int i=0;i<o.length;i++)
            if(!this.get(i).equals(o.get(i)))
                return false;
        return true;
    }

    /**
     * Determines if o is a prefix of this (including itself).
     * @param o the other word
     * @return true if o is a prefix, false otherwise
     */
    public boolean isPrefix(TimedWord<T,W> o){
        if(this.length==0) return o.isEmpty();
        if(this.length < o.length) return false;
        for(int i=0;i<o.length;i++)
            if(!this.get(i).equals(o.get(i)))
                return false;
        return true;
    }

    // Hash calculation using action sequence
    public int hashCode() {
        return this.actionWord().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof TimedWord))
            return false;
        TimedWord right = (TimedWord) o;
        if(this.length != right.length) {
            return false;
        }
        for (int i = 0; i < this.length; i++) {
            if (!this.timedActions.get(i).equals(right.timedActions.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Iterator<T> iterator() {
        return this.timedActions.iterator();
    }
}
