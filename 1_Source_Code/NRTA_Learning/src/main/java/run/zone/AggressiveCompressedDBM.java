package run.zone;

import exception.DBMExceptions;
import ta.AtomConstraint;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.FormatOutput.StringMaker;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggressive compressed DBM:
 * - relation matrix packed in 2 bits per cell
 * - finite bound matrix stored as short with overflow side-list
 * - copy-on-write storage sharing for Zone.copy()
 * - storage interning for identical matrices
 *
 * This implementation keeps full semantics while prioritizing space reduction.
 */
public class AggressiveCompressedDBM extends Zone {
    private static final byte REL_LT_FINITE = 0;
    private static final byte REL_LE_FINITE = 1;
    private static final byte REL_LT_INF = 2;

    private static final short BOUND_OVERFLOW_SENTINEL = Short.MIN_VALUE;

    private final int size;
    private final int cellCount;

    private Storage storage;
    private boolean sharedStorageView;

    private static final ConcurrentHashMap<Long, List<WeakReference<Storage>>> INTERN_POOL = new ConcurrentHashMap<>();

    private static final class OverflowList {
        private int[] indices;
        private int[] values;
        private int count;

        private OverflowList() {
            this.indices = new int[8];
            this.values = new int[8];
            this.count = 0;
        }

        private OverflowList(OverflowList src) {
            this.indices = new int[src.indices.length];
            this.values = new int[src.values.length];
            System.arraycopy(src.indices, 0, this.indices, 0, src.count);
            System.arraycopy(src.values, 0, this.values, 0, src.count);
            this.count = src.count;
        }

        private int get(int index, int defaultValue) {
            for (int i = 0; i < count; i++) {
                if (indices[i] == index) {
                    return values[i];
                }
            }
            return defaultValue;
        }

        private void put(int index, int value) {
            for (int i = 0; i < count; i++) {
                if (indices[i] == index) {
                    values[i] = value;
                    return;
                }
            }
            ensureCapacity(count + 1);
            indices[count] = index;
            values[count] = value;
            count++;
        }

        private void clear() {
            count = 0;
        }

        private void ensureCapacity(int need) {
            int cap = indices.length;
            if (need <= cap) {
                return;
            }
            int newCap = Math.max(need, cap * 2);
            int[] newIndices = new int[newCap];
            int[] newValues = new int[newCap];
            System.arraycopy(indices, 0, newIndices, 0, count);
            System.arraycopy(values, 0, newValues, 0, count);
            indices = newIndices;
            values = newValues;
        }
    }

    private static final class Storage {
        private final int cellCount;
        private final long[] relationBits;
        private final short[] bounds;
        private final OverflowList overflow;

        private Storage(int cellCount) {
            this.cellCount = cellCount;
            this.relationBits = new long[(cellCount * 2 + 63) >>> 6];
            this.bounds = new short[cellCount];
            this.overflow = new OverflowList();
            for (int idx = 0; idx < cellCount; idx++) {
                setRelation(relationBits, idx, REL_LE_FINITE);
                bounds[idx] = 0;
            }
        }

        private Storage(Storage src) {
            this.cellCount = src.cellCount;
            this.relationBits = new long[src.relationBits.length];
            this.bounds = new short[src.bounds.length];
            this.overflow = new OverflowList(src.overflow);
            System.arraycopy(src.relationBits, 0, this.relationBits, 0, src.relationBits.length);
            System.arraycopy(src.bounds, 0, this.bounds, 0, src.bounds.length);
        }
    }

    private static final class WorkingMatrix {
        private final int[] bound;
        private final byte[] relation;

        private WorkingMatrix(int cellCount) {
            this.bound = new int[cellCount];
            this.relation = new byte[cellCount];
        }
    }

    public AggressiveCompressedDBM() {
        this.size = TeacherSetting.getDBMClockSize();
        this.cellCount = size * size;
        this.storage = new Storage(cellCount);
        this.sharedStorageView = false;
    }

    private AggressiveCompressedDBM(AggressiveCompressedDBM src) {
        this.size = src.size;
        this.cellCount = src.cellCount;
        this.storage = src.storage;
        this.sharedStorageView = true;
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
        this.sharedStorageView = true;
        return new AggressiveCompressedDBM(this);
    }

    @Override
    public void up() {
        WorkingMatrix matrix = restoreWorkingMatrix();
        for (int i = 1; i < size; i++) {
            int idx = flat(i, 0);
            matrix.relation[idx] = REL_LT_INF;
            matrix.bound[idx] = 0;
        }
        storeFromWorkingMatrix(matrix, true);
    }

