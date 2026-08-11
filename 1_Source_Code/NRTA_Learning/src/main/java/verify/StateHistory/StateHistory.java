package verify.StateHistory;

import run.state.State;

/**
 * Maintains historical states during verification.
 * Implementations define their own inclusion semantics.
 */
public abstract class StateHistory<S extends State> {
    public abstract void add(S state);

    public abstract boolean isIncluded(S state);
}
