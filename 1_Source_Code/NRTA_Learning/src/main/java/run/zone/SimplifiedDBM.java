package run.zone;

import exception.DBMExceptions;
import ta.AtomConstraint;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.FormatOutput.StringMaker;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Simplified DBM with adaptive storage.
 *
 * Sparse mode:
 * - store only non-default cells using aligned lists.
 * - listI/listJ: coordinates
 * - listValues/listClosed: cell content
 *
 * Dense mode:
 * - store full matrices directly.
 *
 * Strategy:
 * - after each operation, estimate density = nonDefault / (size*size)
 * - density <= threshold -> sparse mode
 * - density > threshold  -> dense mode
 *
 * Threshold is configured by TeacherSetting.VERIFY_SIMPLIFIED_DBM_SPARSE_THRESHOLD.
 */
public class SimplifiedDBM extends Zone {
    private static final Value DEFAULT_VALUE = Value.ZERO;
    private static final boolean DEFAULT_CLOSED = AtomConstraint.LESS_EQ;

    private static final class DenseMatrix {
        private final Value[][] upper;
        private final boolean[][] closed;

        private DenseMatrix(int size) {
            this.upper = new Value[size][size];
            this.closed = new boolean[size][size];
        }
    }

    private final int size;

    // Sparse aligned lists
    private final List<Integer> listI;
    private final List<Integer> listJ;
    private final List<Value> listValues;
    private final List<Boolean> listClosed;

    // Dense mode storage
    private boolean denseMode;
    private Value[][] denseUpper;
    private boolean[][] denseClosed;

    public SimplifiedDBM() {
        this.size = TeacherSetting.getDBMClockSize();
        this.listI = new ArrayList<>();
        this.listJ = new ArrayList<>();
        this.listValues = new ArrayList<>();
        this.listClosed = new ArrayList<>();
        this.denseMode = false;
        this.denseUpper = null;
        this.denseClosed = null;
    }

