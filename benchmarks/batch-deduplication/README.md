# Batch Deduplication Benchmark

This benchmark uses 100 already-loaded root objects with 10 unique related IDs. Each root has one annotated relationship
resolved through the same downstream client.

The benchmark records downstream relationship HTTP requests for two configurations:

| Configuration         | Expected relationship requests |                         Elapsed time |
|-----------------------|-------------------------------:|-------------------------------------:|
| No batch profile      |                             10 | Record `real` from the timed command |
| Batch profile enabled |                              1 | Record `real` from the timed command |

The no-batch result demonstrates request-session de-duplication for repeated IDs. The batched result demonstrates one
bounded request for the 10 unique IDs. The counts exclude loading the 100 root objects; they measure only relationship
lookups.

## Measurement

Run the benchmark harness from the repository root and record the `real` value for each configuration:

```sh
/usr/bin/time -p ./gradlew :benchmarks:batch-deduplication:run --args="--batch=false"
/usr/bin/time -p ./gradlew :benchmarks:batch-deduplication:run --args="--batch=true"
```

The repository currently documents the benchmark contract but does not include an executable benchmark task. The
commands above are the required harness entry point when the benchmark project is available; do not substitute the full
test suite for these measurements.
