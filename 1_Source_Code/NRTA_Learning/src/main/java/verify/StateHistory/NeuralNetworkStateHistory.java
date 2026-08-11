package verify.StateHistory;

import run.state.ZoneState;

/**
 * Placeholder for a neural-network-based history strategy.
 */
public class NeuralNetworkStateHistory extends StateHistory<ZoneState> {
    @Override
    public void add(ZoneState state) {
        throw new UnsupportedOperationException("NeuralNetworkStateHistory is a placeholder");
    }

    @Override
    public boolean isIncluded(ZoneState state) {
        throw new UnsupportedOperationException("NeuralNetworkStateHistory is a placeholder");
    }
}
