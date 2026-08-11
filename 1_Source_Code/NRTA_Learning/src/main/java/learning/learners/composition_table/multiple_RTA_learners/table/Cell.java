package learning.learners.composition_table.multiple_RTA_learners.table;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Model;
import learning.learners.composition_table.TriBool;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementNode;
import lombok.Getter;
import lombok.Setter;


@Getter
public class Cell {
    private final Prefix prefix;
    private final Suffix suffix;
    @Setter
    private ElementNode elementNode;

    public Cell(Prefix prefix, Suffix suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public TriBool isAccept(){
        return this.getElementNode().isAccept();
    }

    @Override
    public String toString() {
        return this.prefix + "*" + this.suffix;
    }

    /**********
     * Compare cell information
     * @param other
     * @return
     */
    public boolean suffixLooseEqual(Cell other){
        return this.isAccept().looseEqual(other.isAccept());
    }

    /*****
     * Get SMT expression for this cell
     * @return
     */
    public BitVecExpr getSmtExpr(){
        return this.elementNode.getBvAccept();
    }

    /********
     * Get information in cell after solving
     * @param model
     * @return
     */
    public TriBool getSolvedData(Model model){
        return this.elementNode.getSolvedData(model);
    }

    public boolean isPrefixEmpty(){
        return this.prefix.isEmpty();
    }

    public boolean isSuffixEmpty(){
        return this.suffix.isEmpty();
    }
}
