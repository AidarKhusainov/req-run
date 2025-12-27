# Rules and Invariants

Non-negotiables
- Do not block the EDT; use background threads for IO/network.
- Keep performance safe; avoid quadratic parsing or repeated file scans.
- No Ultimate-only APIs; IC-compatible only.
- Prefer simple parsing over heavy PSI; keep logic testable.
- Avoid reflection and dynamic class loading and memory leak.

Coding conventions
- Kotlin first; Java only if required by APIs.
- Structure by responsibility: parser, model, runner, env resolver, UI, persistence.
- Favor small, explicit types over “magic” abstractions.
- Validate inputs early; return structured errors instead of throwing for control flow.
- Logging: use IntelliJ logger, avoid noisy logs; include request id where helpful.
- Error handling: propagate user-facing errors to the tool window; keep stack traces in logs.
- Threading: UI updates on EDT; long work off-EDT with cancellation support.
- Caching: explicit lifetimes and invalidation; no hidden global caches.

Do/Don’t
- Do keep parsing deterministic and whitespace-tolerant.
- Do keep UI responsive and state updates minimal.
- Don’t access network on the EDT.
- Don’t add new dependencies without strong need.
- Don’t introduce custom DSLs or annotations.
- Don’t rely on PSI for parsing unless absolutely necessary.
