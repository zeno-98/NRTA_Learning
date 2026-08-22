# Not-Not-And Asynchronous Circuit

## Source

The IMPO (Not-Not-And) benchmark from the IMITATOR benchmark library at its
published pi0 valuation, attributed to E. Andre, T. Chatain, and C. Rodriguez,
ACM Transactions on Embedded Computing Systems 16(2), Article 43, 2016.

## Adaptation

All five reachable components, 20 locations, and 30 visible transitions are
retained. Upper-bound source invariants are conjoined with the corresponding
outgoing guards. This does not retain urgency as a separate notion but preserves
the finite timed-word behavior considered in the paper. Added local resets are
behaviorally inactive before the next source reset, and terminal silent
self-loops are omitted under the paper's executability semantics.
