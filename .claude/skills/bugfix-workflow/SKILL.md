---
name: bugfix-workflow
description: >
  End-to-end workflow for fixing a bug in a Java/Maven project: reproduce the bug with a failing
  test first, apply the smallest correct fix, code-review the fix, drive the whole suite green via
  the integration test loop, extend coverage where the bug revealed a gap, and run the OWASP
  security scan before the change is considered done. Use when the user reports a bug, asks to
  "fix this issue", "investigate and fix", or hands over a stack trace or failing behaviour to
  resolve. Composes the code-review, integration-test-loop, and owasp-java-check skills. Never
  marks a fix done without a test that would have failed before it.
---

# Bug-Fix Workflow

The orchestrator for fixing a bug properly: reproduce, fix, review, prove, secure. It ties together
the `code-review`, `integration-test-loop`, and `owasp-java-check` skills so a "fix" is only done
when it has been read critically, verified, and has not introduced a security regression.

## The rule that makes this trustworthy

**A bug is not fixed until a test proves it is fixed — a test that would have failed before your
change and passes after.** Writing the fix first and a passing test afterwards proves nothing: the
test might pass regardless of the fix. So the reproduction test comes first, and you confirm it is red
for the right reason before you touch the production code.

Equally, do not "fix" by patching the one symptom the reporter happened to see if the root cause would
produce other symptoms too. Fix the cause, then let the tests confirm the whole class of failure is
gone.

## The workflow

### 1. Understand the bug

Read the report, stack trace, or described behaviour. Locate the code path involved. State, in one or
two sentences, what the wrong behaviour is and what the correct behaviour should be. If the report is
ambiguous about what "correct" means, **ask the user** before writing anything — a fix to the wrong
spec wastes the whole loop.

### 2. Reproduce with a failing test FIRST

Write an integration (or unit, if that is the right level) test that asserts the *correct* behaviour.
Run it and confirm it **fails**, and that it fails for the reason the bug describes — not a compile
error, not a typo in the test, not an unrelated cause. A reproduction test that fails for the wrong
reason is not a reproduction.

If you genuinely cannot reproduce the bug with a test, stop and report that. A bug you cannot reproduce
is one you cannot prove you have fixed — say so rather than guessing at a fix.

### 3. Fix the root cause

Apply the smallest change that makes the reproduction test pass by correcting the actual cause. Resist
fixing the symptom if the cause sits deeper. Do not weaken the new test to make it pass — it defines
"correct" now.

### 4. Code-review the fix before the expensive part

Invoke the **code-review** skill against the diff (fix plus reproduction test) at a low or medium
effort level — enough to catch a correctness slip, an obvious reuse/simplification miss, or an
efficiency problem, before spending time in the test loop below. This is a cheap, fast gate: it runs
before `integration-test-loop` on purpose, since that loop can be slow (Docker, Testcontainers, a
full suite) and there is no point running it against a fix with an avoidable defect already visible
on inspection. Act on findings — fix what the review surfaces — before moving on. A clean review is
not a substitute for the reproduction test: it checks the shape of the code, not that the bug is
actually gone.

### 5. Drive the whole suite green

Invoke the **integration-test-loop** skill. Your fix must not break anything else, and its rules apply:
never reach green by weakening, disabling, or deleting tests. If the loop cannot get to green
legitimately, stop and report — do not force it.

### 6. Extend coverage where the bug revealed a gap

The bug existed because something was untested. Add the tests that would have caught it: the boundary
case, the null, the malformed input, the case-sensitivity, whatever class of input the bug lived in.
This is what stops the same bug returning in a different shape. Keep it proportionate — cover the gap
the bug exposed, don't gold-plate unrelated areas.

### 7. Security scan before done

Invoke the **owasp-java-check** skill. A fix can introduce a vulnerability — a new dependency pulled in
to solve the problem, a validation removed, an injection opened. The change is not done until the scan
is clean or every finding is triaged with a recorded reason. Pay special attention if the fix touched
input handling, queries, deserialization, auth, or added a dependency.

### 8. Report

Summarise, so the human keeps ownership of what changed:
- the root cause, in one or two sentences,
- the fix and why it addresses the cause rather than the symptom,
- the reproduction test (named) and confirmation it was red before and green after,
- the code-review result and anything it caught,
- any additional coverage added and what class of failure it guards against,
- the security scan result,
- anything you chose not to change and why.

## Guardrails (the whole point)

- **No fix without a failing-first test.** If you wrote the fix before the test, you have not proven
  anything — go back and write the reproduction, confirm it fails, then re-apply.
- **Root cause over symptom.** If the same cause could produce other failures, fix the cause.
- **Code review comes before the test loop, not instead of it.** A clean review says the code looks
  right; it does not prove the bug is fixed. Both steps run, in that order, every time.
- **The test-loop and security rules are inherited, not optional.** Green is only real if the tests
  still verify what they should, and done only counts if the scan is clean or triaged.
- **Stop and ask beats guessing.** Ambiguous spec, unreproducible bug, a failure you can't fix
  legitimately — stop and report. Forcing a false "done" is the one outcome worse than an honest "I'm
  stuck".

## Composition

This skill assumes `code-review`, `integration-test-loop`, and `owasp-java-check` are installed
alongside it. If any is missing, do the equivalent step inline (a manual read of the diff for
correctness and simplification issues; running the suite in a loop under the same rules; running
OWASP Dependency-Check + SpotBugs/FindSecBugs) and note that the dedicated skill was unavailable.
