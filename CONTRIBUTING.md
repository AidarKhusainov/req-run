# Contributing

Thanks for contributing to ReqRun!

## Build and run

```bash
./gradlew runIde
```

## Tests and checks

```bash
./gradlew check buildPlugin verifyPlugin
```

## Coding style and expectations

- Kotlin first; avoid IntelliJ Ultimate APIs (IC-only).
- Do not block the EDT; long work must be cancellable and off-EDT.
- Keep parsing lightweight and deterministic.
- Prefer small, explicit types over heavy abstractions.
- Use IntelliJ logging and report user-facing issues via notifications.

## Docs updates

If a change is user-visible, update:

- `README.md` (high-level highlights only)
- `docs/` reference pages and examples
- `CHANGELOG.md` for release notes

## Knowledge base

See the AI knowledge base for invariants and decisions:

- `ai/CONTEXT.md`
- `ai/RULES.md`
- `ai/DECISIONS.md`
- `ai/GLOSSARY.md`
- `ai/WORKFLOW.md`
- `ai/CHECKLIST.md`
