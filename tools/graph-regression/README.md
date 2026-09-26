# Graph solver regression

Run from any directory with Python 3 and JDK 17 or later:

```text
python tools/graph-regression/run.py --java-home <jdk-directory>
```

The runner compiles the production graph core directly. No Minecraft, Gradle,
network downloads, local saves or third-party solver are required. Results go to
`build/graph-regression`. `--suite fixtures` runs fixed counterexamples, including
three deterministic permutations of recipe and slot order; `--suite oracles`
runs independent integer/BFS/seed/proof checks. `--milliseconds` and `--work`
control the per-order fixture budget; exhaustion is a test failure, not UNSAT.
Local runs default to three seconds and 20 million cumulative work checks.
Timing comparisons should use the same machine and runtime. Run the script
explicitly; no remote workflow is installed by this regression suite.

Fixtures originate from the user-provided September 2026 counterexample packs.
They cover 145 bounded-source cases, 16 focused cases, six long nested counters
and 64 fused cases. SAT/UNSAT truth is preserved. Every returned production
program is independently checked using exact prefix requirements and net effects.
Missing previews are checked after funding their reported deficits.

The oracles check small models exhaustively; this is a correctness regression,
not a claim of completeness or optimality for arbitrary networks. Forge/AE machine
integration remains a separate test suite because it requires the modpack.

Scoped proof export can be enabled with `--proofs`. Archives (`*.cgp`) record
weighted Boolean propagation/learning, rational infeasibility multipliers,
backward coverability closures, startup boxes and forward execution boundaries.
Every record includes its own assumptions/domain. A boundary certificate proves
that at least one listed exit is necessary; it proves global impossibility only
when the boundary is empty. Local model certificates are not implicitly promoted
to whole-order certificates. Cutoffs, dropped archive entries and checker budget
exhaustion are explicit incomplete results, never successful proof checks.

To check an archive in a separate process, after compilation:

```text
java -cp build/graph-regression/classes org.gtlcore.gtlcore.integration.ae2.graph.core.CountProof <archive.cgp>
```
