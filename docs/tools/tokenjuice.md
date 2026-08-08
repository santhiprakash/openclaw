---
summary: "Compact noisy exec and bash tool results with the optional Tokenjuice plugin"
title: "Tokenjuice"
read_when:
  - You want shorter `exec` or `bash` tool results in OpenClaw
  - You want to install or enable the Tokenjuice plugin
  - You need to understand what tokenjuice changes and what it leaves raw
---

`tokenjuice` is an optional external plugin that compacts noisy `exec` and `bash`
tool results after the command has already run.

It changes the returned `tool_result`, not the command itself. Tokenjuice does
not rewrite shell input, rerun commands, or change exit codes.

Today this applies to OpenClaw embedded runs and OpenClaw dynamic tools in the Codex
app-server harness. Tokenjuice hooks OpenClaw's tool-result middleware and
trims the output before it goes back into the active harness session.

## Runtime caveat

A native codex-rs `bash` or `exec` call is one that the Codex runtime executes
directly inside its own sandbox. OpenClaw dynamic tools are `bash` or `exec`
calls that flow through an OpenClaw-managed bridge or sandbox. Tokenjuice does
not compact native codex-rs `bash` or `exec` results because the Codex
`post_tool_use` hook cannot replace `tool_response`; it only observes the result.

## Enable the plugin

Install once:

```bash
openclaw plugins install clawhub:@openclaw/tokenjuice
```

Then enable it:

```bash
openclaw config set plugins.entries.tokenjuice.enabled true
```

Equivalent:

```bash
openclaw plugins enable tokenjuice
```

If you prefer editing config directly:

```json5
{
  plugins: {
    entries: {
      tokenjuice: {
        enabled: true,
      },
    },
  },
}
```

## What tokenjuice changes

- Compacts noisy `exec` and `bash` results before they are fed back into the session, when those tools run through OpenClaw.
- Keeps the original command execution untouched.
- Applies a safe-inventory policy: exact file-content reads stay raw, standalone repository-inventory commands can compact, and unsafe mixed command sequences stay raw.
- Stays opt-in: disable the plugin if you want verbatim output everywhere.
- Does not compact native codex-rs `bash` or `exec` results, because the Codex `post_tool_use` hook cannot replace `tool_response`.

## Verify it is working

1. Enable the plugin.
2. Start an OpenClaw-embedded session, or a session that uses OpenClaw dynamic tools, that can call `exec`.
3. Run a noisy command such as `git status`.
4. Check that the returned tool result is shorter and more structured than the raw shell output.

This check only works for OpenClaw-embedded sessions and OpenClaw dynamic tools in the Codex app-server harness. Native codex-rs `bash` or `exec` results will not be shorter because the compaction is observe-only for those results.

## Disable the plugin

```bash
openclaw config set plugins.entries.tokenjuice.enabled false
```

Or:

```bash
openclaw plugins disable tokenjuice
```

## Related

- [Exec tool](/tools/exec)
- [Thinking levels](/tools/thinking)
- [Context engine](/concepts/context-engine)
