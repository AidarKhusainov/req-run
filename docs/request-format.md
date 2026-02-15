# Request format

ReqRun reads plain `.http` files with one or more request blocks.

## Basic shape

```http
METHOD URL [HTTP/1.1|HTTP/2]
Header-Name: value

body
```

- The request line is the first non-empty, non-comment line that matches `METHOD URL`.
- `HTTP/1.1` or `HTTP/2` in the request line sets the HTTP version; otherwise HTTP/1.1 is used.

## Multiple requests

Use `###` on its own line (or with a title) to split request blocks:

```http
### Get user
GET https://httpbin.org/get

### Create user
POST https://httpbin.org/post
Content-Type: application/json

{"name": "Ada"}
```

## Comments

- Line comments start with `#` and are ignored.
- Comment lines are ignored even inside request bodies.

## ReqRun request options

ReqRun supports per-request options via comment directives:

```http
# @reqrun.<option> <value>
# @reqrun.<option>=<value>
```

- Directives can appear before the request line or in comment lines before the body.
- When running a single request at the caret, ReqRun extracts the block starting from the request line.
  - Directives placed above the request line are ignored unless you select the whole block or run all requests.
  - To avoid this, place `# @reqrun.*` lines after the request line.
- Comment lines inside the body are ignored (so options placed there will not apply).
- Values can be quoted with single or double quotes.
- Placeholders and built-ins are resolved before parsing, so you can use `{{var}}` in option values.
- File paths are resolved relative to the `.http` file directory unless an absolute path is used.

### Supported options

- `proxy` - proxy URL or `host:port`. If the scheme is omitted, `http://` is assumed. `socks`/`socks5` use SOCKS, everything else is HTTP.
- `proxy-user` - proxy credentials in `user:password` form (password optional). Only used when `proxy` is set.
- `connect-timeout` - connect timeout in seconds (floats allowed, for example `0.5`).
- `max-time` - total request time budget in seconds, shared across retries.
- `retry` - number of retries for execution errors (IO/timeout). HTTP status codes are not retried.
- `retry-delay` - delay between retries in seconds (floats allowed).
- `retry-max-time` - time budget for retries in seconds; retries stop when this budget is exceeded.
- `cookie-jar` - path to a cookie jar file. Cookies are saved in Netscape format and reused for requests that use the same path.
- `cacert` - path to a CA bundle (PEM, one or more X.509 certificates) used for TLS validation.
- `cert` - client certificate path. `:password` suffix is supported. If the file is `.p12`/`.pfx`, a separate `key` is not required.
- `key` - private key path for `cert`. `:password` suffix is supported. Only unencrypted PKCS#8 PEM keys are supported.
- `unix-socket` - Unix socket path (parsed and preserved, but not supported for execution yet).

Example:

```http
GET https://httpbin.org/get
# @reqrun.proxy http://proxy.local:3128
# @reqrun.proxy-user user:pass
# @reqrun.connect-timeout 2.5
# @reqrun.max-time 30
# @reqrun.retry 2
# @reqrun.retry-delay 0.5
# @reqrun.retry-max-time 10
# @reqrun.cookie-jar ./cookies.txt
# @reqrun.cacert ./ca.pem
# @reqrun.cert ./client.p12:secret
# @reqrun.key ./client.key
```

## Headers and body

- Headers are `Name: Value` pairs.
- A blank line starts the body.
- A comment line can also separate headers from body.
- Malformed header lines before the body are ignored until a blank line starts the body.

## File upload in body

Prefix a line with `<` to insert file contents into the body:

```http
POST https://httpbin.org/post
Content-Type: multipart/form-data; boundary=WebAppBoundary

--WebAppBoundary
Content-Disposition: form-data; name="file"; filename="note.txt"

< ./note.txt
--WebAppBoundary--
```

- Paths can be quoted with single or double quotes.
- Relative paths are resolved against the request file directory.

## Save response body to file

Use `> path` to save and `>> path` to append:

```http
GET https://httpbin.org/image/png
Accept: image/png
> ./downloads/image.png
```

- The save directive can appear after headers or after the body.
- Once `> / >>` is encountered, the rest of the block is not treated as body.
