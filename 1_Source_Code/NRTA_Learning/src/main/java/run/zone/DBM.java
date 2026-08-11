package run.zone;

import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import learning.LearningSetting;
import learning.teachers.TeacherSetting;
import exception.DBMExceptions;
import ta.AtomConstraint;
import ta.Clock;
import ta.Constraint;
import ta.Value;
import tools.FormatOutput.StringMaker;

import java.util.*;

public class DBM extends Zone {
    // Upper bound values of each cell in DBM
    private final Value[][] upper_bound_matrix;
    private final boolean[][] closed_matrix;

    private static int globalSize() {
        return TeacherSetting.getDBMClockSize();
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
        return globalSize();
    }


    public DBM(){
        int size = globalSize();
        this.upper_bound_matrix = new Value[size][size];
        this.closed_matrix = new boolean[size][size];
        int i;
        for(i=0;i<size;i++){
            for(int j=0;j<size;j++){
                upper_bound_matrix[i][j] = Value.ZERO;
                closed_matrix[i][j] = true;
            }
        }
    }

    // DBM copy constructor
    private DBM(DBM copy_source){
        int size = copy_source.upper_bound_matrix.length;
        // These need deep copy
        upper_bound_matrix = new Value[size][size];
        closed_matrix = new boolean[size][size];
        for(int i=0;i<size;i++){
            for(int j=0;j<size;j++){
                // Since all operations involving Value create a new Value object, we can safely copy here
                upper_bound_matrix[i][j] = copy_source.upper_bound_matrix[i][j];
                closed_matrix[i][j] = copy_source.closed_matrix[i][j];
            }
        }
    }

    @Override
    public DBM copy(){
        return new DBM(this);
    }

    // The following operations will affect the instance, must copy before operating
    @Override
    public void up(){
        int size = upper_bound_matrix.length;
        for(int i=1;i<size;i++){
            upper_bound_matrix[i][0] = Value.INFINITE;
            closed_matrix[i][0] = false;
        }
    }

    @Override
    public void reset(Clock x){
        Integer index = getClockIndexMap().get(x);
        if(index == null) {
            throw new IllegalArgumentException("Clock " + x + " is not in this DBM");
        }
        int size = upper_bound_matrix.length;
        for(int i=0;i<size;i++){
            if(i == index)
                continue;
            upper_bound_matrix[index][i] = upper_bound_matrix[0][i];
            upper_bound_matrix[i][index] = upper_bound_matrix[i][0];

            closed_matrix[index][i] = closed_matrix[0][i];
            closed_matrix[i][index] = closed_matrix[i][0];
        }
    }

    @Override
    public void canonical(){
        int size = upper_bound_matrix.length;
        for(int k = 0; k < size; k++){
            for(int i = 0; i < size; i++){
                for(int j = 0; j < size; j++){
                    Value v1 = upper_bound_matrix[i][j];
                    boolean closed1 = closed_matrix[i][j];
                    Value v = Value.add(upper_bound_matrix[i][k],upper_bound_matrix[k][j]);
                    boolean closed = closed_matrix[i][k] && closed_matrix[k][j];

                    int relation = Value.compare(v1,v);
                    // Make constraints as tight as possible
                    if(relation == Value.GREATER) {
                        upper_bound_matrix[i][j] = v;
                        closed_matrix[i][j] = closed;
                    }
                    else if(relation == Value.EQ)
                        closed_matrix[i][j] = closed1 && closed;
                }
            }
        }
    }

    @Override
    public boolean isConsistent(){
        int size = upper_bound_matrix.length;
        for(int i=0;i<size;i++){
            if(Value.compare(upper_bound_matrix[i][i],Value.ZERO)!=Value.EQ) return false;
            if(!closed_matrix[i][i]) return false;
        }
        return true;
    }

    // DBM add constraint
    @Override
    public void and(Constraint constraint){
        for(AtomConstraint atomConstraint : constraint.getAtomConstraints())
            this.and(atomConstraint);
    }

    private void and(AtomConstraint atomConstraint){
        int i = getClockIndexMap().get(atomConstraint.getClock1());
        int j = getClockIndexMap().get(atomConstraint.getClock2());
        Value value = atomConstraint.getUpperbound();
        boolean closed = atomConstraint.isClosed();
        Value value1 = upper_bound_matrix[i][j];
        boolean closed1 = closed_matrix[i][j];

        int relation = Value.compare(value1, value);
        if(relation==Value.GREATER){
            upper_bound_matrix[i][j] = value;
            closed_matrix[i][j] = closed;
        } else if (relation==Value.EQ){
            closed_matrix[i][j] = closed1 && closed;
        }
    }

