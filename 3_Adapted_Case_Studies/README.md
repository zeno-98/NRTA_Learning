# Adapted Case-Study Benchmarks and Raw Data

`models/` contains the six adapted case-study benchmark instances used in the
evaluation. Each instance directory includes a short `SOURCE.txt` documenting
its source case study and adaptation to the NRTA input format.

`results/CL.csv`, `results/TOT.csv`, and `results/TCT.csv` contain the canonical
raw data. Each file has one row per benchmark instance. Runs on these benchmarks
used a 600-second timeout and a 16-GiB address-space limit. Every
instance/algorithm pair ran in an independent JVM, sequentially. Timeout rows
retain the latest query counters.

`results/case_name_map.csv` maps stable CSV identifiers to case directories.
The files under `statistics/` are derived summaries. `averageKi` is computed by
directly averaging the component `K_i` values.
