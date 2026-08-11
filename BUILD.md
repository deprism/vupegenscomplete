# Build VupeCore 2.1.0 MAX Native Commerce

Requirements:

```text
Java 21
Maven 3.9+
```

## GitHub Actions — easiest

The repository contains:

```text
.github/workflows/build.yml
```

Upload the complete project to the root of your existing Vupe GitHub repo.

The root must contain:

```text
.github/
src/
external-configs/
docs/
pom.xml
README.md
```

Commit/push.

Then:

```text
GitHub -> Actions -> Build VupeCore
```

The workflow runs:

```bash
mvn -B clean package
```

When green, open the successful workflow run and download the `VupeCore`
artifact.

Inside should be:

```text
VupeCore-2.1.0.jar
```

## Local build

```bash
mvn clean package
```

Output:

```text
target/VupeCore-2.1.0.jar
```

## Build dependencies

Compile-time provided APIs:

```text
Paper 1.21.11 API
PlotSquared 7.5.13 API
Vault API
PlaceholderAPI
```

Shaded into the Vupe JAR:

```text
Gson
JDA
```

JDA remains shaded because Vupe's optional Discord module is internal.

## Important

A successful compilation verifies Java/Paper API correctness, but does not
replace server integration testing. Vupe MAX intentionally spans many external
plugins, so run `TEST_PLAN_MAX.md` before opening the server publicly.
