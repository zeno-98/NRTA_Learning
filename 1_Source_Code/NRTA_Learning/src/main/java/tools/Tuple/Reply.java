package tools.Tuple;

import tools.Tuple.Pair;

public class Reply<T> extends Pair<Boolean, T> {
    public Reply(boolean valid, T evidence) {
        super(valid, evidence);
    }

    public boolean valid(){
        return this.p0;
    }
    public T evidence(){
        return this.p1;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
