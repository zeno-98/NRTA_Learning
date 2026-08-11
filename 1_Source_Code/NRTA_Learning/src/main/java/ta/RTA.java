package ta;

import language.actions.RTARegionAction;
import language.words.RTARegionWord;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RTA extends TA{
    @Getter
    @Setter
    private Clock single_clock = null;
    private final Map<Integer, IntervalCondition> transitionGuardIntervalMap = new HashMap<>();
    private final Map<Location, IntervalCondition> locationInvariantIntervalMap = new HashMap<>();

    public RTA(String name) {
        super(name);
    }

    public void addClock(Clock x){
        if(this.X.isEmpty()){
            super.addClock(x);
            this.single_clock = x;
        }
    }

    /**
     * Check whether this RTA accepts a region word.
     * Internally follows the same run as prefix acceptance tracking.
     */
    public boolean ifContainRTAZoneWord(RTARegionWord word) {
        List<Boolean> prefixAcceptTrace = this.prefixAcceptTrace(word);
        return !prefixAcceptTrace.isEmpty() && prefixAcceptTrace.get(prefixAcceptTrace.size() - 1);
    }

    /**
     * Returns acceptance of every prefix (including epsilon prefix at index 0).
     * Example: result size = word.length + 1.
     */
    public List<Boolean> prefixAcceptTrace(RTARegionWord word) {
        List<Boolean> outcome = new ArrayList<>();
        Set<Location> currentLocations = new LinkedHashSet<>();
        currentLocations.add(this.q0);
        outcome.add(this.q0 != null && this.q0.is_accepting());
        if (word == null || word.isEmpty()) {
            return outcome;
        }

        for (int i = 0; i < word.getLength(); i++) {
            RTARegionAction regionAction = word.getTimedAction(i);
            RegionInterval currentRegion = this.regionFromAction(regionAction);

            Set<Location> nextLocations = new LinkedHashSet<>();
            for (Location source : currentLocations) {
                if (!this.constraintPassesRegion(source.getInvariant(), currentRegion, source, null)) {
                    continue;
                }
                List<Transition> candidates = source.getOutgoingTransitions(regionAction.getAction());
                for (Transition transition : candidates) {
                    if (transition.isEpsilon()) {
                        continue;
                    }
                    if (!this.constraintPassesRegion(transition.getGuard(), currentRegion, null, transition)) {
                        continue;
                    }
                    RegionInterval targetRegion = transition.getReset().contains(this.single_clock)
                            ? RegionInterval.point(Value.ZERO)
                            : currentRegion;
                    if (!this.constraintPassesRegion(transition.getTarget().getInvariant(), targetRegion, transition.getTarget(), null)) {
                        continue;
                    }
                    nextLocations.add(transition.getTarget());
                }
            }

            if (nextLocations.isEmpty()) {
                for (int k = i; k < word.getLength(); k++) {
                    outcome.add(false);
                }
                return outcome;
            }
            boolean accepted = false;
            for (Location location : nextLocations) {
                if (location.is_accepting()) {
                    accepted = true;
                    break;
                }
            }
            outcome.add(accepted);
            currentLocations = nextLocations;
        }
        return outcome;
    }

    private boolean constraintPassesRegion(Constraint constraint, RegionInterval region, Location location, Transition transition) {
        IntervalCondition compiled;
        if (transition != null) {
            compiled = this.transitionGuardIntervalMap.computeIfAbsent(
                    transition.getId(),
                    id -> this.compileConstraint(transition.getGuard()));
        } else if (location != null) {
            compiled = this.locationInvariantIntervalMap.computeIfAbsent(
                    location,
                    loc -> this.compileConstraint(loc.getInvariant()));
        } else {
            compiled = this.compileConstraint(constraint);
        }
        return compiled.intersects(region);
    }

    private RegionInterval regionFromAction(RTARegionAction action) {
        if (action.getLower().equals(action.getUpper()) && action.isLowerClosed() && action.isUpperClosed()) {
            return RegionInterval.point(action.getLower());
        }
        return new RegionInterval(action.getLower(), action.isLowerClosed(), action.getUpper(), action.isUpperClosed());
    }

    private IntervalCondition compileConstraint(Constraint constraint) {
        if (constraint == null || constraint.isEmpty()) {
            return IntervalCondition.unbounded();
        }
        IntervalCondition outcome = IntervalCondition.unbounded();
        for (AtomConstraint atom : constraint.getAtomConstraints()) {
            Clock c1 = atom.getClock1();
            Clock c2 = atom.getClock2();
            Value ub = atom.getUpperbound();
            boolean closed = atom.isClosed();

            if (ub.isInfinite()) {
                continue;
            }
            if (!this.supportedClockPair(c1, c2)) {
                throw new IllegalStateException(
                        "Non-RTA atom found while compiling guard/invariant: "
                                + atom.toStringNew()
                                + ", single_clock=" + this.single_clock
                                + ", ta=" + this.getName());
            }
            if (ub.isMinus_infinite()) {
                return IntervalCondition.empty();
            }

            if (!c1.isZeroClock() && c2.isZeroClock()) {
                if (!outcome.tightenUpper(ub, closed)) {
                    return IntervalCondition.empty();
                }
                continue;
            }
            if (c1.isZeroClock() && !c2.isZeroClock()) {
                Value lower = Value.negative(ub);
                if (!outcome.tightenLower(lower, closed)) {
                    return IntervalCondition.empty();
                }
                continue;
            }
            throw new IllegalStateException(
                    "Unsupported atom form while compiling guard/invariant: "
                            + atom.toStringNew()
                            + ", ta=" + this.getName());
        }
        return outcome;
    }

    private boolean supportedClockPair(Clock c1, Clock c2) {
        if (this.single_clock == null) {
            throw new IllegalStateException("RTA single_clock is null for TA " + this.getName());
        }
        if (c1.isZeroClock() && c2.isZeroClock()) {
            return false;
        }
        if (c1.isZeroClock()) {
            return c2.equals(this.single_clock);
        }
        if (c2.isZeroClock()) {
            return c1.equals(this.single_clock);
        }
        return false;
    }

    private static final class RegionInterval {
        private final Value lower;
        private final boolean lowerClosed;
        private final Value upper;
        private final boolean upperClosed;

        private RegionInterval(Value lower, boolean lowerClosed, Value upper, boolean upperClosed) {
            this.lower = lower;
            this.lowerClosed = lowerClosed;
            this.upper = upper;
            this.upperClosed = upperClosed;
        }

        private static RegionInterval point(Value point) {
            return new RegionInterval(point, true, point, true);
        }
    }

    private static final class IntervalCondition {
        private Value lower;
        private boolean lowerClosed;
        private Value upper;
        private boolean upperClosed;

        private IntervalCondition(Value lower, boolean lowerClosed, Value upper, boolean upperClosed) {
            this.lower = lower;
            this.lowerClosed = lowerClosed;
            this.upper = upper;
            this.upperClosed = upperClosed;
        }

        private static IntervalCondition unbounded() {
            return new IntervalCondition(Value.MINUS_INFINITE, false, Value.INFINITE, false);
        }

        private static IntervalCondition empty() {
            return new IntervalCondition(Value.ZERO, false, Value.ZERO, false);
        }

        private boolean tightenLower(Value candidate, boolean candidateClosed) {
            int cmp = Value.compare(candidate, this.lower);
            if (cmp > 0) {
                this.lower = candidate;
                this.lowerClosed = candidateClosed;
            } else if (cmp == 0) {
                this.lowerClosed = this.lowerClosed && candidateClosed;
            }
            return this.isFeasible();
        }

        private boolean tightenUpper(Value candidate, boolean candidateClosed) {
            int cmp = Value.compare(candidate, this.upper);
            if (cmp < 0) {
                this.upper = candidate;
                this.upperClosed = candidateClosed;
            } else if (cmp == 0) {
                this.upperClosed = this.upperClosed && candidateClosed;
            }
            return this.isFeasible();
        }

        private boolean intersects(RegionInterval region) {
            Value l = this.lower;
            boolean lClosed = this.lowerClosed;
            int cmpL = Value.compare(region.lower, l);
            if (cmpL > 0) {
                l = region.lower;
                lClosed = region.lowerClosed;
            } else if (cmpL == 0) {
                lClosed = lClosed && region.lowerClosed;
            }

            Value u = this.upper;
            boolean uClosed = this.upperClosed;
            int cmpU = Value.compare(region.upper, u);
            if (cmpU < 0) {
                u = region.upper;
                uClosed = region.upperClosed;
            } else if (cmpU == 0) {
                uClosed = uClosed && region.upperClosed;
            }

            int cmp = Value.compare(l, u);
            if (cmp < 0) {
                return true;
            }
            if (cmp > 0) {
                return false;
            }
            return lClosed && uClosed;
        }

        private boolean isFeasible() {
            int cmp = Value.compare(this.lower, this.upper);
            if (cmp < 0) {
                return true;
            }
            if (cmp > 0) {
                return false;
            }
            return this.lowerClosed && this.upperClosed;
        }
    }
}
