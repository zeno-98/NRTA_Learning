package learning.learners.multi_clocks_tree.classifytree;

import language.actions.ResetLogicalTimedAction;

public class LeafTrans extends TreeNodeTransition<TreeNodeLeaf, TreeNodeLeaf, ResetLogicalTimedAction> {
    public LeafTrans(TreeNodeLeaf sourceNode, TreeNodeLeaf targetNode, ResetLogicalTimedAction edgeValue) {
        super(sourceNode, targetNode, edgeValue);
    }
}
