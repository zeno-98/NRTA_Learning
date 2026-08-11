package learning.learners.multi_clocks_table;

import language.actions.ResetLogicalTimedAction;
import language.words.ResetLogicalTimedWord;
import lombok.Getter;
import ta.Clock;

import java.util.*;

public class TableElement {
    @Getter
    private final boolean accept;
    private final List<Set<Clock>> reset_list;

    public TableElement(boolean accept, ResetLogicalTimedWord wrl, ResetLogicalTimedWord prefix){
        this.accept = accept;
        int length = wrl.getLength();
        int len1 = prefix.getLength();
        this.reset_list = new LinkedList<>();
        for(int i=len1;i<length;i++){
            ResetLogicalTimedAction arl = wrl.getTimedAction(i);
            this.reset_list.add(arl.getReset_clocks());
        }
    }

    public TableElement(boolean accept, List<Set<Clock>> reset_list){
        this.accept = accept;
        this.reset_list = reset_list;
    }

    public boolean equals(Object obj){
        if(this == obj) return true;
        if(obj == null || getClass() != obj.getClass()) return false;
        TableElement element = (TableElement) obj;
        if(this.accept!=element.accept) return false;
        if(this.reset_list.size()!=element.reset_list.size()) return false;
        for(int i=0;i< reset_list.size();i++) {
            Set<Clock> a = this.reset_list.get(i);
            Set<Clock> b = element.reset_list.get(i);
            if(a.size()!=b.size()) return false;
            if(!a.containsAll(b)) return false;
        }
        return true;
    }

    public int hashCode(){
        return Objects.hash(this.accept, this.reset_list);
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(this.accept ? " + " : " - ");
        for (Set<Clock> e : this.reset_list) {
            sb.append(e);
        }
        return sb.toString();
    }

    public int size(){
        return this.reset_list.size()+1;
    }
}
