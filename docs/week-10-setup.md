# Week 10: Broken-links feedback loop + Week 9 bugfixes

Three things:

1. **`java.lang.*` false positives** — Week 9's dependency analyzer flagged `String`, `Long`, `Integer` etc. as broken because it didn't know about auto-imports. Fixed: a whitelist of common `java.lang` types resolves to their JDK FQNs and gets filtered.

2. **`parent_artifact_id` FK constraint violations** — parallel worker threads inserting derived siblings hit transaction isolation edge cases where the FK target wasn't visible on their connection. Fixed by dropping the FK constraint (`parent_artifact_id` is a soft link — parents and children always live and die together via `deleteByRepoId`, so cascade behavior isn't needed).

3. **Broken-links feedback loop** (the headline). New service `PlanPatchService` takes the current plan + the distinct broken class names from the dependency graph, asks the LLM to propose additions covering each missing class (slotted into existing phases or grouped into a new gap-fill phase), and merges the result into the plan JSON. Endpoint: `POST /api/repos/{id}/plan/patch`.

## What's in the drop

**Backend:**
- `V11__plan_patches.sql` — drops the parent FK + adds `plan_patches` audit table.
- `DependencyAnalyzer.java` — adds `java.lang.*` implicit import handling.
- `PlanPatchService.java` — the feedback loop. Takes broken refs, calls LLM in JSON mode, merges patch into plan.
- `PlanningController.java` — new `POST /plan/patch` endpoint alongside existing GET/POST.

**Frontend:**
- `agents.service.ts` — new `patchPlan(repoId)` method.
- `agents-tab.component.ts` — a purple **`Patch plan (N)`** button appears next to Rerun when `brokenEdges > 0`. Click it → the LLM adds plan entries → a toast appears with "Patched X broken references → added Y files across Z new phases" and a **Rerun** shortcut to fill the new slots.

## 1. Extract

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week10.zip -d /tmp/lf-w10
cp -R /tmp/lf-w10/legacyforge-w10/backend/. backend/
cp -R /tmp/lf-w10/legacyforge-w10/frontend/. frontend/
cp -R /tmp/lf-w10/legacyforge-w10/docs/. docs/
rm -rf /tmp/lf-w10
git status
```

## 2. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git commit -m "Week 10: broken-links feedback loop; fix java.lang deps + parent FK"
git push
sleep 5 && gh run watch
```

Wait for all four green.

## 3. Test the feedback loop

1. https://legacyforge-git-main-vinay-1684.vercel.app
2. Log in, open jpetstore
3. Migration agents tab
4. Click **Rerun** first — with the java.lang fix, `broken links` count should drop noticeably
5. Once done, if `broken links > 0`, click the purple **`Patch plan (N)`** button
6. Wait 5-15s for the LLM to propose additions
7. The purple toast appears: *"Patched X broken references → added Y files across Z new phase(s)"*
8. Click **Rerun agents to fill new slots** in the toast
9. New files generate for the previously-broken classes → broken count drops toward zero

Loop until broken → 0, or diminishing returns.

## Story for interviews

> The dependency graph finds classes the generated code references but never migrated. Broken-links feedback pipes that report back into the planning LLM: it proposes new file entries covering the missing classes, slots them into existing phases where possible, and creates a gap-fill phase when they don't fit. The user runs the agents again and the broken count drops. That's the migration self-completing rather than requiring a human to hand-list what's missing.

## Common issues

- **"No broken references to patch"** after clicking Patch: the graph is fully resolved (nice). No-op.
- **Patch runs but broken count doesn't drop**: rerun the agents. The patch adds plan entries; the agents need to run again to generate code for them.
- **Some paths in patch entries look weird**: LLM guessed based on FQN heuristics. Free to hand-edit the plan via a follow-up (Week 11 will add plan editing in the UI).

## What's next

Week 11: **Hardening pass.** Back-fill the DB integration tests we deferred at Week 5 (Testcontainers with pgvector); add smoke tests for the planning + agents endpoints; wire structured logging + timing metrics into the agent orchestrator so we can see LLM-call latency distributions in prod.
