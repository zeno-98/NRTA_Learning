package learning.learners.multi_clocks_tree.classifytree;

import hu.webarticum.treeprinter.text.ConsoleText;
import language.words.RegionTimedWord;
import language.words.ResetLogicalTimedWord;
import learning.learners.multi_clocks_tree.BoolMatrix;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TreeNodeInside extends TreeNode {
    @Getter
    private final RegionTimedWord regionWord;

    private final Map<BoolMatrix, InsideTrans> children;

    public TreeNodeInside(RegionTimedWord regionWord, TreeNodeInside parent, BoolMatrix lastBoolK) {
        super(parent, lastBoolK);
        this.regionWord = regionWord;
        this.children = new HashMap<>();
    }

    public TreeNode findChildren(BoolMatrix boolMatrix) {
        assert 1 + this.regionWord.getLength() == boolMatrix.getVecLength();
        InsideTrans insideTrans = this.children.get(boolMatrix);
        if (insideTrans != null) {
            return insideTrans.getTargetNode();
        }
        return null;
    }

    public void replaceChild(TreeNode oldNode, TreeNodeInside newNode) {
        for (Map.Entry<BoolMatrix, InsideTrans> entry : this.children.entrySet()) {
            InsideTrans insideTrans = entry.getValue();
            if (insideTrans.getTargetNode().equals(oldNode)) {
                insideTrans.setTargetNode(newNode);
                break;
            }
        }
    }

    public TreeNodeLeaf addLeaf(BoolMatrix boolMatrix, ResetLogicalTimedWord resetLogicalWord) {
        assert !children.containsKey(boolMatrix);
        TreeNodeLeaf newLeafNode = new TreeNodeLeaf(resetLogicalWord, this, boolMatrix);
        return this.addLeaf(boolMatrix, newLeafNode);
    }

    public TreeNodeLeaf addLeaf(BoolMatrix boolMatrix, TreeNodeLeaf leafNode) {
        assert !this.children.containsKey(boolMatrix);
        InsideTrans transition = new InsideTrans(this, leafNode, boolMatrix);
        this.children.put(boolMatrix, transition);
        leafNode.renewMetadata(this, boolMatrix);
        return leafNode;
    }

    @Override
    public String toString() {
        return this.regionWord.toString();
    }

    @Override
    public ConsoleText content() {
        return ConsoleText.of(this.getLastBoolK() + "->" + this);
    }

    @Override
    public List<hu.webarticum.treeprinter.TreeNode> children() {
        return this.children.values().stream()
                .map(TreeNodeTransition::getTargetNode)
                .collect(Collectors.toList());
    }
}
