# Adapted Case-Study Benchmarks and Raw Data

`models/` contains the six adapted case-study benchmark instances used in the
evaluation. Each instance directory includes a `README.md` documenting
its source case study and adaptation to the NRTA input format.

## Adaptation Conventions

Finite shared variables are represented by explicit automaton components, with
variable tests and updates encoded as synchronization actions. Upper-bound
location invariants in the source models are conjoined with the guards of the
corresponding outgoing transitions. This transformation does not preserve
urgency as a separate semantic notion. However, under the finite timed-word
executability semantics considered in the paper, it preserves the relevant
observable behavior: an action can occur after the same delays as in the source
model. The README in each instance directory records the transformations
specific to that benchmark.

`results/CL.csv`, `results/TOT.csv`, and `results/TCT.csv` contain the canonical
raw data. Each file has one row per benchmark instance. Runs on these benchmarks
used a 600-second timeout and a 16-GiB address-space limit. Every
instance/algorithm pair ran in an independent JVM, sequentially. Timeout rows
retain the latest query counters.

`results/case_name_map.csv` maps stable CSV identifiers to case directories.
The files under `statistics/` are derived summaries. `averageKi` is computed by
directly averaging the component `K_i` values.
