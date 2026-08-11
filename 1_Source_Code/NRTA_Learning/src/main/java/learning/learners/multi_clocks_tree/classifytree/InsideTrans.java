package learning.learners.multi_clocks_tree.classifytree;


import learning.learners.multi_clocks_tree.BoolMatrix;

public class InsideTrans extends TreeNodeTransition<TreeNodeInside, TreeNode, BoolMatrix> {
    public InsideTrans(TreeNodeInside sourceNode, TreeNode targetNode, BoolMatrix edgeValue) {
        super(sourceNode, targetNode, edgeValue);
        assert 1 + sourceNode.getRegionWord().getLength() == edgeValue.getVecLength();
    }
}