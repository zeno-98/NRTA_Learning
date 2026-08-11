package learning.learners.composition_table.multiple_RTA_learners.element_tree;

import language.words.RTARegionWord;

import java.util.List;

public class ElementRecord {
    private final List<RTARegionWord> unknownFrontierWords;
    private final List<RTARegionWord> falseFrontierWords;

    public ElementRecord(List<RTARegionWord> unknownFrontierWords, List<RTARegionWord> falseFrontierWords) {
        this.unknownFrontierWords = unknownFrontierWords;
        this.falseFrontierWords = falseFrontierWords;
    }

    public List<RTARegionWord> getUnknownFrontierWords() {
        return this.unknownFrontierWords;
    }

    public List<RTARegionWord> getFalseFrontierWords() {
        return this.falseFrontierWords;
    }
}
