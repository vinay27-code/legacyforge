# Demo video script — LegacyForge (3-4 minutes)

Record with Loom, QuickTime, or OBS. Full-screen browser at 1440px width. Have prod URL open and logged in as admin before you hit record. Aim for one continuous take with no re-records — polished-but-real is way more compelling than cut-to-perfection.

**Pace:** speak fast. Never dwell on any one screen for more than 15 seconds. Momentum is the whole story.

---

## 0:00 — 0:20 · The hook (Dashboard)

**Show:** the Dashboard tab.

**Say (roughly):**

> "This is LegacyForge. It's an agentic AI platform I built that migrates legacy Java monoliths — think Struts 1, iBATIS, JSP — to modern Spring Boot 3 and Angular 18. What you're looking at is the real prod dashboard after I ran it against jpetstore-6, a canonical Java legacy app. 208 files generated, 174 dependency edges tracked across the codebase, 18 broken references caught. Total OpenAI spend: six cents."

Pause on the `$0.06` figure for a beat.

---

## 0:20 — 0:45 · The input (Files tab)

**Show:** click "View all migrations" → open jpetstore-6 → Files tab. Expand a folder, click into a JSP file with scriptlets.

**Say:**

> "Here's the input — jpetstore-6, straight from mybatis on GitHub. Struts action classes, iBATIS SQL maps, JSPs with scriptlets. 163 files, roughly what a small enterprise team hands you on day one."

---

## 0:45 — 1:15 · Semantic search

**Show:** Semantic search tab. Query "where is the user login logic?"

**Say:**

> "First thing LegacyForge does is chunk every file at method boundaries, embed each chunk in pgvector with OpenAI's text-embedding-3-small, and give you semantic search. Watch — I ask 'where is the user login logic?' and it returns SignonForm.jsp at 47% match. The word 'login' isn't in that file. This is semantic understanding, not keyword search."

Try one more query: `iBATIS SQL mappings for orders` → returns OrderMapper.

---

## 1:15 — 1:50 · Migration plan

**Show:** Migration plan tab. If a plan exists, scroll through the phases. Otherwise click Generate.

**Say:**

> "Then the entire chunked codebase goes to an LLM in a single prompt — I use OpenAI's gpt-4o-mini for cost — and gets back a phased plan. Five phases, dependency-ordered: build & config first, then data layer, services, controllers, and JSPs to Angular last. Every file gets a risk score and a concrete target pattern like 'Convert to @RestController' or 'Migrate to Spring Data JPA @Repository'."

Scroll through phases to show variety.

---

## 1:50 — 2:30 · Migration agents (the visual centerpiece)

**Show:** Migration agents tab with existing results. Click a file to open the diff modal. Ideally a JSP → Angular one from Phase 5.

**Say:**

> "This is where it gets real. Six LLM agents run in parallel, one per file. Each agent takes a legacy file plus its plan entry and generates the modernized equivalent. Every Java file is validated with JavaParser — if it doesn't parse, the agent gets called again with the parser's errors injected into the prompt. Self-healing loop. Two retries max per file."

Click a file with a diff. Point at the panes.

> "Legacy on the left, generated on the right. That's a JSP with JSTL c:out tags. That's an Angular 18 standalone component with inline template and Angular interpolation. Real code. Actually parses."

---

## 2:30 — 3:00 · Dependency graph + broken links

**Show:** close the modal. Point at the "N deps / N broken" chips. Click a file with the `hub` chip.

**Say:**

> "After all agents finish, every generated Java class gets AST-walked for what it references — imports, field types, method params, constructor calls. Those become edges in a dependency graph. Green chips resolved, red chips broken — classes the generated code needs but the plan never migrated. That's real bugs, surfaced automatically."

Show a broken chip in the Uses section.

---

## 3:00 — 3:30 · The feedback loop

**Show:** close modal. Point at the orange `Patch plan (N)` button. Click it.

**Say:**

> "Now watch. I feed the broken references back to the planning LLM and ask it to add file entries covering them. Slots them into existing phases or creates a new gap-fill phase."

Wait for toast to appear.

> "Rerun the agents. The broken count drops. Loop until convergence. That's the migration self-completing rather than requiring a human to hand-list what's missing."

---

## 3:30 — 3:50 · Wrap

**Show:** back to Dashboard. Point at the tiles.

**Say:**

> "Multi-model architecture — everything speaks the OpenAI-compatible chat completions shape, so I can swap in Gemini for its million-token context, or Ollama for local dev, config-only. All deployed on Cloud Run, Supabase, and Vercel. GitHub Actions auto-deploy on push to main."

---

## 3:50 — 4:00 · CTA

**Say:**

> "Full source is on GitHub. Live demo link in the description. Built over 12 weeks. I'm Vinay. Thanks for watching."

---

## Framing tips

- **Skip the login screen.** Record with a stitched cut if needed, or use an incognito window pre-logged.
- **Cursor tracker on.** macOS System Settings → Accessibility → Display → Pointer size = large + Color = distinctive. Trivial polish, huge readability boost.
- **Silence notifications.** Do Not Disturb on both OS and browser tabs. A single Slack ping during a demo kills it.
- **Talk over dead air.** Waiting for the LLM to respond during a rerun is fine — narrate what's about to happen ("here we go, six agents launching in parallel...").
- **One take.** Perfection isn't the goal. Momentum + real product is.

Post the Loom to your portfolio, pin it on GitHub, drop it in job applications. It's your single most persuasive asset.
