# Workspace Rules (Wupp)

## Hard Rule: Always Check Issue References & Screenshots

Whenever checking, analyzing, summarizing, or fixing any GitHub issue:

1. **Fetch full context first:** Read both the full issue `body` and all `comments` (`gh issue view <id> --comments --json title,body,comments,state`).
2. **Inspect every item in `Screenshots or references` (and anywhere else in the issue or comments):**
   - **Images / Screenshots / Videos:** Download attached media (e.g. GitHub `user-attachments` or image URLs) into `<appDataDir>\brain\<conversation-id>\scratch\` and open/inspect them visually with `read_file` before proposing or coding a solution.
   - **Referenced Issues / PRs / Commits:** Follow every `#issue`, PR, or commit reference via `gh issue view` / `gh pr view` / `git show` so related context is never missed.
   - **External Links / Reference Apps:** Review any referenced URLs or docs notes explicitly cited by the reporter.
3. **Report findings explicitly:** When summarizing an issue ("check and tell"), always note what was in the references/screenshots (or state clearly if none were provided).

## Hard Rule: Always Report Realistic WhatsApp Flag Risk

Whenever analyzing, proposing, or implementing any feature, bug fix, or protocol/UI behavior change in Wupp:

1. **Always include an explicit `WhatsApp Flag Risk` section** in the response—never wait for the user to ask `"flag?"`.
2. **Stay realistic and accurate:** Never claim `"0% risk"` or `"100% safe"` (any unofficial `whatsmeow` companion client carries non-zero baseline detection risk).
3. **Break down what touches WhatsApp servers vs what is local-only:** State whether the change adds/modifies network stanzas, receipts, presence, peer requests, upload metadata, or polling—plus any rate limits/guards and residual edge-case risks.

## Hard Rule: Always Write Concise, Simple Changelogs

Whenever updating `CHANGELOG.md` or preparing release/pre-release notes:

1. **Keep entries short and simple:** Use 1 clear, user-facing bullet per fix or feature in plain everyday English (no multi-clause explanations or internal symbol names).
2. **Match release automation headers:** Always place entries under `## [<version>] - YYYY-MM-DD` cleanly so `.github/workflows/release.yml` extracts the exact release notes.
