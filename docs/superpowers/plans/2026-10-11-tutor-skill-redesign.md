# Socratic Personal Tutor Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the global `tutor` skill to enforce a 1-on-1 Socratic Personal Tutor method using micro-stepping, physical analogies, and interactive comprehension gates.

**Architecture:** Transform `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md` from a generic advice document into a structured 4-phase pedagogical pipeline with strict anti-patterns and guardrails.

**Tech Stack:** Markdown, Antigravity Customization System (Global Skills).

---

### Task 1: Backup Current Global Tutor Skill

**Files:**
- Read: `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md`
- Create: `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md.bak`

- [ ] **Step 1: Create backup copy of existing SKILL.md**

Read `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md` and write a copy to `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md.bak`.

- [ ] **Step 2: Verify backup file exists and matches size**

Verify that `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md.bak` exists and has identical content to the original.

---

### Task 2: Implement Redesigned Socratic Personal Tutor in Global SKILL.md

**Files:**
- Modify: `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md`

- [ ] **Step 1: Write the updated SKILL.md**

Write the complete 4-phase Socratic Personal Tutor guidelines to `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md`:
- YAML Frontmatter (`name: tutor`, description emphasizing step-by-step Socratic intuition building).
- Phase 1: Discovery & Curriculum Decomposition.
- Phase 2: Roadmap Briefing & Immediate Launch.
- Phase 3: The Micro-Stepping Socratic Loop (The Rule of One, Physical Analogy First, The Socratic Gate, The Hard Stop, The Affirm & Refine Loop, The Reset Protocol).
- Phase 4: Synthesis & Quality Gate Handoff (Summary table, Codebase mapping, `/deep-dive` prompt).
- Strict Anti-Patterns & Common Mistakes section.

- [ ] **Step 2: Verify file formatting and frontmatter**

Check that lines 1-4 contain valid YAML frontmatter and that all markdown headers and sections are well-formed.

---

### Task 3: Commit Plan and Workspace Artifacts

**Files:**
- Tracked in workspace: `docs/superpowers/plans/2026-10-11-tutor-skill-redesign.md`

- [ ] **Step 1: Add and commit implementation plan in workspace repo**

```bash
git add docs/superpowers/plans/2026-10-11-tutor-skill-redesign.md
git commit -m "docs: add implementation plan for tutor skill redesign"
```

- [ ] **Step 2: Clean up temporary backup file**

Remove `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md.bak` once the new file is verified.
