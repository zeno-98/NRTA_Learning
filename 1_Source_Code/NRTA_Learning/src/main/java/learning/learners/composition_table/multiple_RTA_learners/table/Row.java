package learning.learners.composition_table.multiple_RTA_learners.table;

import learning.learners.composition_table.TriBool;
import lombok.Getter;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Row implements Iterable<TriBool>{
    private final List<TriBool> data;
    @Getter
    private Prefix prefix;

    public Row(Prefix prefix){
        this.data = new LinkedList<>();
        this.prefix = prefix;
        for(Cell cell : prefix.getCells()){
            TriBool cellData = cell.isAccept();
            this.data.add(cellData);
        }
    }

    public Row(Prefix prefix, List<TriBool> data){
        this.prefix = prefix;
        this.data = new LinkedList<>();
        this.data.addAll(data);
    }


    /********
     * Strict equality
     * @param other
     * @return
     */
    public boolean strictEqual(Row other){
        assert this.data.size() == other.data.size();
        for(int i=0;i<this.data.size();i++){
            if(!this.data.get(i).strictEqual(other.data.get(i))) {
                return false;
            }
        }
        return true;
    }

    /*********
     * This row is in R area.
     * One-way equality:
     * ? in S area must match ? in R area,
     * but ? in R area can match +/-? in S area.
     */
    public boolean directionStrictEqual(Row s){
        assert this.data.size() == s.data.size();
        for(int i=0;i<this.data.size();i++){
            if(!this.data.get(i).directionStrictEqual(s.data.get(i))) {
                return false;
            }
        }
        return true;
    }

    /******
     * Loose equality
     * @param other
     * @return
     */
    public boolean looseEqual(Row other){
        assert this.data.size() == other.data.size();
        for(int i=0;i<this.data.size();i++){
            if(!this.data.get(i).looseEqual(other.data.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return this.data.toString();
    }

    public String toStringFitSuffix(boolean needFit, Prefix prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("|");
        List<Cell> cells = prefix.getCells();
        for (int idx = 0; idx < this.data.size(); idx++) {
            String cellValue = this.data.get(idx).toString();
            if (needFit) {
                int width = cells.get(idx).getSuffix().toString().length();
                sb.append(cellValue);
                sb.append(" ".repeat(Math.max(0, width - cellValue.length())));
            } else {
                sb.append(cellValue);
            }
            sb.append("|");
        }
        return sb.toString();
    }

    @Override
    public Iterator<TriBool> iterator() {
        return this.data.iterator();
    }
}
