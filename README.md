# ReqRun

[![Version](https://img.shields.io/jetbrains/plugin/v/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)

[![IDEs](https://img.shields.io/badge/IDEs-JetBrains-0B7285.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)
[![IDE Since](https://img.shields.io/badge/IDE%20since-241-0B7285.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)
[![IDE Until](https://img.shields.io/badge/IDE%20until-%2A-0B7285.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)

[![Main](https://img.shields.io/github/actions/workflow/status/AidarKhusainov/req-run/main.yml?branch=main&label=Main)](https://github.com/AidarKhusainov/req-run/actions/workflows/main.yml)
[![Qodana](https://img.shields.io/github/actions/workflow/status/AidarKhusainov/req-run/main.yml?branch=main&label=Qodana)](https://github.com/AidarKhusainov/req-run/actions/workflows/main.yml)

[![Release](https://img.shields.io/github/v/release/AidarKhusainov/req-run.svg)](https://github.com/AidarKhusainov/req-run/releases)
[![Last Commit](https://img.shields.io/github/last-commit/AidarKhusainov/req-run.svg)](https://github.com/AidarKhusainov/req-run/commits)
[![Stars](https://img.shields.io/github/stars/AidarKhusainov/req-run.svg)](https://github.com/AidarKhusainov/req-run/stargazers)
[![Contributors](https://img.shields.io/github/contributors/AidarKhusainov/req-run.svg)](https://github.com/AidarKhusainov/req-run/graphs/contributors)
[![License](https://img.shields.io/badge/License-MIT%20-6B4E2E.svg)](LICENSE)

<!-- Plugin description -->
ReqRun is a minimal HTTP client for IntelliJ IDEA Community Edition that runs `.http` requests from the editor.

- Run request blocks with gutter icons or `Ctrl+Alt+R`
- Run selections, entire files, or folders of requests
- Inline toolbar for templates, environments, and cURL import/export
- Environments via `http-client.env.json` and `http-client.private.env.json`
- File variables (`@name = value`) and built-in variables (`{{$timestamp}}`, `{{$uuid}}`, `{{$randomInt}}`)
- Static auth configs under `Security.Auth` with `{{$auth.token(...)}}` and `{{$auth.header(...)}}`
- Response viewer with history in the service tool window

```http
GET https://httpbin.org/get
Accept: application/json
```
<!-- Plugin description end -->

A minimal HTTP client for IntelliJ IDEA Community Edition that runs `.http` requests directly from the editor.

## Why ReqRun

- Works in IntelliJ IDEA Community Edition without Ultimate APIs
- Keeps requests in plain text files you can version-control
- Fast request execution with clear response viewer and history
- Simple environments and variable resolution with predictable precedence
- cURL import/export for interoperability

## Quick example

```http
GET https://httpbin.org/get
Accept: application/json
```

Place the caret inside the request block and press `Ctrl+Alt+R`.

## Highlights

- Request blocks separated by `###`
- Line comments with `#`
- Run request at caret, selection, file, or folder
- Gutter run icons for request blocks
- Inline toolbar with templates, environment selector, and run-all
- Environment discovery with shared/private merge rules
- File variables and built-in variables
- Static auth configs with helper placeholders
- Unresolved variable warnings before execution
- Response viewer with JSON/XML/HTML/Text view modes
- Response toolbar actions: soft wraps, scroll, copy body
- Header folding and line numbers in the response viewer
- Service tool window history with re-run and clear actions
- Save response to file with `> / >>` and upload with `<`
- Copy as cURL and paste cURL as HTTP

## Documentation

- `docs/index.md` - start here
- `docs/quickstart.md` - first request in 5 minutes
- `docs/request-format.md` - syntax and separators
- `docs/variables.md` - file, environment, and built-in variables
- `docs/environments.md` - env files and discovery
- `docs/auth.md` - static auth configs
- `docs/response-viewer.md` - response viewer reference
- `docs/curl-import-export.md` - cURL workflow

## Installation

### From Marketplace

`Settings | Plugins | Marketplace | Search "ReqRun" | Install`

### From Disk

1) Download the plugin ZIP from Marketplace or GitHub Releases
2) `Settings | Plugins | Install plugin from disk...`

## Project

- `CHANGELOG.md`
- `CONTRIBUTING.md`
- `RELEASING.md`
- Issues: https://github.com/AidarKhusainov/req-run/issues