    private boolean includesDbm(DBM dbm){
        int size = upper_bound_matrix.length;
        for(int i = 0; i < size; i++){
            for(int j = 0; j < size; j++){
                Value value1 = this.upper_bound_matrix[i][j];
                boolean closed1 = this.closed_matrix[i][j];
                Value value2 = dbm.upper_bound_matrix[i][j];
                boolean closed2 = dbm.closed_matrix[i][j];

                int relation = Value.compare(value1,value2);
                if(relation==Value.LESS)  return false;
                else if (relation==Value.EQ) {
                    if(!closed1 && closed2) return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean includes(Zone zone){
        if (!(zone instanceof DBM dbm)) {
            throw new RuntimeException(DBMExceptions.INCOMPARABLE_DBMS);
        }
        return includesDbm(dbm);
    }

    public void extrapolate(Map<Clock, Value> lowerBounds,
                            Map<Clock, Value> upperBounds,
                            Map<Clock, Value> mBounds,
                            TeacherSetting.ExtrapolationType type) {
        if (type == TeacherSetting.ExtrapolationType.NONE) {
            return;
        }
        List<Clock> clocks = getClockList();
        int size = upper_bound_matrix.length;
        for (int i = 0; i < size; i++) {
            Clock ci = clocks.get(i);
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }
                Value current = upper_bound_matrix[i][j];
                if (current.isInfinite() || current.isMinus_infinite()) {
                    continue;
                }

                // Cap upper constraints (x_i - x_j <= c) using bound of x_i.
                if (i != 0) {
                    Value upperCap = getUpperCap(ci, upperBounds, mBounds, type);
                    if (Value.gt(current, upperCap)) {
                        upper_bound_matrix[i][j] = Value.INFINITE;
                        closed_matrix[i][j] = AtomConstraint.LESS;
                        continue;
                    }
                }

                // Cap lower constraints via x_j >= L  <=> 0 - x_j <= -L.
                if (j != 0) {
                    Clock cj = clocks.get(j);
                    Value lowerCap = getLowerCap(cj, lowerBounds, mBounds, type);
                    Value minAllowed = Value.negative(lowerCap);
                    if (Value.lt(current, minAllowed)) {
                        upper_bound_matrix[i][j] = minAllowed;
                        closed_matrix[i][j] = AtomConstraint.LESS_EQ;
                    }
                }
            }
        }
    }

    private static Value getUpperCap(Clock clock,
                                     Map<Clock, Value> upperBounds,
                                     Map<Clock, Value> mBounds,
                                     TeacherSetting.ExtrapolationType type) {
        if (type == TeacherSetting.ExtrapolationType.EXTRA_M) {
            return clampNonNegative(mBounds.get(clock));
        }
        return clampNonNegative(upperBounds.get(clock));
    }

    private static Value getLowerCap(Clock clock,
                                     Map<Clock, Value> lowerBounds,
                                     Map<Clock, Value> mBounds,
                                     TeacherSetting.ExtrapolationType type) {
        if (type == TeacherSetting.ExtrapolationType.EXTRA_M) {
            return clampNonNegative(mBounds.get(clock));
        }
        return clampNonNegative(lowerBounds.get(clock));
    }

    private static Value clampNonNegative(Value value) {
        if (value == null || value.isInfinite() || value.isMinus_infinite()) {
            return Value.ZERO;
        }
        if (Value.lt(value, Value.ZERO)) {
            return Value.ZERO;
        }
        return value;
    }

    public String toString(){
        int size = upper_bound_matrix.length;
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
                if (closed_matrix[i][j]) {
                    element.append("<=");
                } else {
                    element.append("< ");
                }
                element.append(',').append(upper_bound_matrix[i][j]).append(")");
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

    @Override
    public Value getValue(int i, int j){
        return this.upper_bound_matrix[i][j];
    }

    @Override
    public boolean getClosed(int i, int j){
        return this.closed_matrix[i][j];
    }

    @Override
    public Iterator<AtomConstraint> iterator() {
        return defaultEffectiveIterator();
    }

}