    @Override
    public void reset(Clock x) {
        Integer index = getClockIndexMap().get(x);
        if (index == null) {
            throw new IllegalArgumentException("Clock " + x + " is not in this DBM");
        }
        WorkingMatrix matrix = restoreWorkingMatrix();
        for (int i = 0; i < size; i++) {
            if (i == index) {
                continue;
            }
            int idxIndexI = flat(index, i);
            int idxZeroI = flat(0, i);
            matrix.relation[idxIndexI] = matrix.relation[idxZeroI];
            matrix.bound[idxIndexI] = matrix.bound[idxZeroI];

            int idxIIndex = flat(i, index);
            int idxIZero = flat(i, 0);
            matrix.relation[idxIIndex] = matrix.relation[idxIZero];
            matrix.bound[idxIIndex] = matrix.bound[idxIZero];
        }
        storeFromWorkingMatrix(matrix, true);
    }

    @Override
    public void canonical() {
        WorkingMatrix matrix = restoreWorkingMatrix();
        for (int k = 0; k < size; k++) {
            for (int i = 0; i < size; i++) {
                int ik = flat(i, k);
                for (int j = 0; j < size; j++) {
                    int kj = flat(k, j);
                    int ij = flat(i, j);
                    byte viaRelation = composeRelation(matrix.relation[ik], matrix.relation[kj]);
                    int viaBound = composeBound(matrix.bound[ik], matrix.relation[ik], matrix.bound[kj], matrix.relation[kj]);
                    int cmp = compareCell(matrix.bound[ij], matrix.relation[ij], viaBound, viaRelation);
                    if (cmp > 0) {
                        matrix.bound[ij] = viaBound;
                        matrix.relation[ij] = viaRelation;
                    } else if (cmp == 0 && viaRelation != REL_LT_INF) {
                        matrix.relation[ij] = stricterRelation(matrix.relation[ij], viaRelation);
                    }
                }
            }
        }
        storeFromWorkingMatrix(matrix, true);
    }

