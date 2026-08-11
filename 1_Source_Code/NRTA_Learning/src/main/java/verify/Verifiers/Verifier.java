package verify.Verifiers;

import run.Trace;
import ta.Location;
import ta.NTA;
import tools.Tuple.Pair;
import tools.Tuple.Triplet;

public abstract class Verifier {
    public abstract Triplet<Boolean, Trace, Boolean> checkLanguageInclude(NTA nta1, NTA nta2);
    public abstract Triplet<Boolean, Trace, Boolean> checkLanguageEquivalence(NTA nta1, NTA nta2);
    public abstract Pair<Boolean, Trace> checkLocationReachable(NTA nta, Location location);
}
