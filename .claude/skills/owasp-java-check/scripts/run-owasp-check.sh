#!/usr/bin/env bash
#
# run-owasp-check.sh — OWASP dependency + static analysis for a Java/Maven project.
# Runs against the project in the current working directory. Defensive analysis only.
#
set -uo pipefail

# ---- config (override via environment) ----------------------------------------
OWASP_FAIL_CVSS="${OWASP_FAIL_CVSS:-7}"                 # fail dependency scan at/above this CVSS
DC_PLUGIN="org.owasp:dependency-check-maven:12.1.0"     # OWASP Dependency-Check
SPOTBUGS_PLUGIN="com.github.spotbugs:spotbugs-maven-plugin:4.8.6.6"
FINDSECBUGS_VERSION="1.13.0"
REPORT_DIR="target/security-reports"

# ---- preflight ----------------------------------------------------------------
if [[ ! -f "pom.xml" ]]; then
  echo "ERROR: no pom.xml in $(pwd). Run this from the root of a Maven project." >&2
  exit 2
fi

# Prefer the project's wrapper so the build is reproducible.
if [[ -x "./mvnw" ]]; then
  MVN="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
else
  echo "ERROR: neither ./mvnw nor mvn found on PATH." >&2
  exit 2
fi

mkdir -p "$REPORT_DIR"
echo "=== OWASP Java Security Check ==="
echo "Project : $(pwd)"
echo "Maven   : $MVN"
echo "Fail CVSS threshold (dependency scan): $OWASP_FAIL_CVSS"
[[ -n "${NVD_API_KEY:-}" ]] && echo "NVD API key: set (faster feed)" || echo "NVD API key: not set (first run will be slow)"
echo

# Track outcomes without aborting the whole run on the first failure.
DEP_STATUS=0
SPOTBUGS_STATUS=0
SEMGREP_STATUS=0

# ---- 1. dependency scanning ---------------------------------------------------
echo "--- [1/3] OWASP Dependency-Check (CVE scan of dependencies) ---"
DC_ARGS=(
  "$DC_PLUGIN:check"
  "-DfailBuildOnCVSS=${OWASP_FAIL_CVSS}"
  "-Dformats=HTML,JSON"
  "-DoutputDirectory=${REPORT_DIR}"
)
[[ -n "${NVD_API_KEY:-}" ]] && DC_ARGS+=("-DnvdApiKey=${NVD_API_KEY}")
"$MVN" --batch-mode "${DC_ARGS[@]}"
DEP_STATUS=$?
echo

# ---- 2. static analysis: SpotBugs + FindSecBugs -------------------------------
echo "--- [2/3] SpotBugs + FindSecBugs (static analysis of your code) ---"
# Needs compiled classes; compile quietly first.
"$MVN" --batch-mode -q compile
if [[ $? -ne 0 ]]; then
  echo "WARN: compile failed; skipping SpotBugs (fix the build first)." >&2
  SPOTBUGS_STATUS=1
else
  "$MVN" --batch-mode \
    "$SPOTBUGS_PLUGIN:check" \
    -Dspotbugs.effort=Max \
    -Dspotbugs.threshold=Low \
    -Dspotbugs.plugins="com.h3xstream.findsecbugs:findsecbugs-plugin:${FINDSECBUGS_VERSION}" \
    -Dspotbugs.xmlOutput=true \
    -Dspotbugs.xmlOutputDirectory="${REPORT_DIR}"
  SPOTBUGS_STATUS=$?
fi
echo

# ---- 3. static analysis: Semgrep (optional) -----------------------------------
echo "--- [3/3] Semgrep (optional source-level rules) ---"
if command -v semgrep >/dev/null 2>&1; then
  semgrep --config p/java --config p/owasp-top-ten \
    --json --output "${REPORT_DIR}/semgrep.json" \
    --error .
  SEMGREP_STATUS=$?
  semgrep --config p/java --config p/owasp-top-ten . || true   # human-readable to stdout
else
  echo "Semgrep not installed — skipping. Install with: pip install semgrep"
  SEMGREP_STATUS=0
fi
echo

# ---- summary ------------------------------------------------------------------
echo "=== Summary ==="
report() {
  local name="$1" status="$2"
  if [[ "$status" -eq 0 ]]; then
    echo "  PASS  $name"
  else
    echo "  FAIL  $name (exit $status)"
  fi
}
report "Dependency-Check (CVEs)" "$DEP_STATUS"
report "SpotBugs + FindSecBugs"  "$SPOTBUGS_STATUS"
report "Semgrep"                 "$SEMGREP_STATUS"
echo
echo "Reports written to: ${REPORT_DIR}/"
echo "  - dependency-check-report.html   (open in a browser)"
echo "  - spotbugsXml.xml"
echo "  - semgrep.json                   (if Semgrep ran)"
echo
echo "Reminder: a clean scan does not clear business-logic or authorization review."
echo "Static analysis cannot see intent — the human still owns that."

# Exit non-zero if the dependency scan breached the CVSS gate, so this works as a CI gate.
# SpotBugs/Semgrep failures are surfaced but do not by themselves fail the gate — tune to taste.
if [[ "$DEP_STATUS" -ne 0 ]]; then
  exit 1
fi
exit 0
