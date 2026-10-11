# Design Document: Redesigning the `tutor` Skill into a Socratic Personal Tutor

**Date:** 2026-10-11  
**Status:** Approved  
**Target File:** `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md`

---

## 1. Context & Motivation

The existing `tutor` skill acts as a pedagogical precursor to the `/deep-dive` quality gate. However, its execution guidelines were previously too generic, often causing AI assistants to produce long, monolithic text dumps filled with unearned technical jargon that overwhelm the learner.

During an architectural session on a distributed rate limiter, an interactive 1-on-1 "Personal Tutor" method demonstrated superior pedagogical efficacy:
1. Deconstructing complex systems into small, sequential concepts.
2. Anchoring each concept in an intuitive physical or real-world analogy before presenting software code.
3. Enforcing a hard Socratic check-in gate where the user must answer one targeted question before advancing.
4. Affirming the user's strong intuition while refining subtle engineering nuances.

This design formalizes that pedagogical framework as the core default engine of the `tutor` skill.

---

## 2. Architecture & Lifecycle Phases

The updated `tutor` skill is organized into four discrete, sequential phases:

```
[ Phase 1: Discovery & Curriculum Decomposition ]
                         │
                         ▼
[ Phase 2: Roadmap Briefing & Immediate Launch ]
                         │
                         ▼
[ Phase 3: The Micro-Stepping Socratic Loop ] <── (Iterates per step)
                         │
                         ▼
[ Phase 4: Synthesis & Quality Gate Handoff ]
```

### Phase 1: Discovery & Curriculum Decomposition
Before generating educational content, the agent analyzes the context:
1. Inspect the active OpenSpec change or recent `git diff` / PRD.
2. Extract the foundational engineering concepts, architectural trade-offs, and failure modes.
3. Sequence the topics into a progression from physical intuition to low-level implementation (typically 4–8 micro-steps).

### Phase 2: Roadmap Briefing & Immediate Launch
1. Present a concise roadmap outlining the journey (e.g., *"We will break this down into N small, intuitive steps: 1. ..., 2. ... "*).
2. Immediately launch into **Step 1** within the same turn to maintain momentum.

### Phase 3: The Micro-Stepping Socratic Loop
Each step adheres strictly to the **Micro-Stepping Protocol**:
- **The Rule of One:** Exactly one concept per turn. Combining steps or introducing forward-looking concepts is strictly prohibited.
- **Physical Analogy First:** Ground the concept in an everyday physical metaphor (e.g., arcade coin buckets, restaurant waiters, dry cleaner receipts) before introducing technical jargon or code.
- **The Socratic Gate:** End every step with one targeted question testing intuition on failure modes or trade-offs.
- **The Hard Stop:** The agent must immediately cease generation after the question and await user input.
- **The Affirm & Refine Loop:** Upon user reply, celebrate correct intuition, gently refine any subtle nuances in 1–2 sentences, and seamlessly transition into the next step.
- **The Reset Protocol:** If the user indicates confusion, immediately drop one level deeper with an even simpler metaphor without repeating previous text.

### Phase 4: Synthesis & Quality Gate Handoff
Upon completion of all steps:
1. Render a clean two-column markdown summary table: **Concept** vs. **The "Why" You Mastered**.
2. Map the concepts directly back to files and pending tasks in the codebase.
3. Prompt the user to invoke `/deep-dive` to test their retention and earn sign-off.

---

## 3. Strict Anti-Patterns & Guardrails

The updated skill explicitly documents and forbids the following failure modes:
- **Wall of Text / Info-Dumping:** Providing multi-paragraph explanations across several topics at once.
- **Unearned Jargon:** Introducing terms like *I/O multiplexing, TOCTOU, NTP drift, epoll* without prior intuitive grounding.
- **Multi-Question Inquisitions:** Asking more than one concept check at a time.
- **Answering Own Questions:** Posing a rhetorical or Socratic question and answering it in the same message.
- **Skipping the Gate:** Advancing to the next topic before the user has engaged with the current step's question.

---

## 4. Verification & Testing

Verification consists of:
1. Validating that the markdown syntax and frontmatter of `/Users/prajjwalmehta/.gemini/config/skills/tutor/SKILL.md` are well-formed.
2. Conducting an inline self-review against the brainstorming checklist (checking for placeholders, ambiguity, and consistency).
3. Confirming that invoking `/tutor` triggers the step-by-step Socratic curriculum workflow as intended.
