---
allowed-tools: Read, Write, Edit, Glob, Grep, MultiEdit, AskUserQuestion
description: Creates a structured engineering implementation plan from user requirements. Handles incomplete/vague inputs by inferring intent. Outputs consistent, idiot-proof documentation.
argument-hint: [file prefix] [user prompt]
model: opus
---

# Plan Create

Create a comprehensive implementation plan based on user requirements. The output must be detailed enough that any developer can implement the solution without prior context, even if the original request was incomplete or poorly articulated.

## Variables

FILE_PREFIX: $ARGUMENTS.filePrefix
USER_PROMPT: $ARGUMENTS.prompt
STACK_FILE: `.claude/project-stack.md`
PLAN_OUTPUT_DIRECTORY: read from `STACK_FILE` (section "Plan Output Directory"); if `STACK_FILE` defines none, default to `.claude/plans`

## Project Stack Reference

This command is stack-agnostic. Before analyzing the request, read `STACK_FILE` to learn this project's technology, module structure, build/verify commands, theming source, and conventions. Use it to ground the plan's technical design, file paths, success criteria, and design-token mappings in the real stack - never invent framework details.

**If `STACK_FILE` does not exist**, still produce a plan, but: (a) note in Section 7 (Assumptions) that no project-stack file was found, and (b) list "Create `.claude/project-stack.md`" as the first open question in Section 12. Do not fabricate specific commands or file layouts you cannot verify.

## Instructions

### Input Handling
- Assume the user knows what they want but may not express it perfectly - extract intent, not just literal words
- If prompt is vague/incomplete: infer reasonable defaults, document assumptions in the plan
- If prompt contains contradictions: choose the most sensible interpretation, note the resolution
- If critical information is truly missing: surface it in the Scope Clarification phase (see below) - never as a standalone one-off question outside that phase
- Never fail silently - always produce output, documenting uncertainties explicitly

### Scope Clarification

**When to run**: After Analyze Input, BEFORE Design Solution - unless skipped (see below).

**Skip conditions**: If `USER_PROMPT` contains any of the following phrases, skip this phase entirely. Log accepted defaults in Section 7 with the note "User opted out of clarification":
- `skip questions`
- `no questions`
- `proceed directly`
- `just do it`

**When to ask**: Ask only when **2 or more** of these conditions apply to the prompt:
- Scope boundary is ambiguous (what is in vs out, what neighboring code stays untouched)
- Multiple implementation directions exist that materially change the plan's shape (local fix vs systemic refactor, symptom vs root cause)
- Ripple effects on adjacent screens, call sites, or tests are unclear
- A critical technical fork exists where the wrong choice would require replanning (e.g. "new DB table vs reuse existing one", "migration vs runtime fallback")

**When NOT to ask**: If the prompt is specific enough to make confident calls on all of the above, fall back to inferred defaults and document them in Section 7. Do not ask.

**How to ask**: Use `AskUserQuestion` - one batch, never salami-style:
- Maximum **2-5 questions**. Prefer 2 sharp questions over 5 vague ones.
- Every question must include a proposed default: `"Default: X - if you don't specify, I'll go with this."`
- Focus only on:
  - ✅ Scope boundaries (in/out, what stays untouched)
  - ✅ Alternative directions (local fix vs systemic refactor, symptom vs root cause)
  - ✅ Ripple effects (neighboring screens, call sites, tests)
  - ✅ Critical technical forks that change the plan shape
  - ❌ Not naming, formatting, file structure, or minor implementation details - decide those yourself
- After receiving answers, log all accepted defaults (including confirmed ones) in Section 7.

### Solution Design
- Ultrathink through 2-3 implementation approaches; document ALL of them (with tradeoffs) in Section 12, not only the selected one
- Every technical decision must have explicit reasoning documented
- Consider: existing code patterns, security implications, edge cases, scalability, error handling
- Map dependencies and integration points with existing systems

