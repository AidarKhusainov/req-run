<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# req-run Changelog

## [Unreleased]
### Added

### Changed

### Fixed

## [2.2.2] - 2026-03-13
### Added

### Changed
- Updated the Qodana linter image to `2025.2` to match the GitHub Action CLI version used in CI.

### Fixed
- Stopped nightly CI from depending on Qodana Cloud token validity by running the community linter locally in GitHub Actions.
- Replaced deprecated `ReadAction.compute(ThrowableComputable)` usages flagged by JetBrains Marketplace with non-deprecated read actions.

## [2.2.1] - 2026-02-16
### Added

### Changed
- Applied Kotlin formatting updates across Gradle script, main code, tests, and UI tests to satisfy ktlint checks.
- Replaced wildcard imports with explicit imports in core and test modules.
- Refreshed Detekt baseline to reflect the current code structure and rule signatures.

### Fixed
- Resolved ktlint violations in `PasteCurlAction` around multiline expression formatting.

## [2.2.0] - 2026-02-16
### Added
- Dedicated UI test source set and `uiTests` Gradle task based on IntelliJ Remote Robot.
- End-to-end UI test coverage for toolbar actions, response viewer flows, environment/settings behavior, and cURL workflows.
- Reusable UI test fixtures and project test data for environment files and HTTP scenarios.

### Changed
- Expanded ReqRun per-request options support and cURL import/export behavior.
- Increased default request and connect timeouts for HTTP execution.
- Updated environment file loading to prefer unsaved in-memory document content before filesystem fallback.
- Reorganized README overview and removed plugin description markers.

### Fixed
- Stabilized UI test IDE setup with isolated config/system/plugins directories.

## [2.1.9] - 2026-01-06
### Added
- Response viewer settings (View As, line numbers, header folding) and toolbar actions (soft wraps, scroll, copy body).
- File upload via `< path` and response save via `> / >> path`.
- New POST (File) request template in “Add to HTTP Client…”.
- HTTP version support and OkHttp-based transport.
- Setting to shorten request history URLs (hide host).
- Re-run action in the service view toolbar.

### Changed
- Request separators allow inline titles after `###`.
- Documentation refreshed with screenshots and fixed links.

### Fixed
- Action update thread warnings for service view actions.

## [2.1.0] - 2025-12-26
### Added
- Inline editor toolbar for `.http` files with templates, import/export, run-all, and environment selector.
- Environment file helpers: create/open `http-client.env.json` and `http-client.private.env.json`, add variable actions.
- Global/project settings for shared and private environment file paths.
- New plugin icons and refreshed documentation.
- Additional test coverage across actions, core, and services.

### Changed
- Editor popup actions grouped with a separator and shown only for `.http` files.

## [2.0.0] - 2025-12-22
### Added
- Run all requests in the current selection or file via "Run Selected Requests".
- Group run action for selections, files, and folders.
- Convert HTTP requests to cURL and copy to clipboard.
- Paste a cURL command and convert to HTTP.
- Comments and flexible separators in `.http` request blocks.
- Clear request history from the service view.

### Changed
- IntelliJ IDEA compatibility updated for 2024.x (since build 241), JVM toolchain set to 17.
- Request execution is cancellable in progress dialogs for single and grouped runs.
- Proxy settings handling updated for broader IDE compatibility.

### Fixed
- SSL context now uses the project SDK truststore when available, with IDE fallback.
- Service view re-run and selection behavior stabilized (no toolbar churn).
- History storage capped with eviction to avoid unbounded growth.
- Line marker calculation avoids duplicate markers and extra passes.

## [1.0.0] - 2025-12-20
### Added
- Lightweight HTTP request runner for `.http` files.
- Editor action and context menu to run requests.
- Response viewer with headers, body, and diff.
- Service tool window with request history and re-run action.

[Unreleased]: https://github.com/AidarKhusainov/req-run/compare/2.2.2...HEAD
[2.2.2]: https://github.com/AidarKhusainov/req-run/releases/tag/2.2.2
[2.2.1]: https://github.com/AidarKhusainov/req-run/releases/tag/2.2.1
[2.2.0]: https://github.com/AidarKhusainov/req-run/releases/tag/2.2.0
[2.1.9]: https://github.com/AidarKhusainov/req-run/releases/tag/2.1.9
[2.1.1]: https://github.com/AidarKhusainov/req-run/releases/tag/2.1.1
[2.1.0]: https://github.com/AidarKhusainov/req-run/releases/tag/2.1.0
[2.0.0]: https://github.com/AidarKhusainov/req-run/releases/tag/2.0.0
[1.0.0]: https://github.com/AidarKhusainov/req-run/releases/tag/1.0.0
