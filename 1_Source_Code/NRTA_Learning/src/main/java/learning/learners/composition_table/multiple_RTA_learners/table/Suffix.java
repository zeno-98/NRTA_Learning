package learning.learners.composition_table.multiple_RTA_learners.table;

import language.words.RTARegionWord;

public class Suffix {
    private final RTARegionWord suffix;

    public Suffix(RTARegionWord suffix) {
        this.suffix = suffix;
    }

    @Override
    public String toString() {
        return this.suffix.toString();
    }

    public RTARegionWord word(){
        return this.suffix;
    }

    public int getLength(){
        return this.suffix.getLength();
    }

    public boolean isEmpty(){
        return this.suffix.isEmpty();
    }
}
