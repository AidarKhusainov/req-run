# cURL import/export

ReqRun can copy a request as cURL or convert a cURL command into `.http`.

## Copy as cURL

1) Place the caret inside a request block
2) Use **Export | Copy as cURL** or **Convert to cURL and Copy**

Notes:

- Variables and auth helpers are resolved before conversion.
- If unresolved placeholders remain, the copy is aborted and a warning is shown.
- ReqRun-only directives (`# @reqrun.*`) and response save directives (`> / >>`) are not exported.
- File body parts (`< path`) are exported as `--data-binary @path`.

## Paste cURL as HTTP

1) Copy a cURL command to the clipboard (or select it in the editor)
2) Use **Import | Paste cURL** or **Paste cURL as HTTP**

If parsing succeeds with warnings, the request is still inserted and a warning notification shows the first issue (with line/column).

### Supported cURL flags

HTTP version:

- `--http1.1`
- `--http2`
- `--http2-prior-knowledge`

These map to `HTTP/1.1` or `HTTP/2` in the request line.

Method and URL:

- `-X`, `--request`, `--request=` - set request method.
- `-I`, `--head` - `HEAD`.
- `--url`, `--url=` - explicit URL.

Headers:

- `-H`, `--header`, `--header=` - add header.
- `-A`, `--user-agent` - `User-Agent`.
- `-e`, `--referer` - `Referer`.
- `-b`, `--cookie` - `Cookie`.
- `-u`, `--user` - basic auth header.

Body:

- `-d`, `--data`, `--data-raw`, `--data-binary`, `--data-urlencode`, `--data-ascii`
  - Multiple data flags are joined with newlines.
  - Values starting with `@` become `< path` body directives.
- `-G`, `--get` - merge data into the URL query string (file uploads are ignored with a warning).
- `-F`, `--form` - builds a `multipart/form-data` body, supports `;type=` and `;filename=`.

Output:

- `-o`, `--output`, `--output=` - maps to `> path`.
- `-O`, `--remote-name` - maps to `> <filename-from-url>` (warns if the name cannot be inferred).

Request options (mapped to ReqRun directives):

- `-x`, `--proxy`, `--proxy=` -> `# @reqrun.proxy ...`
- `--proxy-user`, `--proxy-user=` -> `# @reqrun.proxy-user ...`
- `--connect-timeout`, `--connect-timeout=` -> `# @reqrun.connect-timeout ...`
- `-m`, `--max-time`, `--max-time=` -> `# @reqrun.max-time ...`
- `--retry`, `--retry=` -> `# @reqrun.retry ...`
- `--retry-delay`, `--retry-delay=` -> `# @reqrun.retry-delay ...`
- `--retry-max-time`, `--retry-max-time=` -> `# @reqrun.retry-max-time ...`
- `-c`, `--cookie-jar`, `--cookie-jar=` -> `# @reqrun.cookie-jar ...`
- `--cacert`, `--cacert=` -> `# @reqrun.cacert ...`
- `--cert`, `--cert=` -> `# @reqrun.cert ...` (supports `:password` suffix)
- `--key`, `--key=` -> `# @reqrun.key ...` (supports `:password` suffix)
- `--unix-socket`, `--unix-socket=` -> `# @reqrun.unix-socket ...` (parsed, but execution is not supported yet)

cURL config files:

- `-K`, `--config`, `--config=` loads a cURL config file.
- The file is resolved relative to the `.http` file when using **Paste cURL as HTTP**.
- Supported config line formats:
  - lines starting with `-` are treated as flags
  - `key=value` is converted to `--key value`
  - `#` comments are ignored

Notes:

- Flags not listed above are ignored.
- `-L/--location` and `--compressed` are accepted but do not change ReqRun behavior.

## Example

```bash
curl --http2 -X POST https://httpbin.org/post -H 'Content-Type: application/json' --data '{"ok":true}'
```

After paste, ReqRun produces:

```http
POST https://httpbin.org/post HTTP/2
Content-Type: application/json

{"ok":true}
```
