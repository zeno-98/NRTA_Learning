# Illustrative Example

This is the default case used when the command-line program is run without
arguments. It contains two deterministic one-clock RTAs:

- `P1` has actions `a` and `b`, with two locations and two transitions.
- `P2` has actions `b` and `c`, with two locations and two transitions.

Action `b` is the only shared action. Every location represents executable
behavior, and every transition resets its component clock. The example is
compact enough to follow its models and the CL learning process by hand. The
default run completes in 10 equivalence-query rounds and triggers one rollback.
