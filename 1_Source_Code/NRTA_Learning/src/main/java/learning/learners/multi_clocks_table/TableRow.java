package learning.learners.multi_clocks_table;

import language.words.ResetLogicalTimedWord;
import ta.Clock;

import java.util.*;

public class TableRow {
    private final List<TableElement> elements;

    public TableRow() {
        this.elements = new LinkedList<>();
    }

    public int size() {
        return this.elements.size();
    }

    public void add(boolean accept, ResetLogicalTimedWord wrl, ResetLogicalTimedWord prefix) {
        this.elements.add(new TableElement(accept, wrl, prefix));
    }

    public void add(boolean accept, int len, Set<Clock> clockSet){
        List<Set<Clock>> reset_list = new LinkedList<>();
        for(int i=0;i<len;i++)
            reset_list.add(clockSet);
        this.elements.add(new TableElement(accept, reset_list));
    }

    public boolean equals(Object obj){
        if(this==obj) return true;
        if(obj == null || getClass() != obj.getClass()) return false;
        TableRow row = (TableRow) obj;
        if(this.size()!=row.size()) return false;
        for(int i=0;i<this.size();i++){
            if(!this.elements.get(i).equals(row.elements.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        for(TableElement element : this.elements) {
            sb.append("[");
            sb.append(element.toString());
            sb.append("]");
        }
        return sb.toString();
    }

    public TableElement get(int i){
        return this.elements.get(i);
    }

    /*********
     * Create a copy. Note: elements remain the same as they are immutable.
     * This only creates a new Row at the Row level.
     * @return
     */
    public TableRow copy(){
        TableRow outcome = new TableRow();
        outcome.elements.addAll(this.elements);
        return outcome;
    }
}