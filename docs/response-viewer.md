# Response viewer

Responses open in the **service tool window** under ReqRun. Each request execution becomes a history entry you can re-open.

## What you see

- Request line (optional), request headers, and request body
- Response status line, headers, and body
- Metadata: response time and content length
- Saved response path if `> / >>` was used

## View modes

Use **View As** to change how the body is rendered:

- Auto
- Text
- JSON
- XML
- HTML

Auto mode chooses a view based on `Content-Type` (JSON/XML/HTML) and falls back to Text.

If JSON parsing fails, the viewer shows a parse error plus the raw body.

## Toolbar actions

- Soft wraps toggle
- Show line numbers
- Fold headers of non-empty responses by default
- Show request method (rerun to apply)
- Scroll to top / end
- Copy response body

## Context menu

- Compare with Clipboard: opens a diff between the response and clipboard text

## History

- Re-run a request from the service view toolbar or context menu
- Clear all history entries from the context menu
- History is capped (older entries are evicted automatically)
- Optional URL shortening for history titles
