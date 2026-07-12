---
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Agent
description: Updates an existing implementation plan with new requirements, bug fixes, or scope changes. Finds the plan by prefix, applies the requested changes, then automatically implements the updated/new steps.
argument-hint: [plan prefix] [next steps description]
model: opus
---

# Plan Update

Update an existing implementation plan based on new information. This could be bug reports from QA, scope changes, new requirements, design corrections, or any other reason the plan needs revision. The updated plan must remain self-contained - a developer reading only the updated plan should be able to implement everything without seeing the original.

## Variables

PLAN_PREFIX: $ARGUMENTS.planPrefix
NEXT_STEPS: $ARGUMENTS.nextSteps
STACK_FILE: `.claude/project-stack.md`
PLAN_OUTPUT_DIRECTORY: read from `STACK_FILE` (section "Plan Output Directory"); if `STACK_FILE` defines none, default to `.claude/plans`

## Project Stack Reference

This command is stack-agnostic. Before locating or editing the plan, read `STACK_FILE` to resolve `PLAN_OUTPUT_DIRECTORY` and to learn this project's module structure, build/verify commands, dependency injection, theming source, code style, code generation, and localization system. The Phase B implementation rules below defer to these values.

**If `STACK_FILE` does not exist**, STOP and tell the user to create it (see `.claude/project-stack.template.md`). Do not guess the stack.

## Rules - Read These First

1. **Never destroy information.** When updating a plan, preserve all existing context that is still valid. Only modify/remove sections that are explicitly contradicted by the new input.
2. **Maintain plan integrity.** After your edits, the plan must still be a complete, self-contained document. No dangling references, no orphaned steps, no broken numbering.
3. **Track every change.** Every modification must be reflected in the CHANGELOG (Section 11). The changelog is the audit trail - it must be accurate and complete.
4. **Respect the original plan structure.** Follow the same template and conventions used in the existing plan. Do not restructure sections unless the update requires it.
5. **Renumber, don't leave gaps.** If steps are inserted, removed, or reordered, renumber all subsequent steps so the sequence is clean.

## Instructions

### Input Handling
- `PLAN_PREFIX` is the ticket/plan prefix used to locate the plan file (e.g., `TA-2044`; see the ticket convention in `STACK_FILE` if defined). Search for files matching `PLAN_PREFIX*` in the `PLAN_OUTPUT_DIRECTORY`.
- `NEXT_STEPS` is freeform text describing what needs to change. It may be:
  - A QA bug report ("tester found X doesn't work when Y")
  - A scope change ("we also need to handle Z")
  - A requirement correction ("actually the button should be red, not blue")
  - A partial rollback ("remove step 3, it's no longer needed")
  - Additional follow-up work ("we also need to add analytics events")
  - Any combination of the above
- If `NEXT_STEPS` is vague, infer the most reasonable interpretation and document your assumptions.
- If `NEXT_STEPS` contradicts the existing plan, the new input wins - update the plan accordingly and note the change in the changelog.

### Locating the Plan

1. Search `PLAN_OUTPUT_DIRECTORY` for files matching `PLAN_PREFIX*`.
2. If exactly one file matches - use it.
3. If multiple files match - list them and ask which one to update.
4. If no files match - report the error and stop. Do NOT create a new plan (use `/plan-create` for that).

### Analysis

Before making changes, analyze the impact of `NEXT_STEPS` on the existing plan:

1. **Read the entire existing plan.** Understand all sections, steps, dependencies.
2. **Read ALL files referenced** in the plan's Quick Reference (Section 8) to understand current codebase state - including any changes that may have already been implemented from the original plan.
3. **Classify the update type(s)**:
  - `BUG_FIX` - Fixing something that was implemented but doesn't work correctly
  - `SCOPE_ADDITION` - Adding new functionality or steps
  - `SCOPE_REMOVAL` - Removing planned functionality or steps
  - `REQUIREMENT_CHANGE` - Modifying existing requirements or behavior
  - `DESIGN_CORRECTION` - Fixing visual/UI specifications
  - `CLARIFICATION` - Adding detail to ambiguous areas without changing scope
4. **Map affected sections.** Determine which plan sections need modification based on the update type(s).

### Applying Changes

For each affected section, apply changes according to these rules:

#### Section 1 (PROBLEM & SOLUTION)
- Update Problem Statement only if the problem itself has changed
- Update Solution Overview if the approach changes
- Update Scope (IS / IS NOT) to reflect additions or removals

#### Section 2 (SUCCESS CRITERIA)
- Add new criteria for new scope
- Modify existing criteria if requirements changed
- Remove criteria for removed scope
- **Every criterion must be testable** - no vague "works correctly" statements

#### Section 3 (TECHNICAL DESIGN)
- Update architecture diagram if components change
- Add/modify Key Decisions for new technical choices
- Preserve existing decisions that are still valid

