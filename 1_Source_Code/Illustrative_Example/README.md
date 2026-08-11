# Illustrative Example

This is the default case used when the command-line program is run without
arguments. It contains two deterministic one-clock RTAs:

- `P1` has actions `a` and `b`, with two locations and two transitions.
- `P2` has actions `b` and `c`, with two locations and two transitions.

Action `b` is the only shared action. All locations are accepting and every
transition resets its component clock. The example is deliberately compact so
that its models and the CL learning process can be followed by hand. The
default run completes in 10 equivalence-query rounds and triggers one rollback.
