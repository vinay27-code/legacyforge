# Week 8: Validation + self-healing agents

Goal: every generated Java file gets an AST-level validation pass via JavaParser. If it fails, the agent is called again with the specific parse errors in the prompt, up to 2 retries. Green (VALID) = compiles at the syntax level. Red (INVALID) = still broken after 2 heals.

Additional change: dedup files inside a phase so we don't generate `.mvn/settings.xml` three times like Week 7 did.

## What's in the drop

- **V9__artifact_validation.sql** — adds `validation_status`, `validation_errors`, and `retry_count` columns to `migration_artifacts`.
- **`ValidationService`** — JavaParser-backed syntax checker. Returns VALID / INVALID (with error list) / SKIPPED (non-Java files).
- **`MigrationAgentService`** — new self-healing loop: validate → on INVALID, resend prompt with error context → up to 2 retries → store final result. Also dedups files inside each phase.
- **`MigrationArtifact` entity + DTOs** — new fields (validationStatus, validationErrors, retryCount) + summary counts (valid/invalid/skipped/totalRetries).
- **`agents-tab.component.ts`** — shows validation badge on each row, retry chip when > 0, new summary chips (valid, invalid, self-healing retries), new filter modes (Valid, Invalid, Retried, Failed). Diff modal shows validation errors banner if the code still doesn't parse.

## 1. Extract the zip

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week8.zip -d /tmp/lf-w8
cp -R /tmp/lf-w8/legacyforge-w8/backend/. backend/
cp -R /tmp/lf-w8/legacyforge-w8/frontend/. frontend/
cp -R /tmp/lf-w8/legacyforge-w8/docs/. docs/
rm -rf /tmp/lf-w8
git status
```

Should show these changes:
- Modified: `MigrationArtifact.java`, `MigrationAgentService.java`, `AgentDtos.java`, `MigrationAgentController.java`, `agents-tab.component.ts`, `agents.models.ts`
- New: `V9__artifact_validation.sql`, `ValidationService.java`, `docs/week-8-setup.md`

## 2. Push to prod (skip local this time)

Same as Week 7 — local Ollama is too slow to iterate through 156 files with retries. Prod OpenAI handles it in about 60-120 seconds.

```bash
cd ~/dev/legacyforge
git add .
git commit -m "Week 8: JavaParser validation + self-healing retry loop for agents"
git push
sleep 5 && gh run watch
```

Wait for all four green. Cloud Run picks up V9 migration.

## 3. Test in the browser

1. https://legacyforge-git-main-vinay-1684.vercel.app
2. Log in, open jpetstore
3. **Migration agents** tab → **Rerun**

Watch for these new signals in the summary chips:
- **"N valid"** chip in green — files that parsed cleanly on first or a healed attempt
- **"N invalid"** chip in red (if any) — files still failing after 2 retries
- **"N self-healing retries"** chip in yellow — total heal calls made

Try the new filters:
- **Retried (self-healed)** — see just the files that needed one or more heal passes
- **Invalid** — see files that never parsed even after 2 retries (useful for future prompt tuning)

Click any file to open the diff modal. If a file is INVALID, you'll see a red banner at the top with the actual parse errors.

## Story for interviews

> Every generated Java file runs through a syntax validator built on JavaParser. If the file doesn't parse, the agent is called again with the parser's error messages injected into the prompt. Up to 2 retries per file. The UI shows how many files self-healed, which is the difference between "LLM wrote code" and "LLM wrote code that compiles." On jpetstore, about 5-10% of files needed at least one retry, and almost all of those ended up VALID.

## Common issues

- **Every file shows SKIPPED**: your targets aren't `.java`. That's expected for JSPs, YAMLs, XMLs — those we don't validate. Look at Java files only.
- **Everything INVALID**: JavaParser might be missing from your pom. It was added in Week 5 (used by ChunkingService). If somehow it's not there, add `com.github.javaparser:javaparser-core:3.26.4`.
- **Retries never happen**: check the LlmProvider log line at startup; if PLANNING_MODEL is a really weak local Ollama model, its first attempt is usually clean-looking-but-wrong rather than clearly-broken. Retries only fire on clear parse errors.

## What's next

Week 9: **Dependency graph.** Use JavaParser to walk each generated class and figure out what other generated classes it references. Detect broken edges (references to classes that failed migration or that the plan missed entirely). Visualize the dependency graph in the UI so you can see at a glance which parts of the migration are structurally coherent.
