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
- Static auth configs in env files (see `docs/auth.md`)
- Warnings for unresolved variables before execution
- Full response viewer: status, headers, body, and quick compare
- Response view settings: view as JSON/XML/HTML/Text, line numbers, header folding, and soft wraps
- Response viewer tools: scroll to top/end and copy response body
- Service tool window with history and one-click re-run
- File upload with `< path` and response save with `> path`
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

You can specify the HTTP version in the request line (default is HTTP/1.1):

```http
GET https://example.com HTTP/2
```

### Files

Upload a file (multipart):

```http
POST https://httpbin.org/post
Content-Type: multipart/form-data; boundary=WebAppBoundary
Accept: application/json

--WebAppBoundary
Content-Disposition: form-data; name="file"; filename="note.txt"

< ./note.txt
--WebAppBoundary--
```

Save a response to a file:

```http
GET https://httpbin.org/image/png
Accept: image/png
> ./downloads/image.png
```

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
- **Paste cURL as HTTP** — convert cURL to `.http` (supports `--http1.1`, `--http2`, `--http2-prior-knowledge`)
- **New Request Templates** — insert GET/POST/POST (File)/PUT/PATCH/DELETE templates
- **Add Environment Variable** — add variable to shared or private env file
- **Add Auth Configuration** — add static auth template to shared or private env file

## Toolbar

When you open a `.http` file, ReqRun shows an inline toolbar above the editor:

- **New Request** — insert request templates and env variables
- **Export / Import** — copy as cURL or paste from cURL
- **Run All Requests** — execute every request in the current file
- **Run With** — select environment or open env files
- **Examples** — open curated examples

## Response Viewer

Response results open in the Service tool window and include:

- View As modes for JSON/XML/HTML/Text (Auto by Content-Type)
- Soft wraps and line numbers toggles
- Header folding for non-empty responses
- Quick actions: scroll to top/end, copy response body

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

Also available there:
- Toggle short URLs in request history (hide host).

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

## Static Auth

ReqRun supports static auth configs defined under `Security.Auth` in env files and referenced via `{{$auth.token("id")}}` or `{{$auth.header("id")}}`.
Full auth documentation: `docs/auth.md`.

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
