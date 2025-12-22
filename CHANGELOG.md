<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# req-run Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/AidarKhusainov/req-run/compare/2.0.0...HEAD
[2.0.0]: https://github.com/AidarKhusainov/req-run/releases/tag/2.0.0
[1.0.0]: https://github.com/AidarKhusainov/req-run/releases/tag/1.0.0
