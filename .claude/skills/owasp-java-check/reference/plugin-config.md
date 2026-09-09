# Plugin configuration reference

The skill's script invokes the plugins ad hoc, so nothing needs to be in your `pom.xml` for it
to run. If you prefer to wire the checks in as declared build plugins (so `./mvnw verify` runs
them automatically and CI picks them up), use the snippets below.

## OWASP Dependency-Check

```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <version>12.1.0</version>
  <configuration>
    <failBuildOnCVSS>7</failBuildOnCVSS>
    <formats>
      <format>HTML</format>
      <format>JSON</format>
    </formats>
    <!-- Faster, less rate-limited NVD downloads. Prefer an env var over hardcoding. -->
    <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
  </configuration>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

Suppress a false positive (never without a reason) via a `suppression.xml`:

```xml
<suppress>
  <notes>Transitive only, not reachable from our code paths. Reviewed 2026-xx-xx.</notes>
  <cve>CVE-XXXX-XXXXX</cve>
</suppress>
```

...and point the plugin at it with `<suppressionFiles><suppressionFile>suppression.xml</suppressionFile></suppressionFiles>`.

## SpotBugs + FindSecBugs

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>4.8.6.6</version>
  <configuration>
    <effort>Max</effort>
    <threshold>Low</threshold>
    <plugins>
      <plugin>
        <groupId>com.h3xstream.findsecbugs</groupId>
        <artifactId>findsecbugs-plugin</artifactId>
        <version>1.13.0</version>
      </plugin>
    </plugins>
  </configuration>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

Suppress a specific finding in code (with justification) using the SpotBugs annotations
dependency and:

```java
@SuppressFBWarnings(
    value = "PATH_TRAVERSAL_IN",
    justification = "Path is a fixed classpath resource, not user input. Reviewed 2026-xx-xx.")
```

## Semgrep (optional, source-level)

Not a Maven plugin. Install once:

```bash
pip install semgrep
```

Then it is picked up automatically by the skill script. To run standalone:

```bash
semgrep --config p/java --config p/owasp-top-ten .
```

## Version notes

Plugin and ruleset versions move. Before relying on these, check for newer releases:
- OWASP Dependency-Check: https://github.com/jeremylong/DependencyCheck/releases
- FindSecBugs: https://find-sec-bugs.github.io/
- SpotBugs Maven plugin: https://spotbugs.github.io/

Pin whatever versions you settle on so scans are reproducible across machines and CI.