    private SimplifiedDBM(SimplifiedDBM source) {
        this.size = source.size;
        this.listI = new ArrayList<>(source.listI);
        this.listJ = new ArrayList<>(source.listJ);
        this.listValues = new ArrayList<>(source.listValues);
        this.listClosed = new ArrayList<>(source.listClosed);
        this.denseMode = source.denseMode;
        if (source.denseMode) {
            this.denseUpper = new Value[size][size];
            this.denseClosed = new boolean[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    this.denseUpper[i][j] = source.denseUpper[i][j];
                    this.denseClosed[i][j] = source.denseClosed[i][j];
                }
            }
        } else {
            this.denseUpper = null;
            this.denseClosed = null;
        }
    }

    @Override
    public Map<Clock, Integer> getClockIndexMap() {
        return TeacherSetting.getDBMClockIndexMap();
    }

    @Override
    public List<Clock> getClockList() {
        return TeacherSetting.getDBMClockList();
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public Zone copy() {
        return new SimplifiedDBM(this);
    }

    @Override
    public void up() {
        DenseMatrix dense = restoreDense();
        applyUp(dense);
        storeAfterMutation(dense);
    }

    @Override
    public void reset(Clock x) {
        DenseMatrix dense = restoreDense();
        Integer index = getClockIndexMap().get(x);
        if (index == null) {
            throw new IllegalArgumentException("Clock " + x + " is not in this DBM");
        }
        applyReset(dense, index);
        storeAfterMutation(dense);
    }

    @Override
    public void canonical() {
        DenseMatrix dense = restoreDense();
        applyCanonical(dense);
        storeAfterCanonical(dense);
    }

    @Override
    public boolean isConsistent() {
        DenseMatrix dense = restoreDense();
        for (int i = 0; i < size; i++) {
            if (Value.compare(dense.upper[i][i], Value.ZERO) != Value.EQ) {
                return false;
            }
            if (!dense.closed[i][i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void and(Constraint constraint) {
        DenseMatrix dense = restoreDense();
        applyAnd(dense, constraint);
        storeAfterMutation(dense);
    }

    @Override
    public boolean includes(Zone zone) {
        if (zone.getSize() != this.size) {
            throw new RuntimeException(DBMExceptions.INCOMPARABLE_DBMS);
        }
        DenseMatrix dense = restoreDense();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Value value1 = dense.upper[i][j];
                boolean closed1 = dense.closed[i][j];
                Value value2 = zone.getValue(i, j);
                boolean closed2 = zone.getClosed(i, j);
                int relation = Value.compare(value1, value2);
                if (relation == Value.LESS) {
                    return false;
                } else if (relation == Value.EQ && !closed1 && closed2) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Value getValue(int i, int j) {
        checkIndex(i, j);
        if (denseMode) {
            return denseUpper[i][j];
        }
        int idx = findIndex(i, j);
        return idx >= 0 ? listValues.get(idx) : DEFAULT_VALUE;
    }

    @Override
    public boolean getClosed(int i, int j) {
        checkIndex(i, j);
        if (denseMode) {
            return denseClosed[i][j];
        }
        int idx = findIndex(i, j);
        return idx >= 0 ? listClosed.get(idx) : DEFAULT_CLOSED;
    }

    @Override
    public Iterator<AtomConstraint> iterator() {
        if (denseMode) {
            return defaultEffectiveIterator();
        }
        final List<Clock> clockList = getClockList();
        return new Iterator<>() {
            private int cursor = 0;
            private AtomConstraint next = advance();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public AtomConstraint next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                AtomConstraint current = next;
                next = advance();
                return current;
            }

            private AtomConstraint advance() {
                while (cursor < listI.size()) {
                    int k = cursor++;
                    int i = listI.get(k);
                    int j = listJ.get(k);
                    Value value = listValues.get(k);
                    boolean closed = listClosed.get(k);
                    if (value.isInfinite()) {
                        continue;
                    }
                    if (i == j && closed && value.isZero()) {
                        continue;
                    }
                    return new AtomConstraint(clockList.get(i), clockList.get(j), closed, value);
                }
                return null;
            }
        };
    }

    @Override
    public String toString() {
        DenseMatrix dense = restoreDense();
        List<Clock> clockList = getClockList();
        if (clockList.size() != size) {
            throw new IllegalStateException("Global DBM clock index changed after DBM construction");
        }

        String[] names = new String[size];
        int nameWidth = 1;
        for (int i = 0; i < size; i++) {
            names[i] = clockList.get(i).toString();
            nameWidth = Math.max(nameWidth, names[i].length());
        }

        String[][] elements = new String[size][size];
        int cellWidth = 1;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                StringBuilder element = new StringBuilder();
                element.append('(');
                if (dense.closed[i][j]) {
                    element.append("<=");
                } else {
                    element.append("< ");
                }
                element.append(',').append(dense.upper[i][j]).append(")");
                elements[i][j] = element.toString();
                cellWidth = Math.max(cellWidth, elements[i][j].length());
            }
        }
        for (String name : names) {
            cellWidth = Math.max(cellWidth, name.length());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(StringMaker.setWidthRight("", nameWidth, ' '));
        sb.append(" | ");
        for (int i = 0; i < size; i++) {
            sb.append(StringMaker.setWidthRight(names[i], cellWidth, ' '));
            if (i < size - 1) {
                sb.append(' ');
            }
        }
        sb.append('\n');

        int totalWidth = nameWidth + 3 + (size * cellWidth) + Math.max(0, size - 1);
        sb.append(StringMaker.setWidthRight("", totalWidth, '-'));
        sb.append('\n');

        for (int i = 0; i < size; i++) {
            sb.append(StringMaker.setWidthRight(names[i], nameWidth, ' '));
            sb.append(" | ");
            for (int j = 0; j < size; j++) {
                sb.append(StringMaker.setWidthRight(elements[i][j], cellWidth, ' '));
                if (j < size - 1) {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private DenseMatrix restoreDense() {
        DenseMatrix dense = new DenseMatrix(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                dense.upper[i][j] = DEFAULT_VALUE;
                dense.closed[i][j] = DEFAULT_CLOSED;
            }
        }
        if (denseMode) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    dense.upper[i][j] = denseUpper[i][j];
                    dense.closed[i][j] = denseClosed[i][j];
                }
            }
            return dense;
        }
        for (int k = 0; k < listI.size(); k++) {
            int i = listI.get(k);
            int j = listJ.get(k);
            dense.upper[i][j] = listValues.get(k);
            dense.closed[i][j] = listClosed.get(k);
        }
        return dense;
    }

    private void storeAfterMutation(DenseMatrix dense) {
        int nonDefault = countNonDefault(dense);
        double density = (double) nonDefault / (size * (double) size);
        if (density > TeacherSetting.VERIFY_SIMPLIFIED_DBM_SPARSE_THRESHOLD) {
            setDenseMode(dense);
        } else {
            setSparseModeNonDefault(dense);
        }
    }

    private void storeAfterCanonical(DenseMatrix canonicalDense) {
        int nonDefault = countNonDefault(canonicalDense);
        double density = (double) nonDefault / (size * (double) size);
        if (density > TeacherSetting.VERIFY_SIMPLIFIED_DBM_SPARSE_THRESHOLD) {
            setDenseMode(canonicalDense);
        } else {
            setSparseModeWithMinimization(canonicalDense);
        }
    }

    private int countNonDefault(DenseMatrix dense) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!isDefaultCell(dense.upper[i][j], dense.closed[i][j])) {
                    count++;
                }
            }
        }
        return count;
    }

    private void setDenseMode(DenseMatrix dense) {
        this.denseMode = true;
        this.denseUpper = new Value[size][size];
        this.denseClosed = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                this.denseUpper[i][j] = dense.upper[i][j];
                this.denseClosed[i][j] = dense.closed[i][j];
            }
        }
        clearSparse();
    }

    private void setSparseModeNonDefault(DenseMatrix dense) {
        this.denseMode = false;
        this.denseUpper = null;
        this.denseClosed = null;
        clearSparse();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!isDefaultCell(dense.upper[i][j], dense.closed[i][j])) {
                    addEntry(i, j, dense.upper[i][j], dense.closed[i][j]);
                }
            }
        }
    }

    private void setSparseModeWithMinimization(DenseMatrix canonicalDense) {
        setSparseModeNonDefault(canonicalDense);
        int candidateCount = listI.size();
        List<Integer> candI = new ArrayList<>(candidateCount);
        List<Integer> candJ = new ArrayList<>(candidateCount);
        for (int k = 0; k < candidateCount; k++) {
            candI.add(listI.get(k));
            candJ.add(listJ.get(k));
        }

        for (int k = 0; k < candidateCount; k++) {
            int i = candI.get(k);
            int j = candJ.get(k);
            int idx = findIndex(i, j);
            if (idx < 0) {
                continue;
            }
            Value removedValue = listValues.get(idx);
            boolean removedClosed = listClosed.get(idx);
            removeAt(idx);

            DenseMatrix rebuilt = restoreDense();
            applyCanonical(rebuilt);
            if (!sameAs(canonicalDense, rebuilt)) {
                addEntry(i, j, removedValue, removedClosed);
            }
        }
    }

    private boolean sameAs(DenseMatrix a, DenseMatrix b) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Value.compare(a.upper[i][j], b.upper[i][j]) != Value.EQ) {
                    return false;
                }
                if (a.closed[i][j] != b.closed[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void applyUp(DenseMatrix dense) {
        for (int i = 1; i < size; i++) {
            dense.upper[i][0] = Value.INFINITE;
            dense.closed[i][0] = AtomConstraint.LESS;
        }
    }

    private void applyReset(DenseMatrix dense, int index) {
        for (int i = 0; i < size; i++) {
            if (i == index) {
                continue;
            }
            dense.upper[index][i] = dense.upper[0][i];
            dense.upper[i][index] = dense.upper[i][0];
            dense.closed[index][i] = dense.closed[0][i];
            dense.closed[i][index] = dense.closed[i][0];
        }
    }

    private void applyAnd(DenseMatrix dense, Constraint constraint) {
        for (AtomConstraint atomConstraint : constraint.getAtomConstraints()) {
            int i = getClockIndexMap().get(atomConstraint.getClock1());
            int j = getClockIndexMap().get(atomConstraint.getClock2());
            Value newValue = atomConstraint.getUpperbound();
            boolean newClosed = atomConstraint.isClosed();
            Value oldValue = dense.upper[i][j];
            boolean oldClosed = dense.closed[i][j];
            int relation = Value.compare(oldValue, newValue);
            if (relation == Value.GREATER) {
                dense.upper[i][j] = newValue;
                dense.closed[i][j] = newClosed;
            } else if (relation == Value.EQ) {
                dense.closed[i][j] = oldClosed && newClosed;
            }
        }
    }

    private void applyCanonical(DenseMatrix dense) {
        for (int k = 0; k < size; k++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    Value currentValue = dense.upper[i][j];
                    boolean currentClosed = dense.closed[i][j];
                    Value viaK = Value.add(dense.upper[i][k], dense.upper[k][j]);
                    boolean viaKClosed = dense.closed[i][k] && dense.closed[k][j];
                    int relation = Value.compare(currentValue, viaK);
                    if (relation == Value.GREATER) {
                        dense.upper[i][j] = viaK;
                        dense.closed[i][j] = viaKClosed;
                    } else if (relation == Value.EQ) {
                        dense.closed[i][j] = currentClosed && viaKClosed;
                    }
                }
            }
        }
    }

    private int findIndex(int i, int j) {
        for (int k = 0; k < listI.size(); k++) {
            if (listI.get(k) == i && listJ.get(k) == j) {
                return k;
            }
        }
        return -1;
    }

    private void addEntry(int i, int j, Value value, boolean closed) {
        int idx = findIndex(i, j);
        if (idx >= 0) {
            listValues.set(idx, value);
            listClosed.set(idx, closed);
            return;
        }
        listI.add(i);
        listJ.add(j);
        listValues.add(value);
        listClosed.add(closed);
    }

    private void removeAt(int idx) {
        int last = listI.size() - 1;
        if (idx != last) {
            listI.set(idx, listI.get(last));
            listJ.set(idx, listJ.get(last));
            listValues.set(idx, listValues.get(last));
            listClosed.set(idx, listClosed.get(last));
        }
        listI.remove(last);
        listJ.remove(last);
        listValues.remove(last);
        listClosed.remove(last);
    }

    private void clearSparse() {
        listI.clear();
        listJ.clear();
        listValues.clear();
        listClosed.clear();
    }

    private static boolean isDefaultCell(Value value, boolean closed) {
        return closed == DEFAULT_CLOSED && Value.compare(value, DEFAULT_VALUE) == Value.EQ;
    }

    private void checkIndex(int i, int j) {
        if (i < 0 || i >= size || j < 0 || j >= size) {
            throw new IndexOutOfBoundsException("index out of DBM range: (" + i + "," + j + "), size=" + size);
        }
    }
}
