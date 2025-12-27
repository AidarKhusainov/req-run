# Decisions and Rationale

Environment merge
- Rule: load `http-client.env.json`, then overlay `http-client.private.env.json`.
- Rationale: keep shared defaults, allow private secrets to override safely.

Variable precedence
- Rule: file variables `@name = value` override environment variables.
- Built-ins are only referenced via `{{$...}}` and are not shadowed by file vars.
- Rationale: local file intent should be strongest for a request.

Request block detection
- Rule: blocks are split by lines containing `###` (trimmed line match).
- Lines starting with `#` are treated as comments and ignored by parser/extractor.
- Rationale: mirror common .http conventions and keep parsing simple.

Error reporting
- Rule: user-visible warnings/errors are shown via notifications; tool window shows execution results and history.
- Rationale: keep execution UI focused while preserving feedback.
