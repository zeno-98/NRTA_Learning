package learning.learners.multi_clocks_tree.classifytree;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public abstract class TreeNodeTransition<SourceType extends TreeNode, TargetType extends TreeNode, EdgeType> {
    protected SourceType sourceNode;
    protected TargetType targetNode;
    protected EdgeType edgeValue;

    @Override
    public String toString() {
        return sourceNode.toString() + "--" + edgeValue.toString() + "-->" + targetNode.toString();
    }
}
