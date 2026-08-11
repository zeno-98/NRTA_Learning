package learning.learners.multi_clocks_tree.classifytree;

import hu.webarticum.treeprinter.text.ConsoleText;
import language.words.ResetLogicalTimedWord;
import learning.learners.multi_clocks_tree.BoolMatrix;
import lombok.Getter;

import java.util.List;

public class TreeNodeLeaf extends TreeNode {
    @Getter
    private final ResetLogicalTimedWord resetLogicalWord;

    public TreeNodeLeaf(ResetLogicalTimedWord resetLogicalWord, TreeNodeInside parent, BoolMatrix lastBoolMatrix) {
        super(parent, lastBoolMatrix);
        this.resetLogicalWord = resetLogicalWord;
    }

    @Override
    public String toString() {
        return this.resetLogicalWord.toString();
    }

    @Override
    public ConsoleText content() {
        return ConsoleText.of(this.getLastBoolK() + "->" + this);
    }

    @Override
    public List<hu.webarticum.treeprinter.TreeNode> children() {
        return List.of();
    }
}
