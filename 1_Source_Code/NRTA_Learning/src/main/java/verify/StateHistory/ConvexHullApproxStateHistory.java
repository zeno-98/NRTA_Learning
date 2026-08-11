package verify.StateHistory;

import run.state.ZoneState;

/**
 * Placeholder for a convex-hull approximation history strategy.
 */
public class ConvexHullApproxStateHistory extends StateHistory<ZoneState> {
    @Override
    public void add(ZoneState state) {
        throw new UnsupportedOperationException("ConvexHullApproxStateHistory is a placeholder");
    }

    @Override
    public boolean isIncluded(ZoneState state) {
        throw new UnsupportedOperationException("ConvexHullApproxStateHistory is a placeholder");
    }
}
