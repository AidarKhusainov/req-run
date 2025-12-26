<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# req-run Changelog

## [Unreleased]

## [2.1.7] - 2025-12-26

### Added

- RELEASING.md with a tag-based release flow and CI expectations.
- UI tests as a required CI gate for releases.

### Changed

- CI workflows consolidated via reusable setup action and wrapper validation.
- Release process now creates GitHub releases on tags and publishes artifacts automatically.
- Marketplace publishing runs after a successful GitHub release.
- Plugin version is derived from the Git tag in CI.
- Env file picker updated to non-deprecated IntelliJ APIs.

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

[Unreleased]: https://github.com/AidarKhusainov/req-run/compare/v2.1.7...HEAD
[2.1.7]: https://github.com/AidarKhusainov/req-run/compare/v2.1.0...v2.1.7
[2.1.1]: https://github.com/AidarKhusainov/req-run/releases/tag/2.1.1
[2.1.0]: https://github.com/AidarKhusainov/req-run/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/AidarKhusainov/req-run/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/AidarKhusainov/req-run/commits/v1.0.0
