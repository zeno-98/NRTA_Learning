package learning.learners.multi_clocks_tree;

import language.words.ResetLogicalTimedWord;
import lombok.Getter;
import ta.Clock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class BoolMatrix {
    public static BoolMatrix EMPTY = new BoolMatrix();

    private final boolean[][] bMtx;
    private boolean last;

    private final int clockCount;
    private final int vecLength;

    public BoolMatrix(ResetLogicalTimedWord resetWord, List<Clock> sortedClocks, int expectedWordLength, boolean isAccepted) {
        assert resetWord.getLength() <= expectedWordLength;
        this.clockCount = sortedClocks.size();
        this.vecLength = expectedWordLength + 1;
        this.bMtx = new boolean[expectedWordLength][this.clockCount];
        for (int i = 0; i < resetWord.getLength(); i++) {
            for (int j = 0; j < this.clockCount; j++) {
                this.bMtx[i][j] = resetWord.getTimedAction(i).getReset_clocks().contains(sortedClocks.get(j));
            }
        }

        for (int i = resetWord.getLength(); i < expectedWordLength; i++) {
            for (int j = 0; j < this.clockCount; j++) {
                this.bMtx[i][j] = true;
            }
        }
        this.last = isAccepted;
    }

    public BoolMatrix() {
        this.bMtx = new boolean[0][0];
        this.clockCount = 0;
        this.vecLength = 0;
    }

    public Set<Clock> getResetClockSet(int idx, List<Clock> sortedClocks) {
        assert idx < this.vecLength - 1;
        assert this.clockCount == sortedClocks.size();
        Set<Clock> resetClockSet = new HashSet<>();
        for (int j = 0; j < this.clockCount; j++) {
            if (this.bMtx[idx][j]) {
                resetClockSet.add(sortedClocks.get(j));
            }
        }
        return resetClockSet;
    }

    public boolean getLast() {
        assert this.vecLength > 0;
        return this.last;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.deepHashCode(bMtx) + Boolean.hashCode(last);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        BoolMatrix other = (BoolMatrix) obj;
        return Arrays.deepEquals(this.bMtx, other.bMtx) && this.last == other.last;
    }

    @Override
    public String toString() {
        if (this.vecLength == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (boolean[] bs : this.bMtx) {
            for (boolean b : bs) {
                sb.append(b ? "+," : "-,");
            }
        }
        sb.append(this.last ? "+]" : "-]");
        return sb.toString();
    }
}
