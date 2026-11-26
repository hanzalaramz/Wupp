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
2. **State the risk verdict and direction clearly:** Start with a concise risk call in simple words—whether the change **increases**, **decreases**, or **keeps the same** flag risk (and why), while staying realistic (never claim `"0% risk"` or `"100% safe"` for an unofficial client like `whatsapp-rust` / `whatsmeow`).
3. **Explain server traffic vs. phone-only work concisely:** Briefly note what talks to WhatsApp servers vs. what stays on the phone—covering key safeguards (cooldowns, batching, caching) and any leftover risk in short, plain everyday English instead of long technical lists of requests.

## Hard Rule: Always Report Realistic DMCA & Takedown Risk

Whenever analyzing, proposing, adding, or changing any asset, library, UI screen, feature, protocol code, or documentation in Wupp:

1. **Always include an explicit `DMCA / Takedown Risk` section alongside `WhatsApp Flag Risk`**—never wait for the user to ask `"dmca?"`.
2. **State the risk verdict and direction clearly:** Start with a concise risk call in simple words—whether the change **increases**, **decreases**, or **keeps the same** DMCA/copyright/trademark takedown risk (and why), never claiming `"0% risk"` for a third-party WhatsApp companion client.
3. **Check assets, naming, and third-party code cleanly:** Confirm that no proprietary audio, fonts, icons/trademarks (e.g. speech-bubble tails or `"WhatsApp"`/`"Cuppy"` branding), or unlicensed binaries are introduced, and explain any leftover protocol/trademark risk in plain everyday English.

## Hard Rule: Always Explain Changes & Risks in Simple Words

Whenever telling the user what changed or explaining `WhatsApp Flag Risk` and `DMCA / Takedown Risk`:

1. **Use plain, everyday English:** Keep sentences short and clear so anyone can read them quickly without decoding heavy technical talk.
2. **Include key technical details simply:** Still cover the important technical facts—such as which parts of the app changed, cooldowns/timings, caching rules, and how server requests behave—explained in clear everyday language instead of cryptic variable names or raw file-line dumps (unless raw symbols are asked for).
3. **Keep both sections easy to scan:** Summarize what changed in a few clear bullets, followed by concise, honest `WhatsApp Flag Risk` and `DMCA / Takedown Risk` breakdowns.

## Hard Rule: Always Write Concise, Simple Changelogs

Whenever updating `CHANGELOG.md` or preparing release/pre-release notes:

1. **Keep entries short and simple:** Use 1 clear, user-facing bullet per fix or feature in plain everyday English (no multi-clause explanations or internal symbol names).
2. **Match release automation headers:** Always place entries under `## [<version>] - YYYY-MM-DD` cleanly so `.github/workflows/release.yml` extracts the exact release notes.

## Hard Rule: Always Use Natural Human Writing (Humanizer), Especially for Comments

Whenever drafting GitHub issue/PR comments, social or community replies, release announcements, user-facing copy, or prose explanations:

1. **Write like a real person:** Use short, direct, natural sentences with zero stiff phrasing, filler hype, or robotic transitions.
2. **Strip classic AI tells:** Avoid filler phrases (`"it is important to note"`, `"additionally"`, `"leverage"`, `"seamless"`, `"comprehensive"`), heavy em-dash chains, and padded bullet-point fluff.
3. **Keep comments & replies sharp:** Get straight to the point, sound conversational and grounded, and state real trade-offs honestly without fake guarantees.

## Hard Rule: Always Open a Pull Request & Check Active Workflows First

Whenever pushing code, merging changes, or creating a release/pre-release in Wupp:

1. **Never push directly to `main`:** Always create a dedicated branch, push that branch, open a Pull Request targeting `main`, wait for CI checks to pass, and merge through the Pull Request.
2. **Always check recent commits and tags first:** Before branching, rebasing, merging a PR, or bumping `CHANGELOG.md` / release tags, run `git fetch origin --tags` and inspect `git log --oneline -n 10 origin/main` plus recent tags so collaborator work and shipped versions are never overwritten or collided with.
3. **Always check active GitHub Actions runs before merging or tagging:** Run `gh run list --limit 10` before merging to `main` or pushing any release tag. If another `Release APK` or `main` `CI` build is currently `in_progress` or `queued`, wait for it to finish cleanly, pull/rebase onto the latest `origin/main`, and then merge or tag.

