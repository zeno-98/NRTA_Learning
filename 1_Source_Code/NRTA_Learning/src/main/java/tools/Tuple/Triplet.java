package tools.Tuple;

import java.util.Objects;

public class Triplet<X, Y, Z> {
    protected final X p0;
    protected final Y p1;
    protected final Z p2;

    public Triplet(X p0, Y p1, Z p2) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
    }

    public X getP0() {
        return this.p0;
    }

    public Y getP1() {
        return this.p1;
    }

    public Z getP2() {
        return this.p2;
    }

    public X p0() {
        return this.p0;
    }

    public Y p1() {
        return this.p1;
    }

    public Z p2() {
        return this.p2;
    }

    public Triplet<X, Y, Z> copy() {
        return new Triplet<>(p0, p1, p2);
    }

    @Override
    public String toString() {
        return "Triplet{\n" +
                "p0 = " + p0 +
                ",\np1 = " + p1 +
                ",\np2 = " + p2 +
                "\n}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triplet<?, ?, ?> triplet = (Triplet<?, ?, ?>) o;
        return Objects.equals(p0, triplet.p0)
                && Objects.equals(p1, triplet.p1)
                && Objects.equals(p2, triplet.p2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p0, p1, p2);
    }
}
