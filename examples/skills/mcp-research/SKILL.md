---
name: mcp-research
description: Use MCP resources and prompts as first-class research context
triggers: [mcp, resource, @server, research]
---

Use this skill when the task mentions MCP servers, resources, prompts, or external context exposed through `@server:protocol://path`.

Workflow:

- Run `/mcp` or inspect MCP status before assuming a server is available.
- Prefer explicit resources supplied by the user through `@server:...` mentions when they are present.
- Use `/mcp resources <name>` to discover readable resources and `/mcp prompts <name>` to inspect prompt templates.
- Treat MCP tools as external tools with side effects unless the server description clearly says they are read-only.
- If a server is disabled or missing credentials, explain the exact config key or environment variable needed.

For docs or repo analysis, combine MCP resources with local file reads instead of letting one source override the other.
