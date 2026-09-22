# Week 9: Multi-file splitting + cross-file dependency graph

Two things this week:

**1. Multi-file splitting** — the LLM sometimes emits multiple `// TARGET:` blocks in one response (a Repository, its Entity, and a Service in one go). Week 8 was stapling them together as one artifact and failing validation. Week 9 splits on the TARGET headers: the first block updates the original artifact, the rest become **derived sibling artifacts** in the same phase, each validated independently.

**2. Cross-file dependency graph** — after every run, `DependencyAnalyzer` walks each generated Java file with JavaParser and records what it references (imports, field types, method params, `new Foo()` calls). Each reference becomes an edge in `migration_dependencies`. If the referenced class exists as another generated artifact, the edge is `resolved`; otherwise it's a **broken link** — the plan missed that class, or its migration failed, or the LLM hallucinated a name.

## What's in the drop

**Backend:**
- `V10__deps_and_multifile.sql` — adds `parent_artifact_id` + `declared_fqn` to `migration_artifacts`, and a new `migration_dependencies` table.
- `MigrationArtifact` entity — new fields (`parentArtifactId`, `declaredFqn`).
- `MigrationDependency` entity + repo — the edge table.
- `DependencyAnalyzer` service — walks JavaParser AST, extracts refs, marks resolved vs broken.
- `MigrationAgentService` — splits multi-TARGET responses into siblings; calls `DependencyAnalyzer.rebuildFor(...)` at end of run; system prompt updated to explicitly allow multi-file output.
- `AgentDtos` + controller — new fields on summary (`derivedCount`, `totalEdges`, `brokenEdges`), per-artifact (`depsOut`, `depsBroken`, `depsIn`), and detail (`outgoing[]` + `incoming[]` dep edges).

**Frontend:**
- New summary chips: `N derived`, `N deps`, `N broken links`.
- New filter modes: `Derived (multi-target splits)`, `Broken deps`.
- Per-file row: purple deps chip showing outgoing count, red slash + count if any are broken.
- Derived artifacts get a blue left border + `call_split` icon.
- Diff modal has a new **Uses** / **Used by** section listing each dependency as a color-coded chip (green resolved, red broken with a `link_off` icon).

## 1. Extract

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week9.zip -d /tmp/lf-w9
cp -R /tmp/lf-w9/legacyforge-w9/backend/. backend/
cp -R /tmp/lf-w9/legacyforge-w9/frontend/. frontend/
cp -R /tmp/lf-w9/legacyforge-w9/docs/. docs/
rm -rf /tmp/lf-w9
git status
```

## 2. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git commit -m "Week 9: multi-file splitting + cross-file dependency graph"
git push
sleep 5 && gh run watch
```

## 3. Verify and rerun agents

```bash
gcloud run services logs read legacyforge-backend \
  --project=$GCP_PROJECT_ID \
  --region=us-central1 \
  --limit=20 \
  | grep -i "migration"
```

Should show `Rebuilt dependency graph for repo ... : N edges across M artifacts` once the first run finishes.

Then in the browser:
1. https://legacyforge-git-main-vinay-1684.vercel.app
2. Migration agents tab → **Rerun**

Watch for the new signals:
- **`N derived`** chip — should be > 0 (those SequenceMapper INVALIDs from Week 8 should now be legit derived files)
- **`N deps`** chip — the total edges in the generated codebase
- **`N broken links`** chip — plan gaps or hallucinated references

Then click any file to see its **Uses** / **Used by** section in the diff modal.

## Interview story

> Each generated Java file gets AST-analyzed for the classes it imports, holds as fields, takes as method params, or instantiates. Those become edges in a dependency graph. The graph tells us two things a raw file list can't: **structural coherence** (are the pieces wired together?) and **plan gaps** (which classes are referenced but never migrated?). On jpetstore, the run reports N total edges and M broken links — each broken link is a candidate for a follow-up plan iteration.

## Common issues

- **`0 deps` after a rerun**: none of the target paths ended `.java`, so the analyzer skipped everything. Check the artifact list.
- **All edges marked `broken`**: FQN extraction failed. Look for `Rebuilt dependency graph` log line; if it says 0 edges, the analyzer failed silently.
- **`N broken links` is very high**: expected on the first run — the plan doesn't touch every class the LLM references (Spring internals slip past the ignore list, or a helper class the LLM invented). Filter by "Broken deps" and eyeball what's missing.

## What's next

Week 10: **Feedback loop into the plan.** Feed the broken-links report back to the planning LLM and let it generate a plan patch — new phase entries for the missing classes. That's how the migration self-completes rather than requiring manual iteration.
