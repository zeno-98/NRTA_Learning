package run.zone;

import exception.DBMExceptions;
import ta.AtomConstraint;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.FormatOutput.StringMaker;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Integer-only simplified DBM with adaptive sparse/dense storage.
 *
 * Sparse mode stores only non-default cells with aligned primitive arrays:
 * - sparseI[k], sparseJ[k] : coordinates
 * - sparseBound[k], sparseRelation[k] : cell content
 *
 * Dense mode stores full flattened arrays for fast updates.
 */
public class SimplifiedIntDBM extends Zone {
    private static final byte REL_LT_FINITE = 0;
    private static final byte REL_LE_FINITE = 1;
    private static final byte REL_LT_INF = 2;

    private static final int DEFAULT_BOUND = 0;
    private static final byte DEFAULT_RELATION = REL_LE_FINITE;

    private final int size;

    // Sparse aligned arrays
    private int[] sparseI;
    private int[] sparseJ;
    private int[] sparseBound;
    private byte[] sparseRelation;
    private int sparseCount;

    // Dense flattened arrays
    private boolean denseMode;
    private int[] denseBound;
    private byte[] denseRelation;

    private static final class DenseMatrix {
        private final int[] bound;
        private final byte[] relation;

        private DenseMatrix(int cellCount) {
            this.bound = new int[cellCount];
            this.relation = new byte[cellCount];
        }
    }

    public SimplifiedIntDBM() {
        this.size = TeacherSetting.getDBMClockSize();
        this.sparseI = new int[16];
        this.sparseJ = new int[16];
        this.sparseBound = new int[16];
        this.sparseRelation = new byte[16];
        this.sparseCount = 0;
        this.denseMode = false;
        this.denseBound = null;
        this.denseRelation = null;
    }

    private SimplifiedIntDBM(SimplifiedIntDBM src) {
        this.size = src.size;
        this.sparseI = Arrays.copyOf(src.sparseI, src.sparseI.length);
        this.sparseJ = Arrays.copyOf(src.sparseJ, src.sparseJ.length);
        this.sparseBound = Arrays.copyOf(src.sparseBound, src.sparseBound.length);
        this.sparseRelation = Arrays.copyOf(src.sparseRelation, src.sparseRelation.length);
        this.sparseCount = src.sparseCount;
        this.denseMode = src.denseMode;
        this.denseBound = src.denseBound == null ? null : Arrays.copyOf(src.denseBound, src.denseBound.length);
        this.denseRelation = src.denseRelation == null ? null : Arrays.copyOf(src.denseRelation, src.denseRelation.length);
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
        return size;
    }

    @Override
    public Zone copy() {
        return new SimplifiedIntDBM(this);
    }

    @Override
    public void up() {
        DenseMatrix dense = restoreDense();
        for (int i = 1; i < size; i++) {
            int idx = flat(i, 0);
            dense.bound[idx] = 0;
            dense.relation[idx] = REL_LT_INF;
        }
        storeAfterMutation(dense);
    }

