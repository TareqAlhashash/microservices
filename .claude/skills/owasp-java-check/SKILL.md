---
name: owasp-java-check
description: >
  Runs OWASP security checks on a Java/Maven project: dependency vulnerability scanning
  (OWASP Dependency-Check against the NVD) and static application security testing
  (SpotBugs + FindSecBugs, and Semgrep if available). Use this before merging a change,
  after adding or upgrading dependencies, when reviewing AI-generated code for security
  issues, or whenever the user asks for a security scan, vulnerability check, OWASP check,
  or CVE audit of a Java codebase. Reports findings by severity and does not fail silently.
---

# OWASP Java Security Check

This skill runs two complementary layers of security analysis on a Java/Maven project and
reports the findings. It scans **only the project in the current working directory** — it is
a defensive analysis tool for code you own, not an attack tool.

## When to use this

- Before merging any change, especially AI-generated code, into a production codebase.
- After adding or upgrading a dependency (catches the Log4Shell class of problem).
- When the user asks for a security scan, vulnerability check, OWASP check, CVE audit, or
  "check this for security issues".
- As a recurring gate in a development workflow.

## What it checks

1. **Dependency scanning — OWASP Dependency-Check.** Compares every resolved dependency
   against the National Vulnerability Database (NVD) and reports known CVEs, with CVSS scores.
   This is the layer that catches vulnerable third-party libraries.

2. **Static analysis — SpotBugs + FindSecBugs.** Analyses the compiled bytecode for insecure
   code patterns: SQL/command injection, weak crypto, path traversal, hardcoded secrets,
   XXE, insecure deserialization, and the rest of the FindSecBugs rule set.

3. **Static analysis — Semgrep (optional).** If `semgrep` is on the PATH, also runs its
   `p/java` and `p/owasp-top-ten` rulesets for source-level pattern matching that complements
   the bytecode analysis. Skipped cleanly if Semgrep is not installed.

## How to run it

The entry point is `scripts/run-owasp-check.sh`. From the project root:

```bash
bash <path-to-skill>/scripts/run-owasp-check.sh
```

The script:
- Confirms it is being run against a Maven project (`pom.xml` present) and stops with a clear
  message if not.
- Runs OWASP Dependency-Check via the Maven plugin, failing the step on CVSS >= 7 (High) by
  default. Override with `OWASP_FAIL_CVSS=<n>`.
- Runs SpotBugs with the FindSecBugs plugin at max effort, threshold low (report everything).
- Runs Semgrep if available.
- Writes reports under `target/security-reports/` and prints a consolidated summary to stdout.

### First run is slow

Dependency-Check downloads the full NVD data feed on first run (several minutes, hundreds of
MB) and caches it. Subsequent runs only fetch the delta. If you have an NVD API key, export it
as `NVD_API_KEY` before running — the feed download is much faster and less rate-limited.

## How to read the output

The script exits non-zero if the dependency scan finds a vulnerability at or above the fail
threshold, so it works as a CI gate. Treat the layers differently when triaging:

- **Dependency CVEs** are usually fixed by upgrading the offending library. The report names the
  exact dependency, the CVE, the CVSS score, and often the fixed version. Prefer upgrading over
  suppressing.
- **SpotBugs/FindSecBugs findings** are in *your* code. Read each one — some are true positives
  that need a code change, some are false positives that should be suppressed *with a documented
  reason*, never blanket-ignored.
- **Never suppress a finding without recording why.** A suppression with no justification is a
  vulnerability with a note saying "ignore me". Use Dependency-Check's `suppression.xml` and
  SpotBugs' `@SuppressFBWarnings` with a `justification`.

## Reviewing AI-generated code specifically

When this skill runs as part of reviewing AI-generated code, pay closest attention to:
- **Injection** — string-concatenated queries, `Runtime.exec` with interpolated input.
- **Auth gaps** — an endpoint added without the authorization check its neighbours have.
- **Secrets** — credentials or keys inlined rather than pulled from config/secrets management.
- **Deserialization** — accepting and deserializing untrusted input.
- **New dependencies** — an agent pulling in a library to solve a problem; scan it before trusting it.

A clean scan is necessary but not sufficient. Static analysis does not understand business logic
or authorization intent — the human review still owns those.

## Setup / prerequisites

The skill uses Maven plugins that do not need to be pre-declared in the project `pom.xml` — the
script invokes them via fully-qualified plugin goals. Requirements:

- Java 17+ and the project's own Maven (`./mvnw` is used if present, else `mvn`).
- Network access on first run for the NVD feed.
- Optionally `semgrep` on the PATH for the third layer.
- Optionally `NVD_API_KEY` for faster NVD downloads.

See `reference/plugin-config.md` for the exact plugin coordinates and the recommended `pom.xml`
snippets if you prefer to wire these in as declared build plugins rather than invoking them ad hoc.
