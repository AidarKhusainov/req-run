# cURL import/export

ReqRun can copy a request as cURL or convert a cURL command into `.http`.

## Copy as cURL

1) Place the caret inside a request block
2) Use **Export | Copy as cURL** or **Convert to cURL and Copy**

Notes:

- Variables and auth helpers are resolved before conversion.
- If unresolved placeholders remain, the copy is aborted and a warning is shown.

## Paste cURL as HTTP

1) Copy a cURL command to the clipboard (or select it in the editor)
2) Use **Import | Paste cURL** or **Paste cURL as HTTP**

Supported cURL flags:

- `--http1.1`
- `--http2`
- `--http2-prior-knowledge`

These map to `HTTP/1.1` or `HTTP/2` in the request line.

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
