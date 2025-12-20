# ReqRun

![Build](https://github.com/AidarKhusainov/req-run/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
ReqRun adds a lightweight HTTP request runner to IntelliJ IDEA Community. Write `METHOD URL` blocks in `.http` files, press `Ctrl+Alt+R`, and see the response with status, headers, and body in the ReqRun tool window.

To keep everything working, do not remove `<!-- ... -->` sections.
<!-- Plugin description end -->

## Features

- Run ad-hoc HTTP requests from `.http` files.
- One shortcut (`Ctrl+Alt+R`) from editor or context menu.
- Response viewer with status, headers, body, and quick compare.
- Service tool window with request history and re-run action.

## Usage

Create a `.http` file and write requests like:

```http
GET https://httpbin.org/get
Accept: application/json

```

Place the caret inside a request block and press `Ctrl+Alt+R`, or right-click and choose **Run HTTP Request**.

More examples live in [examples.http](examples.http).

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "ReqRun"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/AidarKhusainov/req-run/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Releases

Release notes and artifacts are published on GitHub:  
https://github.com/AidarKhusainov/req-run/releases

## License

MIT. See `LICENSE`.

---
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
