package learning.learners.multi_clocks_tree;

import language.words.LogicalTimedWord;
import language.words.RegionTimedWord;
import language.words.ResetLogicalTimedWord;

public interface TreeHelper {
    BoolMatrix getResetAndAccept(ResetLogicalTimedWord rGamma, RegionTimedWord d);

    boolean raiseMembershipQuery(LogicalTimedWord logicalTimedWord);

    ResetLogicalTimedWord raiseResetInformationQuery(LogicalTimedWord logicalWord);
}