### Document Requirements
- Follow the OUTPUT TEMPLATE exactly - sections 1-8 and 12 are mandatory, sections 9-11 are conditional (see rules below)
- Section 12 (open questions, alternatives, suggestions) is intentionally the last section - it is what the reader sees last and must always be populated
- Write for someone with zero context on this project
- No vague steps like "figure out" or "determine" - be specific
- Include code examples where they clarify complex concepts
- Include ASCII diagrams for architecture when helpful
- Success criteria must be specific and testable

### Conditional Sections
- **Section 9 (DESIGN REFERENCE)**: Include when the task involves any UI/visual changes, design specs (Figma, screenshots, wireframes), or style adjustments. Omit for pure backend/logic-only work.
- **Section 10 (CORRECTIONS FROM CURRENT STATE)**: Include when the task fixes, refactors, or corrects existing code/design. Shows a clear before→after diff. Omit for greenfield features with no prior implementation.
- **Section 11 (CHANGELOG)**: Always include. Start with `Initial plan created` entry. This section tracks revisions if the plan is updated later.

### Quality Standards
- Verify all mandatory sections (1-8, 12) are present before saving
- Verify conditional sections (9-11) are included/omitted per their rules
- Section 12 MUST list at least 2 alternative approaches with tradeoffs (even if one is clearly best - document why the others were rejected)
- Ensure code examples are syntactically correct
- Confirm a developer could implement this without asking questions
- If task involves UI: Section 9 MUST have concrete style mappings (design token → code equivalent)
- If task changes existing code: Section 10 MUST show before/after for every changed aspect

## Workflow

0. **Load Project Stack** - Read `STACK_FILE` and resolve `PLAN_OUTPUT_DIRECTORY` from it. Keep its module structure, build commands, theming source, and conventions in mind for the rest of the workflow.

1. **Analyze Input** - Parse `USER_PROMPT` to understand core problem and desired outcome. Identify missing information. Infer reasonable defaults for gaps. Define clear acceptance criteria even if user didn't provide them. Check for escape-hatch phrases (`skip questions`, `no questions`, `proceed directly`, `just do it`).

2. **Clarify Scope** - Apply the Scope Clarification rules above. If 2+ open scope questions exist and the user did not opt out, call `AskUserQuestion` with a single batch of 2-5 questions (each with a proposed default). Wait for answers before proceeding. If skipping, note all accepted defaults in Section 7.

3. **Design Solution** - Ultrathink through implementation approaches. Select best approach with documented reasoning. Identify risks, edge cases, and integration points.

4. **Generate Document** - Create plan following the OUTPUT TEMPLATE exactly. Every section must be populated. Document all assumptions made due to incomplete input.

5. **Generate Filename** - Create descriptive kebab-case filename based on plan's main topic.

6. **Save & Report** - Write plan to `PLAN_OUTPUT_DIRECTORY/<FILE_PREFIX>-<name-of-plan>.md`. Provide summary using the REPORT FORMAT.

## Output Template

The generated plan MUST follow this exact markdown structure:

```
# [Plan Title in Title Case]

> **Summary**: [Single sentence describing what this plan achieves]

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
[2-3 sentences explaining the problem in plain English. Non-technical reader should understand.]

### 1.2 Solution Overview
[2-3 sentences explaining the approach. Keep it simple.]

### 1.3 Scope: What This IS
- [Included item]
- [Included item]

### 1.4 Scope: What This IS NOT
- [Excluded item]
- [Excluded item]

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | [Measurable outcome] | [How to test/confirm] |
| 2 | [Measurable outcome] | [How to test/confirm] |
| 3 | [Measurable outcome] | [How to test/confirm] |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Component  │────▶│  Component  │────▶│  Component  │
└─────────────┘     └─────────────┘     └─────────────┘
```

[Brief description of architecture and data flow]

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| [Decision point] | [Selected option] | [Why this choice] |
| [Decision point] | [Selected option] | [Why this choice] |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip.

### Step 1: [Action Verb] [What]
**Goal**: [What this achieves]
**Files**: `path/to/file.ext`

```language
// Code example if helpful
```

