package learning.learners.composition_table.multiple_RTA_learners.table;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Model;
import language.actions.RTARegionAction;
import language.words.RTARegionWord;
import learning.learners.composition_table.TriBool;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementNode;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementTree;
import lombok.Getter;
import ta.Action;
import ta.Clock;
import ta.Value;
import tools.ExtendSolver;

import java.math.BigInteger;
import java.util.*;

public class Prefix {
    private final ExtendSolver slv;
    private final RTARegionWord prefix;
    @Getter
    private final List<Cell> cells;
    private final ElementNode elementNode;
    private final Prefix parent;
    @Getter
    private final Map<Action, Set<Prefix>> children;

    public Prefix(RTARegionWord prefix, Prefix parent, ElementNode elementNode, ExtendSolver slv) {
        this.prefix = prefix;
        this.cells = new LinkedList<>();
        this.parent = parent;
        this.children = new LinkedHashMap<>();
        this.elementNode = elementNode;
        this.slv = slv;
    }

    public List<Prefix> appendAction(Set<Action> actions, Clock clock){
        List<Prefix> outcome = new LinkedList<>();
        for(Action action : actions){
            RTARegionWord prefix = this.prefix.copy();
            RTARegionAction next = new RTARegionAction(action, clock, Value.ZERO);
            prefix.attend(next);
            Prefix next_prefix = new Prefix(prefix, this, this.elementNode.addChild(next), this.slv);

            if(this.children.containsKey(action)){
                Set<Prefix> group = this.children.get(action);
                if(!group.contains(next_prefix)) {
                    group.add(next_prefix);
                    outcome.add(next_prefix);
                }
            } else {
                Set<Prefix> group = new HashSet<>();
                group.add(next_prefix);
                this.children.put(action, group);
                outcome.add(next_prefix);
            }
        }
        return outcome;
    }

    public void constructCells(List<Suffix> e_area){
        for(int i=this.cells.size();i<e_area.size();i++){
            Cell cell = new Cell(this, e_area.get(i));
            this.cells.add(cell);
        }
    }

    public void clearChildren() {
        this.children.clear();
    }

    public void rebuildCells(List<Suffix> eArea, ElementTree elementTree) {
        this.cells.clear();
        this.constructCells(eArea);
        for (Cell cell : this.cells) {
            ElementNode cellElementNode = elementTree.seekNode(cell.getPrefix().word(), cell.getSuffix().word());
            if (cellElementNode == null) {
                cellElementNode = elementTree.addNode(cell.getPrefix().word(), cell.getSuffix().word());
            }
            cell.setElementNode(cellElementNode);
        }
    }

    public Row row(){
        return new Row(this);
    }

    public Row row(Model model){
        List<TriBool> data = new LinkedList<>();
        for(Cell cell : this.cells){
            data.add(cell.getSolvedData(model));
        }
        return new Row(this, data);
    }

    @Override
    public int hashCode() {
        return this.elementNode.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Prefix){
            return this.elementNode == ((Prefix) obj).elementNode;
        }
        return false;
    }

    @Override
    public String toString() {
        return this.prefix.toString();
    }

    public String toStringWithReset() {
        return this.toString();
    }

    public boolean rowLooseEqual(Prefix other){
        return this.row().looseEqual(other.row());
    }

    public boolean rowStrictEqual(Prefix other){
        return this.row().strictEqual(other.row());
    }

    public RTARegionWord word(){
        return this.prefix;
    }

    public int getLength(){
        return this.prefix.getLength();
    }

    public boolean isEmpty(){
        return this.prefix.isEmpty();
    }

    public RTARegionAction getLast(){
        assert !this.prefix.isEmpty();
        return this.elementNode.getLast_action();
    }

    public void addChild(Prefix newPrefix){
        Action action = newPrefix.getLast().getAction();
        this.addChild(action, newPrefix);
    }


    public void addChild(Action action, Prefix newPrefix) {
        if (!this.children.containsKey(action)) {
            this.children.put(action, new LinkedHashSet<>());
        }
        this.children.get(action).add(newPrefix);
    }


    public BitVecExpr rowSmtExpr(){
        BitVecExpr outcome = this.cells.get(0).getSmtExpr();
        for(int i=1;i<this.cells.size();i++){
            outcome = this.slv.mkConcat(outcome, this.cells.get(i).getSmtExpr());
        }
        return outcome;
    }

    public Set<Prefix> getAllChildren(){
        Set<Prefix> outcome = new LinkedHashSet<>();
        for(Map.Entry<Action, Set<Prefix>> entry : this.children.entrySet()){
            outcome.addAll(entry.getValue());
        }
        return outcome;
    }

    public boolean solvedAccept(Model model){
        TriBool accept = this.elementNode.isAccept();
        if(accept == TriBool.tri_true)
            return true;
        else if (accept == TriBool.tri_false)
            return false;
        else {
            BitVecExpr bvAccept = this.elementNode.getBvAccept();
            BigInteger solved = this.slv.getBvValue(bvAccept, model);
            return solved.intValue() == 1;
        }
    }
}
