package learning.learners.composition_table.multiple_RTA_learners.table;

import language.words.RTARegionWord;
import learning.learners.composition_table.multiple_RTA_learners.element_tree.ElementRecord;

import java.util.List;

public class TableRecord {
    private final List<RTARegionWord> sAreaWords;
    private final List<RTARegionWord> rAreaWords;
    private final List<RTARegionWord> eAreaWords;
    private final ElementRecord elementRecord;

    public TableRecord(List<RTARegionWord> sAreaWords,
                       List<RTARegionWord> rAreaWords,
                       List<RTARegionWord> eAreaWords,
                       ElementRecord elementRecord) {
        this.sAreaWords = sAreaWords;
        this.rAreaWords = rAreaWords;
        this.eAreaWords = eAreaWords;
        this.elementRecord = elementRecord;
    }

    public List<RTARegionWord> getSAreaWords() {
        return this.sAreaWords;
    }

    public List<RTARegionWord> getRAreaWords() {
        return this.rAreaWords;
    }

    public List<RTARegionWord> getEAreaWords() {
        return this.eAreaWords;
    }

    public ElementRecord getElementRecord() {
        return this.elementRecord;
    }
}
