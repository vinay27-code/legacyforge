# Week 12: The shareable ship

Two things:

1. **README.md** — a proper top-level README that turns the repo from "some code" into "a project I'd show an interviewer." Rewrites what's there.

2. **Demo video script** (`docs/demo-script.md`) — a beat-by-beat 3-4 minute Loom outline. Record with your phone-cam persona, pin it on the GitHub repo, drop the link in every job application.

Plus one carryover bugfix from Week 11: `PlanPatchService` now writes an audit row to `plan_patches` on every patch, so the Dashboard's `Plan patches applied` tile stops sitting at 0.

## What's in the drop

**Backend:**
- `planning/entity/PlanPatch.java` — the JPA entity
- `planning/repo/PlanPatchRepository.java`
- `planning/service/PlanPatchService.java` — rewritten to also insert an audit row after a successful merge

**Docs:**
- `README.md` — top-level, replaces whatever's there. Includes architecture ASCII, real numbers from your prod db, weekly log, contact line at the bottom.
- `docs/demo-script.md` — full script with timing marks, framing tips, tone guidance.

## 1. Extract

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week12.zip -d /tmp/lf-w12
cp -R /tmp/lf-w12/legacyforge-w12/backend/. backend/
cp /tmp/lf-w12/legacyforge-w12/README.md ./README.md
cp -R /tmp/lf-w12/legacyforge-w12/docs/. docs/
rm -rf /tmp/lf-w12
git status
```

## 2. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git commit -m "Week 12: README, demo script, plan-patch audit trail"
git push
sleep 5 && gh run watch
```

Wait for all four green. Your GitHub repo landing page will now render the new README — visit `https://github.com/vinay27-code/legacyforge` in the browser and see it live.

## 3. Fill in the screenshot placeholders

The README has three placeholder markers:
- `*[Insert Dashboard screenshot: ...]*`
- `*[Insert Migration agents tab: ...]*`
- `*[Insert Diff modal: ...]*`

For each: take a fresh screenshot, drag it into a GitHub issue in the repo to auto-upload, copy the generated CDN URL, replace the placeholder in README.md with:

```markdown
![Dashboard](https://user-images.githubusercontent.com/.../dashboard.png)
```

Commit and push again. Repo landing page now has hero screenshots.

## 4. Record the demo

Follow `docs/demo-script.md` step by step. Aim for one continuous take at 3-4 minutes. Upload to Loom, get a share link, add to the README right under the "Live at ..." line:

```markdown
**Demo video:** [3-minute walkthrough on Loom](https://loom.com/share/...)
```

## 5. Test the plan-patch fix

After the deploy is green, open the Dashboard and rerun agents → click Patch plan a few times. The `Plan patches applied` tile should now count real numbers instead of sitting at 0.

## What's next

You just shipped a 12-week portfolio project. Add it to your resume, link it in every application, and rehearse the demo talk-through once so it flows naturally in interviews.

Then get real sleep.
