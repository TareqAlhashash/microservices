---
name: integration-test-loop
description: >
  Runs the project's integration tests, and if any fail, diagnoses each failure, applies a
  targeted fix, and re-runs — looping until the suite is green or a bounded iteration limit is
  reached. Use when the user asks to "get the tests passing", "run the test loop", "fix the
  failing tests", or after making a change that should keep the suite green. Fixes the code when
  the code is wrong and the test when the test is genuinely wrong; NEVER makes a test pass by
  weakening, disabling, or deleting what it verifies. Stops and reports if it cannot fix a
  failure legitimately.
---

# Integration Test Loop

Drive the project's integration tests to green through an iterate-diagnose-fix loop, without
compromising what the tests verify.

## The one rule that matters most

**A green suite is only valuable if the tests still verify what they should.** The cheapest way
to make a failing test pass is to weaken it — delete the assertion, loosen the expected value,
add `@Disabled`. That is forbidden here. Making tests pass by removing what they check produces a
build that looks healthy and isn't, which is worse than an honest failure.

On every failure, decide which of these is true and act accordingly:

- **The production code is wrong** (the common case) → fix the code. The test is the source of
  truth for intended behaviour. Do not touch the test.
- **The test is genuinely wrong** — it asserts the wrong thing, or it is stale after a deliberate,
  intended behaviour change → fix the test, but only with an explicit written justification of why
  the old assertion was incorrect and the new one is right. State it in the commit message and to
  the user.
- **You cannot tell, or cannot fix it legitimately** → stop. Report the failure, your diagnosis,
  and what you'd need to proceed. Do not guess your way to green.

Never, under any circumstance, to reach green:
- delete or comment out an assertion,
- loosen an expected value to match the actual (unless the expected value was provably wrong, and
  you say why),
- add `@Disabled`, `@Ignore`, `assumeTrue(false)`, or skip/xfail,
- catch-and-swallow the exception the test is meant to surface,
- widen a timeout, retry, or add sleeps to mask a real timing bug.

If you find yourself reaching for any of those, that is the signal to stop and ask the user, not
to proceed.

## The loop

1. **Run the integration tests** using the project's own runner (see "Detecting the runner").
2. If **green**, stop and report success with the passing count.
3. If **red**, for each distinct failure:
   a. Read the failing test and the code path it exercises. Understand what behaviour it asserts
      and why the actual diverged.
   b. Classify the failure per the rule above (code wrong / test wrong / can't tell).
   c. Apply the smallest correct fix. Prefer fixing one root cause over patching several symptoms
      — several failures often share one.
   d. Note what you changed and why.
4. **Re-run** the full suite (not just the previously-failing test — a fix can break something
   else).
5. Repeat from step 2, up to **`MAX_ITERATIONS` (default 5)** total runs.
6. If still red at the limit, **stop and report**: which tests still fail, what you tried, and your
   best diagnosis. Do not keep looping indefinitely and do not lower the bar to escape the loop.

## Detecting the runner

Use the project's committed entry point, in this order of preference:

- **Maven**: `./mvnw verify` (or `./mvnw failsafe:integration-test` if only integration tests are
  wanted). Use `./mvnw` if present, else `mvn`. Integration tests are typically `*IT` / `*ITCase`
  under Failsafe; unit tests are `*Test` under Surefire.
- **Gradle**: `./gradlew integrationTest` if such a task exists, else `./gradlew test`. Use
  `./gradlew` if present.
- If neither wrapper exists or the task layout is unclear, **ask the user** how they run their
  integration tests rather than guessing — a wrong command wastes iterations.

Run the **whole** integration suite each pass. Re-running only the failing test hides regressions
your fix introduced elsewhere.

## Diagnosing well

- Read the actual assertion and the actual-vs-expected, not just the stack trace headline.
- For a shared root cause, fix it once and let the re-run confirm the others clear.
- Distinguish a real failure from a flaky one (test-ordering dependence, shared mutable state,
  real-clock timing, uncleaned fixtures). A flaky test is a bug in the test — fix the flakiness
  (isolate state, control the clock, clean fixtures), do **not** paper over it with retries.
- If a test depends on seeded or pre-existing data, that is itself a smell — good integration tests
  provision what they assert against. Note it; fix it if in scope.

## Reporting

At the end, whether green or stopped, report:
- final status and the passing/failing count,
- for each fix: the file, whether it was a code or test fix, and the one-line reason,
- for any test fix specifically: the justification for why the old assertion was wrong,
- anything you deliberately did **not** change and why,
- if stopped short: the remaining failures and what you'd need to resolve them.

The report is not optional — it is how the human keeps ownership of what changed and why, which is
the whole point of not silently forcing green.
