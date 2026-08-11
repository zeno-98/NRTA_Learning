package tools.Tuple;

import java.util.Objects;

public class Pair<X, Y> {
    protected final X p0;
    protected final Y p1;

    public Pair(X p0, Y p1) {
        this.p0 = p0;
        this.p1 = p1;
    }

    public X getP0() {
        return this.p0;
    }

    public Y getP1() {
        return this.p1;
    }

    public X p0() {
        return this.p0;
    }

    public Y p1() {
        return this.p1;
    }

    public Pair<X, Y> copy() {
        return new Pair<>(p0, p1);
    }

    @Override
    public String toString() {
        return "Pair{\n" +
                "p0 = " + p0 +
                ",\np1 = " + p1 +
                "\n}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(p0, pair.p0) && Objects.equals(p1, pair.p1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p0, p1);
    }
}
