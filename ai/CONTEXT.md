# ReqRun AI Knowledge Base

Purpose: enable consistent contributions without re-explaining context.

Project facts
- IntelliJ Platform Plugin for IntelliJ IDEA Community (IC) only.
- Kotlin + Gradle IntelliJ Plugin.
- Plugin goal: execute HTTP requests defined in .http files.

Core capabilities
- Request blocks separated by `###`.
- Line comments start with `#`.
- File variables: `@name = value`.
- Placeholders: `{{var}}`.
- Built-ins: `{{$timestamp}}`, `{{$uuid}}`, `{{$randomInt}}`.
- Environments: `http-client.env.json` + `http-client.private.env.json` (private overrides shared).
- Environment discovery scans upward to project root; shared/private paths can be configured in Settings.
- UI: gutter run icons, inline toolbar (templates, run-all, env selector, cURL import/export, examples), response viewer tool window + history.
- Actions: run request at caret, run selected requests, run all in file/folder, copy as cURL, paste cURL as HTTP.

Compatibility target
- 2024.3+ (or latest IC used by this project).

Non-goals
- No IntelliJ Ultimate APIs.
- No heavy PSI for parsing when a lightweight parser suffices.