```
**Done when**: [Verification check]

---

### Step 2: [Action Verb] [What]
**Goal**: [What this achieves]
**Files**: `path/to/file.ext`

**Done when**: [Verification check]

---

[Continue for all steps...]

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| [Edge case] | [What should happen] | [Implementation approach] |
| [Error condition] | [Error behavior] | [Recovery/handling] |

---

## 6. SECURITY CONSIDERATIONS

> Omit section only if genuinely no security implications.

- **Input validation**: [What needs sanitization]
- **Auth/Access control**: [Requirements]
- **Sensitive data**: [Handling approach]
- **Logging**: [What to log / what NOT to log]

---

## 7. ASSUMPTIONS

Inferred from incomplete input - verify these are correct:

1. **[Assumption]**: [Why assumed, impact if wrong]
2. **[Assumption]**: [Why assumed, impact if wrong]

> Open questions live in Section 12 (end of document).

---

## 8. QUICK REFERENCE

### Files to Modify
- `path/to/file.ext` - [Change description]

### Files to Create
- `path/to/new_file.ext` - [Purpose]

### Dependencies
- `package@version` - [Purpose]

### Commands
```bash
# Setup
command

# Verify
command
```

---

## 9. DESIGN REFERENCE

> Include this section when the task involves UI changes, visual updates, or follows a design spec (Figma, screenshot, wireframe, etc.). Omit entirely for backend-only or logic-only changes.

### Visual Spec

[Reference to design source: Figma URL, screenshot path, wireframe, or verbal description from user. Include node IDs or frame names if available.]

### Component/Screen Mapping

[Describe which design elements map to which code components, using the module structure from `STACK_FILE`. Example: "Login card in Figma frame X → `LoginCard` composable in `androidApp/src/main/kotlin/.../login/LoginCard.kt`"]

### Style Mapping

> Map design specs to the theming source defined in `STACK_FILE` (never hardcoded values in UI code).

| Design Spec | Code Equivalent | Value |
|-------------|-----------------|-------|
| [Design token/style name] | [Theme property or constant] | [Concrete value] |
| [Design token/style name] | [Theme property or constant] | [Concrete value] |

---

## 10. CORRECTIONS FROM CURRENT STATE

> Include this section when the task fixes, refactors, or corrects existing behavior/design. Omit for greenfield features with no prior implementation.

| What | Before (Wrong/Current) | After (Correct/Target) |
|------|------------------------|------------------------|
| [Aspect being changed] | [Current state] | [Desired state] |
| [Aspect being changed] | [Current state] | [Desired state] |

---

## 11. CHANGELOG

| Date | Change |
|------|--------|
| [YYYY-MM-DD] | Initial plan created |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

> Always include. This is the last section the reader sees - it surfaces what is still uncertain and what other paths exist.

### 12.1 Alternative Approaches Considered

> List 2-3 approaches that were evaluated, including the one selected. Show tradeoffs so a future reader can revisit the decision if constraints change.

| Approach | Pros | Cons | Selected? |
|----------|------|------|-----------|
| [Approach A] | [Pros] | [Cons] | ✅ |
| [Approach B] | [Pros] | [Cons] | - |
| [Approach C] | [Pros] | [Cons] | - |

**Why the selected approach won**: [1-2 sentences tying back to project constraints / priorities]

### 12.2 Open Questions

> Unresolved items. Each MUST have a proposed direction so the reader has a starting point, not just a blocker.

- [ ] **[Question]** - Proposed direction: [suggested answer or how to find one]
- [ ] **[Question]** - Proposed direction: [suggested answer or how to find one]

### 12.3 Suggestions & Follow-ups

> Out of scope for this plan but worth noting: future improvements, related cleanups, risks to watch.

- [Suggestion]
- [Suggestion]

---

## Report Format

After saving, output:

```
✅ Implementation Plan Created

📄 File: `PLAN_OUTPUT_DIRECTORY/<FILE_PREFIX>-<name-of-plan>.md`
📋 Topic: [Brief description]

🎯 Success Criteria: [count] items
📝 Implementation Steps: [count] steps
⚠️ Assumptions Made: [count]
🔀 Alternatives Documented: [count]
❓ Open Questions: [count]
💡 Suggestions / Follow-ups: [count]
🎨 Design Reference: [included / omitted (reason)]
🔄 Corrections Table: [included / omitted (reason)]
```
