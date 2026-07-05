---
name: code-review
description: Focused checklist for YuCLI code review tasks
triggers: [review, code review, 代码审查]
---

Use this skill when the user asks for a review or asks whether the project still has problems.

Prioritize findings over summaries. Read the relevant production code, tests, docs, and command parsing path before deciding there are no issues.

Check these areas first:

- Behavioral regressions in Agent, Plan, Team, hooks, MCP, plugin, skill, and policy flows.
- Missing tests for new command parsing, config loading, and generated example artifacts.
- User-visible docs that drift from code behavior.
- Safety boundaries around filesystem paths, command execution, secrets, and remote URLs.

When reporting results, lead with concrete file and line references. If no issue is found, state the remaining test gap or manual verification gap.
