# IC Final-Test Scheduling

## Source

Figure 9 of An et al., "Learning Nondeterministic Real-Time Automata," ACM
Transactions on Embedded Computing Systems 20(5s), Article 99, 2021.
DOI: 10.1145/3477030.

## Adaptation

Actions are given descriptive names. Overlapping source branches are encoded
by disjoint guard regions whose states represent the enabled successor set.
All executable prefixes are retained under the paper's executability semantics,
and transitions reset the component clock.
