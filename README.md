# ReqRun

![Build](https://github.com/AidarKhusainov/req-run/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)

<!-- Plugin description -->
**ReqRun** is a minimal HTTP client for IntelliJ IDEA Community Edition.

Run `.http` requests directly from the editor — fast, readable, and without Ultimate.

## Features

- Run request blocks with gutter markers or `Ctrl+Alt+R`
- Run all requests from selection, file, or folder
- Inline toolbar with templates, import/export, run-all, and environment selector
- Environment variables from `http-client.env.json` and `http-client.private.env.json`
- File variables (`@name = value`) and built-in variables (`{{$timestamp}}`, `{{$uuid}}`, `{{$randomInt}}`)
- Warnings for unresolved variables before execution
- Full response viewer: status, headers, body, and quick compare
- Service tool window with history and one-click re-run
- Convert requests to cURL or paste cURL as `.http`
- Line comments with `#` and request separators with `###`

## Request Format

```http
POST https://httpbin.org/post
Content-Type: application/json

{
  "work": "yes"
}
```

Place the caret inside a request block and press `Ctrl+Alt+R`.

## Feedback

Suggestions and bug reports:  
https://github.com/AidarKhusainov/req-run
<!-- Plugin description end -->

---

## Actions

- **Run HTTP Request** — run the request block at the caret (`Ctrl+Alt+R`)
- **Run Selected Requests** — run all requests from selection, file, or folder
- **Run All Requests (Toolbar)** — execute all requests in the current file
- **Convert to cURL and Copy** — copy request as a cURL command
- **Paste cURL as HTTP** — convert cURL to `.http`
- **New Request Templates** — insert GET/POST/PUT/PATCH/DELETE templates
- **Add Environment Variable** — add variable to shared or private env file

## Toolbar

When you open a `.http` file, ReqRun shows an inline toolbar above the editor:

- **New Request** — insert request templates and env variables
- **Export / Import** — copy as cURL or paste from cURL
- **Run All Requests** — execute every request in the current file
- **Run With** — select environment or open env files
- **Examples** — open curated examples

## Variables

### File variables

```http
@baseUrl = https://httpbin.org
@token = demo
```

Use them in requests as `{{baseUrl}}` and `{{token}}`.

### Built-in variables

- `{{$timestamp}}` — Unix timestamp (seconds)
- `{{$uuid}}` — random UUID
- `{{$randomInt}}` — random integer in range 0..1000

## Environments

ReqRun loads environment variables from:

- `http-client.env.json` (shared, committed)
- `http-client.private.env.json` (private, recommended for secrets)

Private variables override shared ones on key conflicts.

Environment discovery scans upward from the current file to the project root.
Custom paths can be configured in:

`Settings → Tools → ReqRun`

Example `http-client.env.json`:

```json
{
  "local": {
    "baseUrl": "https://httpbin.org",
    "token": "demo"
  },
  "stage": {
    "baseUrl": "https://stage.example.com"
  }
}
```

Select the active environment from the toolbar.

## Installation

### From Marketplace

`Settings → Plugins → Marketplace → Search "ReqRun" → Install`

### From Disk

1. Download plugin ZIP from Marketplace or GitHub releases
2. `Settings → Plugins → ⚙ → Install plugin from disk...`

## Releases

Release notes and artifacts:  
https://github.com/AidarKhusainov/req-run/releases

## License

MIT. See `LICENSE`.
