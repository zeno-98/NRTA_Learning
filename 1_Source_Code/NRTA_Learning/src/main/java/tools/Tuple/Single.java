package tools.Tuple;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Data
@AllArgsConstructor
public class Single<X>{
    protected X p0;

    public X p0(){return this.p0;};
    public Single<X> copy(){
        return new Single<X>(p0);
    }

    @Override
    public String toString() {
        return "Pair{\n" +
                "p0 = " + p0 +
                "\n}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Single<?> single = (Single<?>) o;

        return Objects.equals(p0, single.p0);
    }
}
