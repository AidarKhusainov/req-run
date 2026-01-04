# Toolbar and actions

This page lists ReqRun actions, where to find them, and their shortcuts.

## Inline editor toolbar

Appears when a `.http` file is open.

- **Add to HTTP Client...** - insert request templates and env/auth helpers
  - Templates: GET, POST, POST (File), PUT, PATCH, DELETE
  - Env helpers: add variable to shared/private env file
  - Auth helpers: add static auth config to shared/private env file
- **Export** - `Copy as cURL`
- **Import** - `Paste cURL`
- **Run All Requests** - run all request blocks in the current file
- **Run With** - select environment, open shared/private env files
- **Examples** - opens curated `.http` examples

## Editor gutter

- **Run request** - click the run icon next to a request block

## Editor context menu

Right-click in a `.http` file:

- `Run HTTP Request`
- `Run Selected Requests`
- `Convert to cURL and Copy`
- `Paste cURL as HTTP`

## Tools menu

- `Run HTTP Request` (`Ctrl+Alt+R`)
- `Run HTTP Requests (Group)`
- `Convert to cURL and Copy`
- `Paste cURL as HTTP`

## Project view context menu

- `Run HTTP Requests (Group)` for selected files/folders

## Related

- `request-format.md`
- `environments.md`
- `curl-import-export.md`
- `examples/basic.http`
