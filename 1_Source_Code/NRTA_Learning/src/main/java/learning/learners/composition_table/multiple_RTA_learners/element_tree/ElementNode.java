package learning.learners.composition_table.multiple_RTA_learners.element_tree;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Model;
import language.actions.RTARegionAction;
import language.words.RTARegionWord;
import learning.learners.composition_table.TriBool;
import lombok.Getter;
import ta.Action;
import tools.ExtendSolver;
import tools.IdCounter;

import java.util.*;

public class ElementNode {
    private static final IdCounter id_counter = new IdCounter();
    private int id;
    private final ExtendSolver slv;
    private final BitVecExpr bvAccept;

    private TriBool accept;
    @Getter
    private final RTARegionWord lw;
    @Getter
    private final RTARegionAction last_action;
    @Getter
    private final ElementNode parent;
    @Getter
    private final Map<Action, List<ElementNode>> children;

    public ElementNode(RTARegionWord lw, ElementNode parent, ExtendSolver slv){
        this.id = ElementNode.id_counter.createId();
        this.bvAccept = slv.mkBVConst("A_" + id, 1);
        this.slv = slv;
        this.lw = lw;
        this.accept = TriBool.unknown;
        this.last_action = this.lw.isEmpty() ? null : this.lw.getLast();
        this.parent = parent;
        if (this.parent != null && this.parent.accept == TriBool.tri_false) {
            this.accept = TriBool.tri_false;
        }
        this.children = new LinkedHashMap<>();
    }

    public ElementNode addChild(RTARegionAction la){
        List<ElementNode> group;
        Action action = la.getAction();
        if(!this.children.containsKey(action)){
            group = new LinkedList<>();
            this.children.put(action, group);
        } else {
            group = this.children.get(action);
            for(ElementNode child : group){
                if(child.last_action.regionEquals(la))
                    return child;
            }
        }
        RTARegionWord lw_ = this.lw.copy();
        lw_.attend(la);

        ElementNode child = new ElementNode(lw_, this, this.slv);
        group.add(child);
        return child;
    }

    public void addChild(Action action, ElementNode newChild) {
        if (!this.children.containsKey(action)) {
            this.children.put(action, new LinkedList<>());
        }
        this.children.get(action).add(newChild);
    }

    public ElementNode seekChild(RTARegionAction la){
        Action action = la.getAction();
        if(this.children.containsKey(action)){
            List<ElementNode> group = this.children.get(action);
            for(ElementNode child : group){
                if(child.last_action.regionEquals(la))
                    return child;
            }
        }
        return null;
    }

    public boolean isKnown(){
        ElementNode current = this;
        while(!current.isRoot()){
            if(current.accept == TriBool.unknown)
                return false;
            current = current.parent;
        }
        return true;
    }

    public void setAccept(TriBool triBool) {
        if (triBool == TriBool.tri_true) {
            this.upFlashAcceptCore();
        } else if (triBool == TriBool.tri_false) {
            this.downFlashNotAcceptCore();
        } else {
            if (this.accept != TriBool.tri_true) {
                this.accept = TriBool.unknown;
            }
        }
    }

    private void upFlashAcceptCore(){
        ElementNode current = this;
        while(current != null){
            current.accept = TriBool.tri_true;
            current = current.parent;
        }
    }

    private void downFlashNotAcceptCore(){
        if (this.accept == TriBool.tri_true) {
            throw new RuntimeException("downFlashNotAcceptCore reached tri_true node: " + this.wordToString());
        }
        this.accept = TriBool.tri_false;
        for(Map.Entry<Action, List<ElementNode>> entry : this.children.entrySet()){
            for(ElementNode child : entry.getValue()) {
                child.downFlashNotAcceptCore();
            }
        }
    }

    public TriBool isAccept(){
        return this.accept;
    }

    void setAcceptDirect(TriBool value) {
        this.accept = value;
    }

    public boolean isRoot(){
        return this.parent == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "[" + this.accept + ":" + this.actionToString() + "]";
    }

    public String actionToString() {
        if (isRoot())
            return "epsilon";
        else
            return "(" + this.last_action.getAction() + "," + this.last_action.getLower() + "," + this.last_action.getUpper() + ")";
    }

    public String wordToString() {
        if (this.isRoot()) {
            return this.actionToString();
        }
        ElementNode curNode = this;
        StringBuilder sBuilder = new StringBuilder();
        List<String> temp = new LinkedList<>();
        do {
            temp.add(curNode.actionToString());
            curNode = curNode.parent;
        } while (!curNode.isRoot());
        for(int i=temp.size()-1;i>=0;i--){
            sBuilder.append(temp.get(i));
        }
        return sBuilder.toString();
    }

    public BitVecExpr getBvAccept(){
        if(this.accept == TriBool.tri_true)
            return this.slv.bvTrue;
        else if(this.accept == TriBool.tri_false)
            return this.slv.bvFalse;
        else
            return this.bvAccept;
    }

    public boolean getSolvedAccept(Model model){
        if(this.accept == TriBool.tri_true)
            return true;
        else if(this.accept == TriBool.tri_false)
            return false;
        else {
            try {
                return this.slv.getBvTruthValue(this.bvAccept, model);
            } catch (RuntimeException e){
                return true;
            }
        }
    }

    public TriBool getSolvedData(Model model){
        return TriBool.fromBoolean(this.getSolvedAccept(model));
    }

    // When a node is not accepted, none of its successors are accepted
    public void generateSinkConstraintCore(List<BoolExpr> constraints){
        BoolExpr notAccept = this.slv.mkEq(this.getBvAccept(), this.slv.bvFalse);
        for(List<ElementNode> group : this.children.values()){
            for(ElementNode child : group){
                BoolExpr childNotAccept = this.slv.mkEq(child.getBvAccept(), this.slv.bvFalse);
                constraints.add(this.slv.mkImplies(notAccept, childNotAccept));
                child.generateSinkConstraintCore(constraints);
            }
        }
    }

    public boolean isLeaf(){
        return this.children.isEmpty();
    }
}
