# Fischer Mutual Exclusion

## Source

The official UPPAAL Fischer benchmark generator by M. O. Moller, available
from the UPPAAL benchmark collection, with k=2.

## Adaptation

This is the two-process instance. The finite shared identifier is represented
by an explicit automaton component, while identifier tests and assignments are
encoded as indexed synchronization actions. The upper-bound location invariant
is conjoined with the corresponding outgoing guards. Although this does not
retain urgency as a separate notion, it preserves the finite timed-word behavior
considered in the paper. Additional resets are placed only where the old clock
value is not used again.
