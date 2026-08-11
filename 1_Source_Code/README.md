# Source Code

This directory contains the Java 17 implementation of CL, TOT, and TCT. The
small `Illustrative_Example/` case is included beside the Maven project and is
used when the command-line program is started without arguments.

## Requirements

- Linux (recommended)
- JDK 17
- Maven 3.8 or later

Maven resolves the dependencies declared in `NRTA_Learning/pom.xml`. The main
dependency is `tools.aqua:z3-turnkey:4.14.1`, which includes the Z3 Java binding
and native library.

## Build

From the artifact root:

```bash
cd 1_Source_Code/NRTA_Learning
mvn -DskipTests clean package
java -jar target/Multi_TA_Learning-1.0-SNAPSHOT.jar --help
```

## Run the illustrative example

From `1_Source_Code/NRTA_Learning`, run:

```bash
java -jar target/Multi_TA_Learning-1.0-SNAPSHOT.jar
```

This runs CL on `../Illustrative_Example`. The two-component example is kept
small enough to inspect directly and includes a rollback during learning.

## Run one case

Run CL on one randomly generated benchmark instance:

```bash
java -jar target/Multi_TA_Learning-1.0-SNAPSHOT.jar   --case ../../2_Randomly_Generated_Cases/models/G1/case1   --algo CL --log quiet --timeout 300
```

Run a baseline by replacing `CL` with `TOT` or `TCT`.

Run CL on one adapted case-study benchmark:

```bash
java -jar target/Multi_TA_Learning-1.0-SNAPSHOT.jar   --case ../../3_Adapted_Case_Studies/models/Fischer   --algo CL --log quiet --timeout 600
```

## Run a complete random benchmark group

```bash
java -jar target/Multi_TA_Learning-1.0-SNAPSHOT.jar   --caseset ../../2_Randomly_Generated_Cases/models/G1   --algo CL --timeout 300   --csv ../../2_Randomly_Generated_Cases/results/reproduced_G1_CL.csv
```

The case-set runner processes cases sequentially. To reproduce the 16-GiB
address-space policy on Linux, wrap a command with:

```bash
prlimit --as=17179869184 java -Xms256m -Xmx8g -jar   target/Multi_TA_Learning-1.0-SNAPSHOT.jar <arguments>
```

## Command-line options

- `--case <path>`: run one JSON model or one case directory.
- `--caseset <path>`: run all immediate `case*` directories.
- `--algo <CL|TOT|TCT>`: select the algorithm.
- `--timeout <seconds>`: per-case internal timeout.
- `--log <quiet|normal|verbose>`: select console detail.
- `--csv <path>`: output CSV for a case-set run.
- `--save-hyp <true|false>`: save learned hypotheses.
- `--strict-eq <true|false>`: select strict equivalence checking.

The original canonical CSV files are stored in
`2_Randomly_Generated_Cases/results/` and `3_Adapted_Case_Studies/results/`.
