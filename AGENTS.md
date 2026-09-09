## Project Guidelines

- Do not call the task done until it is fully complete and tested.
- Do not dismiss a bug as a pre-existing issue even if it was present before your change. It does not matter, it's still your responsibility to fix it. When you see a bug, fix it. Don't ignore it.
- Always build a feature in a new branch. Do not push directly to the main branch. Always check current branch before pushing.
- Read and follow the engineering principles in `principles.md` before planning or making changes.

## Agent Delegation Guidelines

- Use subagents for analysis whenever subagents are available.
- Delegate every large or significant part of a task to one or more subagents. The main agent remains responsible for integrating the work, verification, and the final result.

## Coding Guidelines

  - Keep code simple and easy to read.
  - Never use em dashes in app user-facing copy.
  - Avoid excessive comments. We want code itself to be super readable. Only comment when absolutely necessary.
  - Do not create or modify implementation code unless the user explicitly asks for code changes.
  - Do not create or modify tests unless the user explicitly asks for tests.

## Git Branch Rules

  - Ask the user before creating a new git branch other do it in main itself.
  - No slashes in branch names (e.g., use `fix-something` not `fix/something`).

## Git Commit Rules

  - use plain text commit without formatting in small case, no emoji or formatting ex. 'fix typo in readme'
  - Never use `git add .` or `git add -A`. Always stage specific files by name.
  - Don't batch multiple unrelated changes into one commit.
  - Commit early and often. A working 5-line change is better than a pending 200-line change.

## PR Description Rules

  - Plain text only. No markdown, no headings, no bullets, no bold, no code blocks, no emoji.
  - A few lines, that's it. Don't write an essay.
  - Super casual, like you're telling a teammate over chat. Lowercase is fine.
  - Don't polish it. A few typos and loose grammar are fine and preferred over something that reads like a template.
  - Keep the facts right even though the tone is casual. Casual is about the voice, not about being vague or wrong.
