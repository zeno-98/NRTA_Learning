package run.zone;

import ta.Clock;
import ta.Constraint;
import ta.AtomConstraint;
import ta.Value;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Abstract zone representation.
 * DBM is one concrete implementation.
 */
public abstract class Zone implements Iterable<AtomConstraint> {
    public abstract Map<Clock, Integer> getClockIndexMap();

    public abstract List<Clock> getClockList();

    public abstract int getSize();

    public abstract Zone copy();

    public abstract void up();

    public abstract void reset(Clock x);

    public abstract void canonical();

    public abstract boolean isConsistent();

    public abstract void and(Constraint constraint);

    public abstract boolean includes(Zone zone);

    public abstract Value getValue(int i, int j);

    public abstract boolean getClosed(int i, int j);

    @Override
    public abstract Iterator<AtomConstraint> iterator();

    /**
     * Default effective iterator for dense-like DBM implementations.
     * Subclasses can override iterator() for customized "effective constraint" policy.
     */
    protected final Iterator<AtomConstraint> defaultEffectiveIterator() {
        final int size = getSize();
        final List<Clock> clockList = getClockList();
        if (clockList.size() != size) {
            throw new IllegalStateException("Clock list size and DBM size mismatch");
        }

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
                while (cursor < size * size) {
                    int i = cursor / size;
                    int j = cursor % size;
                    cursor++;

                    Value value = getValue(i, j);
                    boolean closed = getClosed(i, j);

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
}
