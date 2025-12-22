# ReqRun

![Build](https://github.com/AidarKhusainov/req-run/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29471-reqrun.svg)](https://plugins.jetbrains.com/plugin/29471-reqrun)

<!-- Plugin description -->
ReqRun is a minimal HTTP client for IntelliJ IDEA Community that lives in `.http` files.
Fast to run, easy to read.

<h2>Features</h2>
<ul>
  <li>Run request blocks with gutter markers or <code>Ctrl+Alt+R</code>.</li>
  <li>Run all requests in the current selection or file.</li>
  <li>Full response viewer: status, headers, body, and quick compare.</li>
  <li>Service tool window with history and one-click re-run.</li>
  <li>Convert to cURL or paste cURL as a request.</li>
  <li>Line comments with <code>#</code>.</li>
</ul>

<h2>Request format</h2>
<pre>
POST https://httpbin.org/post
Content-Type: application/json
X-Trace: demo
<p>&nbsp;</p>
{"ok": true}
</pre>
Place the caret inside a request block and press <code>Ctrl+Alt+R</code>.

<b>Notes</b>
<ul>
  <li><code>#</code> starts a line comment. Comments are ignored by parser and runner.</li>
  <li>A blank line or a comment line separates headers and body.</li>
  <li>Use <code>###</code> on its own line to separate request blocks.</li>
  <li>More examples: <a href="https://github.com/AidarKhusainov/req-run/blob/main/examples.http">examples.http</a></li>
</ul>

<h2>Feedback</h2>
<ul>
  <li>Suggestions and bug reports: <a href="https://github.com/AidarKhusainov/req-run">github.com/AidarKhusainov/req-run</a></li>
</ul>
<!-- Plugin description end -->

## Actions

- **Run HTTP Request**: run the request block at the caret with `Ctrl+Alt+R`.
- **Run Selected Requests**: run all requests from selection or file.
- **Convert to cURL and Copy**: copy the current request block as a cURL command.
- **Paste cURL as HTTP**: insert a cURL command as a `.http` request.

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