    @Override
    public boolean isConsistent() {
        for (int i = 0; i < size; i++) {
            int idx = flat(i, i);
            byte relation = getRelation(storage.relationBits, idx);
            if (relation != REL_LE_FINITE) {
                return false;
            }
            int bound = getFiniteBoundAt(idx);
            if (bound != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void and(Constraint constraint) {
        WorkingMatrix matrix = restoreWorkingMatrix();
        for (AtomConstraint atom : constraint.getAtomConstraints()) {
            int i = getClockIndexMap().get(atom.getClock1());
            int j = getClockIndexMap().get(atom.getClock2());
            int idx = flat(i, j);

            byte newRelation;
            int newBound;
            if (atom.getUpperbound().isInfinite()) {
                newRelation = REL_LT_INF;
                newBound = 0;
            } else if (atom.getUpperbound().isMinus_infinite()) {
                throw new IllegalArgumentException("AggressiveCompressedDBM does not support -infinite upper bound constraints");
            } else {
                newRelation = atom.isClosed() ? REL_LE_FINITE : REL_LT_FINITE;
                newBound = toIntBound(atom.getUpperbound());
            }

            int cmp = compareCell(matrix.bound[idx], matrix.relation[idx], newBound, newRelation);
            if (cmp > 0) {
                matrix.bound[idx] = newBound;
                matrix.relation[idx] = newRelation;
            } else if (cmp == 0 && newRelation != REL_LT_INF) {
                matrix.relation[idx] = stricterRelation(matrix.relation[idx], newRelation);
            }
        }
        storeFromWorkingMatrix(matrix, true);
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
        int idx = flat(i, j);
        byte relation = getRelation(storage.relationBits, idx);
        if (relation == REL_LT_INF) {
            return Value.INFINITE;
        }
        return new Value(getFiniteBoundAt(idx));
    }

    @Override
    public boolean getClosed(int i, int j) {
        checkIndex(i, j);
        return getRelation(storage.relationBits, flat(i, j)) == REL_LE_FINITE;
    }

    @Override
    public Iterator<AtomConstraint> iterator() {
        return defaultEffectiveIterator();
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
                int idx = flat(i, j);
                byte relation = getRelation(storage.relationBits, idx);
                String element;
                if (relation == REL_LT_INF) {
                    element = "(< ,∞)";
                } else if (relation == REL_LE_FINITE) {
                    element = "(<=," + getFiniteBoundAt(idx) + ")";
                } else {
                    element = "(< ," + getFiniteBoundAt(idx) + ")";
                }
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

    private void ensureWritable() {
        if (!sharedStorageView) {
            return;
        }
        storage = new Storage(storage);
        sharedStorageView = false;
    }

    private WorkingMatrix restoreWorkingMatrix() {
        WorkingMatrix matrix = new WorkingMatrix(cellCount);
        for (int idx = 0; idx < cellCount; idx++) {
            byte relation = getRelation(storage.relationBits, idx);
            matrix.relation[idx] = relation;
            if (relation == REL_LT_INF) {
                matrix.bound[idx] = 0;
            } else {
                matrix.bound[idx] = getFiniteBoundAt(idx);
            }
        }
        return matrix;
    }

    private void storeFromWorkingMatrix(WorkingMatrix matrix, boolean intern) {
        ensureWritable();
        storage.overflow.clear();
        for (int idx = 0; idx < cellCount; idx++) {
            byte relation = matrix.relation[idx];
            setRelation(storage.relationBits, idx, relation);
            if (relation == REL_LT_INF) {
                storage.bounds[idx] = 0;
                continue;
            }
            int bound = matrix.bound[idx];
            if (bound > Short.MIN_VALUE && bound <= Short.MAX_VALUE) {
                storage.bounds[idx] = (short) bound;
            } else {
                storage.bounds[idx] = BOUND_OVERFLOW_SENTINEL;
                storage.overflow.put(idx, bound);
            }
        }
        if (intern) {
            storage = internStorage(storage);
            sharedStorageView = true;
        }
    }

    private int getFiniteBoundAt(int idx) {
        short stored = storage.bounds[idx];
        if (stored != BOUND_OVERFLOW_SENTINEL) {
            return stored;
        }
        return storage.overflow.get(idx, 0);
    }

    private int flat(int i, int j) {
        return i * size + j;
    }

    private static byte getRelation(long[] relationBits, int idx) {
        int bitPos = idx << 1;
        int arr = bitPos >>> 6;
        int shift = bitPos & 63;
        return (byte) ((relationBits[arr] >>> shift) & 0b11L);
    }

    private static void setRelation(long[] relationBits, int idx, byte relation) {
        int bitPos = idx << 1;
        int arr = bitPos >>> 6;
        int shift = bitPos & 63;
        long mask = 0b11L << shift;
        relationBits[arr] = (relationBits[arr] & ~mask) | (((long) relation & 0b11L) << shift);
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
            return r2 == REL_LT_INF ? 0 : 1;
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
            throw new IllegalArgumentException("AggressiveCompressedDBM requires integer finite bounds");
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

    private static Storage internStorage(Storage candidate) {
        long hash = hashStorage(candidate);
        List<WeakReference<Storage>> bucket = INTERN_POOL.computeIfAbsent(hash, k -> new ArrayList<>());
        synchronized (bucket) {
            bucket.removeIf(ref -> ref.get() == null);
            for (WeakReference<Storage> ref : bucket) {
                Storage existing = ref.get();
                if (existing != null && equalsStorage(existing, candidate)) {
                    return existing;
                }
            }
            bucket.add(new WeakReference<>(candidate));
            return candidate;
        }
    }

    private static long hashStorage(Storage s) {
        long h = 1469598103934665603L;
        for (long v : s.relationBits) {
            h ^= v;
            h *= 1099511628211L;
        }
        for (short b : s.bounds) {
            h ^= b;
            h *= 1099511628211L;
        }
        h ^= s.overflow.count;
        h *= 1099511628211L;
        for (int i = 0; i < s.overflow.count; i++) {
            h ^= s.overflow.indices[i];
            h *= 1099511628211L;
            h ^= s.overflow.values[i];
            h *= 1099511628211L;
        }
        return h;
    }

    private static boolean equalsStorage(Storage a, Storage b) {
        if (a.cellCount != b.cellCount) {
            return false;
        }
        if (a.relationBits.length != b.relationBits.length) {
            return false;
        }
        for (int i = 0; i < a.relationBits.length; i++) {
            if (a.relationBits[i] != b.relationBits[i]) {
                return false;
            }
        }
        if (a.bounds.length != b.bounds.length) {
            return false;
        }
        for (int i = 0; i < a.bounds.length; i++) {
            if (a.bounds[i] != b.bounds[i]) {
                return false;
            }
        }
        if (a.overflow.count != b.overflow.count) {
            return false;
        }
        for (int i = 0; i < a.overflow.count; i++) {
            if (a.overflow.indices[i] != b.overflow.indices[i]) {
                return false;
            }
            if (a.overflow.values[i] != b.overflow.values[i]) {
                return false;
            }
        }
        return true;
    }
}