    @Override
    public void reset(Clock x) {
        Integer index = getClockIndexMap().get(x);
        if (index == null) {
            throw new IllegalArgumentException("Clock " + x + " is not in this DBM");
        }
        DenseMatrix dense = restoreDense();
        for (int i = 0; i < size; i++) {
            if (i == index) {
                continue;
            }
            int idxIndexI = flat(index, i);
            int idxZeroI = flat(0, i);
            dense.bound[idxIndexI] = dense.bound[idxZeroI];
            dense.relation[idxIndexI] = dense.relation[idxZeroI];

            int idxIIndex = flat(i, index);
            int idxIZero = flat(i, 0);
            dense.bound[idxIIndex] = dense.bound[idxIZero];
            dense.relation[idxIIndex] = dense.relation[idxIZero];
        }
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
            int idx = flat(i, i);
            if (dense.relation[idx] != REL_LE_FINITE) {
                return false;
            }
            if (dense.bound[idx] != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void and(Constraint constraint) {
        DenseMatrix dense = restoreDense();
        for (AtomConstraint atom : constraint.getAtomConstraints()) {
            int i = getClockIndexMap().get(atom.getClock1());
            int j = getClockIndexMap().get(atom.getClock2());

            int newBound;
            byte newRelation;
            if (atom.getUpperbound().isInfinite()) {
                newBound = 0;
                newRelation = REL_LT_INF;
            } else if (atom.getUpperbound().isMinus_infinite()) {
                throw new IllegalArgumentException("SimplifiedIntDBM does not support -infinite upper bound constraints");
            } else {
                newBound = toIntBound(atom.getUpperbound());
                newRelation = atom.isClosed() ? REL_LE_FINITE : REL_LT_FINITE;
            }

            int idx = flat(i, j);
            int cmp = compareCell(dense.bound[idx], dense.relation[idx], newBound, newRelation);
            if (cmp > 0) {
                dense.bound[idx] = newBound;
                dense.relation[idx] = newRelation;
            } else if (cmp == 0 && newRelation != REL_LT_INF) {
                dense.relation[idx] = stricterRelation(dense.relation[idx], newRelation);
            }
        }
        storeAfterMutation(dense);
    }

    @Override
    public boolean includes(Zone zone) {
        if (zone.getSize() != size) {
            throw new RuntimeException(DBMExceptions.INCOMPARABLE_DBMS);
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Value value1 = getValue(i, j);
                boolean closed1 = getClosed(i, j);
                Value value2 = zone.getValue(i, j);
                boolean closed2 = zone.getClosed(i, j);
                int relationCmp = Value.compare(value1, value2);
                if (relationCmp == Value.LESS) {
                    return false;
                } else if (relationCmp == Value.EQ) {
                    if (!closed1 && closed2) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public Value getValue(int i, int j) {
        checkIndex(i, j);
        byte relation;
        int bound;
        if (denseMode) {
            int idx = flat(i, j);
            relation = denseRelation[idx];
            bound = denseBound[idx];
        } else {
            int idx = findSparseIndex(i, j);
            if (idx < 0) {
                relation = DEFAULT_RELATION;
                bound = DEFAULT_BOUND;
            } else {
                relation = sparseRelation[idx];
                bound = sparseBound[idx];
            }
        }
        if (relation == REL_LT_INF) {
            return Value.INFINITE;
        }
        return new Value(bound);
    }

    @Override
    public boolean getClosed(int i, int j) {
        checkIndex(i, j);
        byte relation;
        if (denseMode) {
            relation = denseRelation[flat(i, j)];
        } else {
            int idx = findSparseIndex(i, j);
            relation = idx < 0 ? DEFAULT_RELATION : sparseRelation[idx];
        }
        return relation == REL_LE_FINITE;
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
                while (cursor < sparseCount) {
                    int k = cursor++;
                    int i = sparseI[k];
                    int j = sparseJ[k];
                    byte relation = sparseRelation[k];
                    if (relation == REL_LT_INF) {
                        continue;
                    }
                    int bound = sparseBound[k];
                    boolean closed = relation == REL_LE_FINITE;
                    if (i == j && closed && bound == 0) {
                        continue;
                    }
                    return new AtomConstraint(clockList.get(i), clockList.get(j), closed, bound);
                }
                return null;
            }
        };
    }

    @Override
    public String toString() {
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
                byte relation;
                int bound;
                if (denseMode) {
                    int idx = flat(i, j);
                    relation = denseRelation[idx];
                    bound = denseBound[idx];
                } else {
                    int idx = findSparseIndex(i, j);
                    if (idx < 0) {
                        relation = DEFAULT_RELATION;
                        bound = DEFAULT_BOUND;
                    } else {
                        relation = sparseRelation[idx];
                        bound = sparseBound[idx];
                    }
                }
                String element = cellToString(bound, relation);
                elements[i][j] = element;
                cellWidth = Math.max(cellWidth, element.length());
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

    private void applyCanonical(DenseMatrix dense) {
        for (int k = 0; k < size; k++) {
            for (int i = 0; i < size; i++) {
                int ik = flat(i, k);
                for (int j = 0; j < size; j++) {
                    int kj = flat(k, j);
                    int ij = flat(i, j);

                    byte viaRelation = composeRelation(dense.relation[ik], dense.relation[kj]);
                    int viaBound = composeBound(dense.bound[ik], dense.relation[ik], dense.bound[kj], dense.relation[kj]);
                    int cmp = compareCell(dense.bound[ij], dense.relation[ij], viaBound, viaRelation);
                    if (cmp > 0) {
                        dense.bound[ij] = viaBound;
                        dense.relation[ij] = viaRelation;
                    } else if (cmp == 0 && viaRelation != REL_LT_INF) {
                        dense.relation[ij] = stricterRelation(dense.relation[ij], viaRelation);
                    }
                }
            }
        }
    }

    private DenseMatrix restoreDense() {
        int cellCount = size * size;
        DenseMatrix dense = new DenseMatrix(cellCount);
        Arrays.fill(dense.bound, DEFAULT_BOUND);
        Arrays.fill(dense.relation, DEFAULT_RELATION);
        if (denseMode) {
            System.arraycopy(denseBound, 0, dense.bound, 0, cellCount);
            System.arraycopy(denseRelation, 0, dense.relation, 0, cellCount);
            return dense;
        }
        for (int k = 0; k < sparseCount; k++) {
            int idx = flat(sparseI[k], sparseJ[k]);
            dense.bound[idx] = sparseBound[k];
            dense.relation[idx] = sparseRelation[k];
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
        int cellCount = size * size;
        for (int idx = 0; idx < cellCount; idx++) {
            if (!isDefaultCell(dense.bound[idx], dense.relation[idx])) {
                count++;
            }
        }
        return count;
    }

    private void setDenseMode(DenseMatrix dense) {
        int cellCount = size * size;
        this.denseMode = true;
        this.denseBound = new int[cellCount];
        this.denseRelation = new byte[cellCount];
        System.arraycopy(dense.bound, 0, this.denseBound, 0, cellCount);
        System.arraycopy(dense.relation, 0, this.denseRelation, 0, cellCount);
        this.sparseCount = 0;
    }

    private void setSparseModeNonDefault(DenseMatrix dense) {
        this.denseMode = false;
        this.denseBound = null;
        this.denseRelation = null;
        this.sparseCount = 0;
        int cellCount = size * size;
        for (int idx = 0; idx < cellCount; idx++) {
            byte relation = dense.relation[idx];
            int bound = dense.bound[idx];
            if (!isDefaultCell(bound, relation)) {
                ensureSparseCapacity(sparseCount + 1);
                sparseI[sparseCount] = idx / size;
                sparseJ[sparseCount] = idx % size;
                sparseBound[sparseCount] = bound;
                sparseRelation[sparseCount] = relation;
                sparseCount++;
            }
        }
    }

    private void setSparseModeWithMinimization(DenseMatrix canonicalDense) {
        setSparseModeNonDefault(canonicalDense);
        int originalCount = sparseCount;
        int[] candidateI = new int[originalCount];
        int[] candidateJ = new int[originalCount];
        System.arraycopy(sparseI, 0, candidateI, 0, originalCount);
        System.arraycopy(sparseJ, 0, candidateJ, 0, originalCount);

        for (int k = 0; k < originalCount; k++) {
            int i = candidateI[k];
            int j = candidateJ[k];
            int idx = findSparseIndex(i, j);
            if (idx < 0) {
                continue;
            }

            int removedBound = sparseBound[idx];
            byte removedRelation = sparseRelation[idx];
            removeSparseAt(idx);

            DenseMatrix rebuilt = restoreDense();
            applyCanonical(rebuilt);
            if (!sameAs(canonicalDense, rebuilt)) {
                addSparseEntry(i, j, removedBound, removedRelation);
            }
        }
    }

    private boolean sameAs(DenseMatrix a, DenseMatrix b) {
        int cellCount = size * size;
        for (int idx = 0; idx < cellCount; idx++) {
            if (a.bound[idx] != b.bound[idx]) {
                return false;
            }
            if (a.relation[idx] != b.relation[idx]) {
                return false;
            }
        }
        return true;
    }

    private int findSparseIndex(int i, int j) {
        for (int k = 0; k < sparseCount; k++) {
            if (sparseI[k] == i && sparseJ[k] == j) {
                return k;
            }
        }
        return -1;
    }

    private void addSparseEntry(int i, int j, int bound, byte relation) {
        int idx = findSparseIndex(i, j);
        if (idx >= 0) {
            sparseBound[idx] = bound;
            sparseRelation[idx] = relation;
            return;
        }
        ensureSparseCapacity(sparseCount + 1);
        sparseI[sparseCount] = i;
        sparseJ[sparseCount] = j;
        sparseBound[sparseCount] = bound;
        sparseRelation[sparseCount] = relation;
        sparseCount++;
    }

    private void removeSparseAt(int idx) {
        int last = sparseCount - 1;
        if (idx != last) {
            sparseI[idx] = sparseI[last];
            sparseJ[idx] = sparseJ[last];
            sparseBound[idx] = sparseBound[last];
            sparseRelation[idx] = sparseRelation[last];
        }
        sparseCount--;
    }

    private void ensureSparseCapacity(int need) {
        int cap = sparseI.length;
        if (need <= cap) {
            return;
        }
        int newCap = Math.max(need, cap * 2);
        sparseI = Arrays.copyOf(sparseI, newCap);
        sparseJ = Arrays.copyOf(sparseJ, newCap);
        sparseBound = Arrays.copyOf(sparseBound, newCap);
        sparseRelation = Arrays.copyOf(sparseRelation, newCap);
    }

    private int flat(int i, int j) {
        return i * size + j;
    }

    private static boolean isDefaultCell(int bound, byte relation) {
        return bound == DEFAULT_BOUND && relation == DEFAULT_RELATION;
    }

    private static String cellToString(int bound, byte relation) {
        StringBuilder element = new StringBuilder();
        element.append('(');
        if (relation == REL_LE_FINITE) {
            element.append("<=");
            element.append(',').append(bound);
        } else if (relation == REL_LT_FINITE) {
            element.append("< ");
            element.append(',').append(bound);
        } else {
            element.append("< ");
            element.append(",∞");
        }
        element.append(")");
        return element.toString();
    }

    private static byte composeRelation(byte r1, byte r2) {
        if (r1 == REL_LT_INF || r2 == REL_LT_INF) {
            return REL_LT_INF;
        }
        return (r1 == REL_LE_FINITE && r2 == REL_LE_FINITE) ? REL_LE_FINITE : REL_LT_FINITE;
    }

    private static int composeBound(int b1, byte r1, int b2, byte r2) {
        if (r1 == REL_LT_INF || r2 == REL_LT_INF) {
            return 0;
        }
        long sum = (long) b1 + b2;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }

    private static int compareCell(int b1, byte r1, int b2, byte r2) {
        if (r1 == REL_LT_INF) {
            if (r2 == REL_LT_INF) {
                return 0;
            }
            return 1;
        }
        if (r2 == REL_LT_INF) {
            return -1;
        }
        if (b1 != b2) {
            return Integer.compare(b1, b2);
        }
        if (r1 == r2) {
            return 0;
        }
        if (r1 == REL_LT_FINITE && r2 == REL_LE_FINITE) {
            return -1;
        }
        if (r1 == REL_LE_FINITE && r2 == REL_LT_FINITE) {
            return 1;
        }
        return 0;
    }

    private static byte stricterRelation(byte r1, byte r2) {
        if (r1 == REL_LT_INF) {
            return r2;
        }
        if (r2 == REL_LT_INF) {
            return r1;
        }
        return (r1 == REL_LT_FINITE || r2 == REL_LT_FINITE) ? REL_LT_FINITE : REL_LE_FINITE;
    }

    private static int toIntBound(Value value) {
        if (!value.isInteger()) {
            throw new IllegalArgumentException("SimplifiedIntDBM requires integer finite bounds");
        }
        String s = value.getIntegerPart().toString();
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return Integer.parseInt(s);
    }

    private void checkIndex(int i, int j) {
        if (i < 0 || i >= size || j < 0 || j >= size) {
            throw new IndexOutOfBoundsException("index out of DBM range: (" + i + "," + j + "), size=" + size);
        }
    }
}
