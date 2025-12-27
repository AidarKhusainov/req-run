# Workflow

Add a feature safely
- Clarify the user-visible behavior and affected components.
- Update parser/model/runner first, then UI hooks.
- Add or update tests for parsing, env resolution, and runner behavior.
- Validate UI behavior on a small .http file with multiple blocks.
- Document any new invariants or decisions in `ai/DECISIONS.md`.

Run locally
- `./gradlew runIde`

Test
- `./gradlew test`
- If adding parsing rules, include focused unit tests.

Release (high level)
- Update `CHANGELOG.md`.
- Ensure compatibility target is unchanged.
- Follow existing `RELEASING.md` steps.

Examples

.http snippet
```http
### Get user
@userId = 42
GET https://api.example.com/users/{{userId}}
Accept: application/json

### Create user
POST https://api.example.com/users
Content-Type: application/json

{"name": "Ada"}
```

env snippet
```json
{
  "dev": {
    "baseUrl": "https://api.dev.example.com",
    "token": "dev-token"
  }
}
```

How to prompt an AI with this knowledge base
```
You are contributing to the ReqRun IntelliJ IDEA Community plugin. Use /ai/CONTEXT.md, /ai/RULES.md, /ai/DECISIONS.md, /ai/GLOSSARY.md, /ai/WORKFLOW.md, /ai/CHECKLIST.md. Follow the invariants, parsing rules, and UI conventions. Propose changes with minimal abstractions, Kotlin-first, IC-compatible APIs only. Call out threading, caching, and error reporting impacts.
```