#### Section 4 (IMPLEMENTATION STEPS)
- **For BUG_FIX**: Add new fix steps at the appropriate position in the sequence. If a previous step produced the bug, update that step directly instead of adding a new one. Mark the step clearly if it replaces or corrects a previous step's implementation.
- **For SCOPE_ADDITION**: Insert new steps at the logical position. Consider dependencies - new steps may need to come before or after existing ones.
- **For SCOPE_REMOVAL**: Remove the steps. Update any steps that depended on removed steps.
- **For REQUIREMENT_CHANGE**: Modify affected steps in-place.
- Always renumber steps after insertions/removals so they form a clean 1..N sequence.
- Every step must have: title, goal, files, "done when" criterion.

#### Section 5 (EDGE CASES & ERRORS)
- Add new edge cases relevant to the update
- Remove edge cases that no longer apply

#### Section 6 (SECURITY CONSIDERATIONS)
- Update if the change introduces new security implications

#### Section 7 (ASSUMPTIONS & QUESTIONS)
- Add new assumptions made during this update
- Mark resolved questions with ✅ and the answer
- Add new open questions if the update introduces uncertainty

#### Section 8 (QUICK REFERENCE)
- Add/remove files, dependencies, commands as needed
- This section must be the single source of truth for "what files does this plan touch"

#### Section 9 (DESIGN REFERENCE) - conditional
- Add or update if the change involves UI/visual modifications
- Include only if design-related changes are part of this update or the original plan

#### Section 10 (CORRECTIONS FROM CURRENT STATE) - conditional
- **Always include this section in the update** if the change type is BUG_FIX, REQUIREMENT_CHANGE, or DESIGN_CORRECTION
- Show before (current wrong state) → after (target correct state) for each correction
- If this section already exists, append new rows - do not overwrite existing corrections

#### Section 11 (CHANGELOG)
- **Always add a new entry** with today's date
- Entry must briefly describe ALL changes made in this update
- Format: `| YYYY-MM-DD | [Update type]: [concise description of all changes] |`

### Quality Checks

Before saving, verify:
- [ ] All mandatory sections (1-8) are present and internally consistent
- [ ] Conditional sections (9-11) are included/omitted per their rules
- [ ] Implementation steps form a clean 1..N sequence with no gaps
- [ ] No step references a removed step or a file no longer in Quick Reference
- [ ] Success criteria cover all new/changed scope
- [ ] Changelog entry accurately describes what changed
- [ ] The plan is still self-contained - a fresh reader can implement it without external context
- [ ] Code examples (if any) are syntactically correct

## Workflow

### Phase A: UPDATE THE PLAN

0. **Load Project Stack** - Read `STACK_FILE`, resolve `PLAN_OUTPUT_DIRECTORY`, and apply the Project Stack Reference. If missing, stop.
1. **Locate Plan** - Find the plan file by `PLAN_PREFIX` in `PLAN_OUTPUT_DIRECTORY`.
2. **Read Plan** - Read the entire existing plan and all referenced files.
3. **Analyze Impact** - Classify update type(s), map affected sections.
4. **Report Analysis** - Tell the user what you found and what you'll change (use ANALYSIS FORMAT below).
5. **Apply Changes** - Update all affected sections following the rules above.
6. **Quality Check** - Run through the checklist.
7. **Save & Report** - Overwrite the plan file with the updated version. Report using REPORT FORMAT.

### Phase B: IMPLEMENT THE CHANGES

After the plan is saved, **automatically proceed to implement the updated/new steps.** Do not wait for user confirmation - the whole point of this command is to update AND fix in one go.

#### Identifying what to implement
- **Do NOT re-implement the entire plan from scratch.** Only implement changes introduced by this update.
- Determine which steps are new or modified by comparing the updated plan to the original.
- Steps that were already implemented and remain unchanged should be skipped.
- If the update modifies an already-implemented step (e.g., a bug fix to existing code), implement just the correction - read the current file state and apply the fix.

#### Execution rules
Follow the same execution discipline as `/plan-implementation`:

1. **One step at a time.** Complete each changed step fully (including verification) before moving to the next.
2. **Read before writing.** Always read the target file before modifying it. Understand the current state - it may differ from what the original plan expected if parts were already implemented.
3. **Stop on failure.** If a step fails verification after 2 attempts, stop and report the blocker.
4. **Preserve existing code.** Only modify files listed in the plan. If you discover you need to touch an unlisted file, note it but ask before proceeding.
5. **Respect design tokens.** All colors, spacing, typography come from the theming source defined in `STACK_FILE`. Never hardcode design values.
6. **Respect line width and code style** as defined in `STACK_FILE`.
7. **Register new services/view models/controllers** in the dependency-injection location defined in `STACK_FILE` if the plan creates any.
8. **Run code generation** (per `STACK_FILE`) if the plan touches types that require it (e.g. persistence entities, serializable models).
9. **Translations**: Only write translations to the source-of-truth locale files that `STACK_FILE` marks as writable (its "Localization" section). Do NOT modify other locale files - those are handled by the separate `/translate` workflow.
10. **Living document**: After implementation, update the plan file to reflect what was actually done - mark step statuses, log deviations, update changelog (see Final Plan Update below).
11. **Respect step statuses**: If a step is already marked `[x]` (completed), skip it unless the code clearly doesn't match. Only implement steps marked `[ ]` (not started) or newly added steps.

