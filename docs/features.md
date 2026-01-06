# Features

This page lists every user-facing capability currently supported by ReqRun, grouped by area.

## Request execution

- Run the request block at the caret or a selected snippet from the editor; supports `Ctrl+Alt+R`. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Run all requests in the current selection, file, or folder from the group action. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Run all requests in the current file from the inline toolbar. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Requests are cancellable via the background progress indicator during execution. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Request blocks are separated by `###` and comments use `#`. See [request-format.md](request-format.md).

## Editor UX

- Gutter run icons appear next to request blocks. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Inline toolbar provides templates, cURL import/export, run-all, and environment selector. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Editor context menu groups ReqRun actions (run, run group, cURL import/export). See [toolbar-and-actions.md](toolbar-and-actions.md).
- Request templates: GET, POST, POST (File), PUT, PATCH, DELETE. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Insert helpers for env variables and static auth configs in shared or private env files. See [toolbar-and-actions.md](toolbar-and-actions.md).
- Examples link opens curated `.http` samples. See [toolbar-and-actions.md](toolbar-and-actions.md).

## Variables

- File variables defined as `@name = value`. See [variables.md](variables.md).
- Placeholders `{{var}}` in URL, headers, and body. See [variables.md](variables.md).
- Built-in variables `{{$timestamp}}`, `{{$uuid}}`, `{{$randomInt}}`. See [variables.md](variables.md).
- Variable precedence: file variables override environment variables; built-ins are resolved only via `{{$...}}`. See [variables.md](variables.md).
- Unresolved variables are reported before execution and requests are skipped when unresolved. See [variables.md](variables.md).

## Environments

- Shared env file `http-client.env.json` and private env file `http-client.private.env.json`. See [environments.md](environments.md).
- Private env variables override shared values by key. See [environments.md](environments.md).
- Environment discovery scans upward from the request file to project root. See [environments.md](environments.md).
- Environment selection via toolbar (including "No Environment"). See [environments.md](environments.md).
- Create/open env files from the toolbar and environment selector. See [environments.md](environments.md).

## Authentication

- Static auth configs under `Security.Auth` in env files. See [auth.md](auth.md).
- Supported schemes: Bearer, Basic, ApiKey (aliases accepted). See [auth.md](auth.md).
- Auth helpers: `{{$auth.token("id")}}` and `{{$auth.header("id")}}`. See [auth.md](auth.md).
- Placeholders and built-ins are supported inside auth fields. See [auth.md](auth.md).

## Response viewer

- Response viewer in the service tool window shows status, headers, body, and metadata. See [response-viewer.md](response-viewer.md).
- View modes: Auto, Text, JSON, XML, HTML. See [response-viewer.md](response-viewer.md).
- Actions: soft wraps, line numbers, header folding, scroll to top/end, copy response body. See [response-viewer.md](response-viewer.md).
- Compare response with clipboard via the response viewer context menu. See [response-viewer.md](response-viewer.md).
- Response body saving via `> / >>` shows the saved path in the viewer. See [request-format.md](request-format.md).

## History and service tool window

- Service tool window lists request history with one-click re-run. See [response-viewer.md](response-viewer.md).
- Clear all history from the context menu. See [response-viewer.md](response-viewer.md).
- History is capped (old entries are evicted) to avoid unbounded growth. See [response-viewer.md](response-viewer.md).
- Optional URL shortening for history items. See [environments.md](environments.md).

## cURL import/export

- Copy the current request as a cURL command. See [curl-import-export.md](curl-import-export.md).
- Paste a cURL command and convert it to `.http`. See [curl-import-export.md](curl-import-export.md).
- cURL HTTP version flags are mapped to request line versions. See [curl-import-export.md](curl-import-export.md).

## Settings

- Configure shared/private env file paths with global or project scope. See [environments.md](environments.md).
- Toggle shortened request history URLs. See [environments.md](environments.md).
- Response viewer settings: view mode, line numbers, header folding, request method visibility. See [response-viewer.md](response-viewer.md).

## Verification and diagnostics

- Warnings for invalid env JSON are shown via notifications. See [troubleshooting.md](troubleshooting.md).
- Missing or incomplete auth config warnings are reported before execution. See [auth.md](auth.md).
- Unresolved variables are reported with details before execution. See [variables.md](variables.md).
