package verify.StateHistory;

import run.parallel_elements.LocationVector;
import run.state.ZoneState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exact DBM-state history:
 * states are grouped by location vector, and inclusion is checked
 * by DBM inclusion against historical states at the same location.
 */
public class ExactStateHistory extends StateHistory<ZoneState> {
    private final Map<LocationVector, Set<ZoneState>> passed = new TreeMap<>();

    @Override
    public void add(ZoneState state) {
        Set<ZoneState> statesAtLocation =
                passed.computeIfAbsent(state.getLocation_vec(), ignored -> new HashSet<>());
        statesAtLocation.add(state);
    }

    @Override
    public boolean isIncluded(ZoneState state) {
        Set<ZoneState> statesAtLocation = passed.get(state.getLocation_vec());
        if (statesAtLocation == null) {
            return false;
        }
        for (ZoneState passedState : statesAtLocation) {
            if (passedState.getZone().includes(state.getZone())) {
                return true;
            }
        }
        return false;
    }
}
