package run.zone;

import exception.DBMExceptions;
import ta.AtomConstraint;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.FormatOutput.StringMaker;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Integer-only DBM implementation.
 *
 * Cell encoding:
 * - REL_LT_FINITE : x_i - x_j <  bound[i][j]
 * - REL_LE_FINITE : x_i - x_j <= bound[i][j]
 * - REL_LT_INF    : x_i - x_j <  +inf
 *
 * This class assumes finite bounds are integer-valued.
 */
public class IntDBM extends Zone {
    private static final byte REL_LT_FINITE = 0;
    private static final byte REL_LE_FINITE = 1;
    private static final byte REL_LT_INF = 2;

    private final int[][] bound;
    private final byte[][] relation;

    private static int globalSize() {
        return TeacherSetting.getDBMClockSize();
    }

    public IntDBM() {
        int size = globalSize();
        this.bound = new int[size][size];
        this.relation = new byte[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                bound[i][j] = 0;
                relation[i][j] = REL_LE_FINITE;
            }
        }
    }

    private IntDBM(IntDBM src) {
        int size = src.bound.length;
        this.bound = new int[size][size];
        this.relation = new byte[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(src.bound[i], 0, this.bound[i], 0, size);
            System.arraycopy(src.relation[i], 0, this.relation[i], 0, size);
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
        return bound.length;
    }

    @Override
    public Zone copy() {
        return new IntDBM(this);
    }

    @Override
    public void up() {
        int size = getSize();
        for (int i = 1; i < size; i++) {
            relation[i][0] = REL_LT_INF;
            bound[i][0] = 0;
        }
    }

    @Override
    public void reset(Clock x) {
        Integer index = getClockIndexMap().get(x);
        if (index == null) {
            throw new IllegalArgumentException("Clock " + x + " is not in this DBM");
        }
        int size = getSize();
        for (int i = 0; i < size; i++) {
            if (i == index) {
                continue;
            }
            bound[index][i] = bound[0][i];
            bound[i][index] = bound[i][0];
            relation[index][i] = relation[0][i];
            relation[i][index] = relation[i][0];
        }
    }

    @Override
    public void canonical() {
        int size = getSize();
        for (int k = 0; k < size; k++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    byte viaRel = composeRelation(relation[i][k], relation[k][j]);
                    int viaBound = composeBound(bound[i][k], relation[i][k], bound[k][j], relation[k][j]);

                    int cmp = compareCell(bound[i][j], relation[i][j], viaBound, viaRel);
                    if (cmp > 0) {
                        // current is looser, replace with tighter via-k
                        bound[i][j] = viaBound;
                        relation[i][j] = viaRel;
                    } else if (cmp == 0 && viaRel != REL_LT_INF) {
                        // same finite bound, keep stricter relation
                        relation[i][j] = stricterRelation(relation[i][j], viaRel);
                    }
                }
            }
        }
    }

    @Override
    public boolean isConsistent() {
        int size = getSize();
        for (int i = 0; i < size; i++) {
            if (relation[i][i] != REL_LE_FINITE) {
                return false;
            }
            if (bound[i][i] != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void and(Constraint constraint) {
        for (AtomConstraint atom : constraint.getAtomConstraints()) {
            int i = getClockIndexMap().get(atom.getClock1());
            int j = getClockIndexMap().get(atom.getClock2());
            byte newRel;
            int newBound;
            if (atom.getUpperbound().isInfinite()) {
                newRel = REL_LT_INF;
                newBound = 0;
            } else if (atom.getUpperbound().isMinus_infinite()) {
                throw new IllegalArgumentException("IntDBM does not support -infinite upper bound constraints");
            } else {
                if (!atom.getUpperbound().isInteger()) {
                    throw new IllegalArgumentException("IntDBM requires integer finite bounds");
                }
                newRel = atom.isClosed() ? REL_LE_FINITE : REL_LT_FINITE;
                newBound = toIntBound(atom.getUpperbound());
            }

            int cmp = compareCell(bound[i][j], relation[i][j], newBound, newRel);
            if (cmp > 0) {
                bound[i][j] = newBound;
                relation[i][j] = newRel;
            } else if (cmp == 0 && newRel != REL_LT_INF) {
                relation[i][j] = stricterRelation(relation[i][j], newRel);
            }
        }
    }

    @Override
    public boolean includes(Zone zone) {
        if (zone.getSize() != getSize()) {
            throw new RuntimeException(DBMExceptions.INCOMPARABLE_DBMS);
        }
        int size = getSize();
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
        if (relation[i][j] == REL_LT_INF) {
            return Value.INFINITE;
        }
        return new Value(bound[i][j]);
    }

    @Override
    public boolean getClosed(int i, int j) {
        checkIndex(i, j);
        return relation[i][j] == REL_LE_FINITE;
    }

    @Override
    public Iterator<AtomConstraint> iterator() {
        return defaultEffectiveIterator();
    }

    @Override
    public String toString() {
        int size = getSize();
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
                if (relation[i][j] == REL_LE_FINITE) {
                    element.append("<=");
                    element.append(',').append(bound[i][j]);
                } else if (relation[i][j] == REL_LT_FINITE) {
                    element.append("< ");
                    element.append(',').append(bound[i][j]);
                } else {
                    element.append("< ");
                    element.append(",∞");
                }
                element.append(")");
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

    /**
     * Compare tightness: smaller means stricter.
     * return <0 if (b1,r1) stricter than (b2,r2)
     * return  0 if equivalent
     * return >0 if (b1,r1) looser than (b2,r2)
     */
    private static int compareCell(int b1, byte r1, int b2, byte r2) {
        if (r1 == REL_LT_INF) {
            if (r2 == REL_LT_INF) return 0;
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
        if (r1 == REL_LT_INF) return r2;
        if (r2 == REL_LT_INF) return r1;
        return (r1 == REL_LT_FINITE || r2 == REL_LT_FINITE) ? REL_LT_FINITE : REL_LE_FINITE;
    }

    private void checkIndex(int i, int j) {
        int size = getSize();
        if (i < 0 || i >= size || j < 0 || j >= size) {
            throw new IndexOutOfBoundsException("index out of DBM range: (" + i + "," + j + "), size=" + size);
        }
    }

    private static int toIntBound(Value value) {
        if (!value.isInteger()) {
            throw new IllegalArgumentException("IntDBM requires integer finite bounds");
        }
        String s = value.getIntegerPart().toString();
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return Integer.parseInt(s);
    }
}
