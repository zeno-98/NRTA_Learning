package learning.learners.multi_clocks_tree.classifytree;

import learning.learners.multi_clocks_tree.BoolMatrix;
import lombok.Getter;

public abstract class TreeNode implements hu.webarticum.treeprinter.TreeNode {
    @Getter
    private TreeNodeInside parent;

    @Getter
    private BoolMatrix lastBoolK;

    /***
     * Only used when leaf nodes split. To strictly set final property, rebuild instead of modifying in place.
     * @param parent the new intermediate node after splitting
     * @param lastBoolK the edge value connected to parent node, for visualization
     */
    public void renewMetadata(TreeNodeInside parent, BoolMatrix lastBoolK) {
        this.parent = parent;
        this.lastBoolK = lastBoolK;
    }

    public TreeNode(TreeNodeInside parent, BoolMatrix lastBoolK) {
        this.parent = parent;
        this.lastBoolK = lastBoolK;
    }
}
