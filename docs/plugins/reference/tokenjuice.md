---
summary: "Compacts exec and bash tool results with tokenjuice reducers."
read_when:
  - You are installing, configuring, or auditing the tokenjuice plugin
title: "Tokenjuice plugin"
---

# Tokenjuice plugin

Compacts exec and bash tool results with tokenjuice reducers.

## Distribution

- Package: `@openclaw/tokenjuice`
- Install route: npm; ClawHub: `clawhub:@openclaw/tokenjuice`

## Surface

contracts: `agentToolResultMiddleware`

<!-- openclaw-plugin-reference:manual-start -->

### Runtime caveat

Tokenjuice registers `agentToolResultMiddleware` for both `openclaw` and `codex` runtimes.

- On the `openclaw` runtime it compacts `exec` and `bash` tool results before they are fed back into the model.
- On the `codex` runtime it runs as an observer in the Codex app-server `post_tool_use` hook. Native codex-rs `bash` and `exec` results cannot be replaced, so the compacted output only reaches `after_tool_call` observers and is not visible to the Codex model.

<!-- openclaw-plugin-reference:manual-end -->

## Related docs

- [Tokenjuice](/tools/tokenjuice)