#### Step execution format
For each step being implemented:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step [N]/[total changed]: [Step Title]
Goal: [step goal from plan]
Type: [NEW | MODIFIED | BUG_FIX]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

After each step:
```
✅ Step [N]/[total changed] complete: [brief summary]
```
OR
```
❌ Step [N]/[total changed] FAILED: [what went wrong]
   Attempting fix...
```

#### Final verification
After all changed steps are implemented:

1. **Check every Success Criterion** from Section 2 that is new or modified. Verify it is met.
2. **Run static analysis / lint** using the command defined in `STACK_FILE` if any source files were modified. Fix only issues caused by this implementation.
3. **Run code generation** using the command defined in `STACK_FILE` if any types requiring codegen were changed.
4. **Output the final report** (see FINAL REPORT FORMAT below).

#### Final Plan Update
After implementation, **update the plan file again** to reflect what was actually done:

1. **Mark step statuses** - For each implemented step:
  - Completed successfully: `- [x] **Status**: Done`
  - Completed with deviations: `- [x] **Status**: Done (with deviations - see changelog)`
  - Failed / blocked: `- [ ] **Status**: Blocked - [brief reason]`

2. **Log deviations** - If any step was implemented differently, add under that step:
   ```
   > **Deviation**: [what changed and why]
   ```

3. **Update the changelog** - Add a new entry for the implementation:
   ```
   | [YYYY-MM-DD] | Implementation: Steps [N-M] completed. [Any deviations or notes.] |
   ```

4. **Update Quick Reference** - If files were added/removed during implementation, update Section 8.

5. **Save the plan file.**

## Analysis Format

Before applying changes, output:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 Plan Update Analysis
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Plan: [plan title]
File: [plan file path]

Update Type(s): [BUG_FIX | SCOPE_ADDITION | SCOPE_REMOVAL | REQUIREMENT_CHANGE | DESIGN_CORRECTION | CLARIFICATION]

Sections to modify:
  - Section [N]: [brief reason]
  - Section [N]: [brief reason]

Steps impact:
  - [N new steps added | N steps modified | N steps removed | no step changes]

Proceeding with update...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Report Format

After saving, output:

```
✅ Implementation Plan Updated

📄 File: `[plan file path]`
📋 Plan: [plan title]

🔄 Update Type(s): [types]
📝 Sections Modified: [list]
🔢 Steps: [previous count] → [new count] ([+N added | -N removed | N modified])
✅ New Success Criteria: [count added, if any]
⚠️ New Assumptions: [count, if any]

📌 Changelog Entry:
> [the changelog entry that was added]
```

## Final Report Format

After implementation is complete, output:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 PLAN UPDATE & IMPLEMENTATION COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Plan: [Plan Title]
Source: [plan file path]

Update Type(s): [types]
Steps Implemented: [N]/[total changed]

Success Criteria (new/modified):
  ✅ [criterion 1]
  ✅ [criterion 2]
  ❌ [criterion 3 - if any failed]

Files Modified:
  - path/to/file.ext (created|modified)
  - path/to/file.ext (created|modified)

Static Analysis: ✅ Pass | ❌ [N] issues

⚠️ Notes:
  - [Any deviations from the plan]
  - [Any assumptions that turned out wrong]
  - [Any follow-up work needed]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Error Handling

| Situation | Action |
|-----------|--------|
| `STACK_FILE` missing | Stop, ask the user to create `.claude/project-stack.md` (see `.claude/project-stack.template.md`) |
| No plan file matches prefix | Stop, report error, suggest `/plan-create` |
| Multiple plan files match prefix | List all matches, ask which to update |
| Plan file is malformed / missing sections | Report which sections are missing, attempt update on remaining sections |
| `NEXT_STEPS` contradicts multiple plan sections | Apply changes, clearly document all contradictions resolved in changelog |
| `NEXT_STEPS` is too vague to act on | Ask ONE focused clarifying question before proceeding |
| Update would remove all implementation steps | Stop, confirm with user - this effectively deletes the plan |
| Referenced files in codebase have changed since plan was written | Note the drift in assumptions, update steps to reflect current state |
