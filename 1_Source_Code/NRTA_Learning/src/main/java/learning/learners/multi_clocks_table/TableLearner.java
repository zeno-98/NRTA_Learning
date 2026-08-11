package learning.learners.multi_clocks_table;


public interface TableLearner {
    void fillTable();
    boolean isClosed();
    void makeClosed();
    boolean isConsistent();
    void makeConsistent();
}
