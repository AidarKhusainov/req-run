# Troubleshooting

## 401 / 403 responses

- Verify your auth header or `Security.Auth` config.
- Ensure the selected environment contains required tokens.
- If you use `{{$auth.token(...)}}` or `{{$auth.header(...)}}`, confirm the id exists in the active environment.

## Invalid JSON in env files

- ReqRun warns if `http-client.env.json` or `http-client.private.env.json` contains invalid JSON.
- Fix the JSON syntax and rerun the request.

## Unresolved variables

- ReqRun blocks execution if placeholders remain unresolved.
- Check that file variables (`@name = value`) and environment variables exist and the correct environment is selected.

## Env file not found

- Use the toolbar environment selector to open or create env files.
- If you configured custom paths, verify them in `Settings | Tools | ReqRun`.
- Use **No Environment** if you want to run without env vars.

## SSL / certificate issues

- ReqRun uses the project SDK truststore when available and falls back to the IDE SSL context.
- If you set `# @reqrun.cacert`, that CA bundle is used for validation instead of the SDK truststore.
- Client certificates are supported via `# @reqrun.cert` and `# @reqrun.key`.
  - `.p12` / `.pfx` can be used as `cert` without a separate `key`.
  - Only unencrypted PKCS#8 PEM keys are supported for `key`.
- Common errors:
  - `Certificate file not found: <path>`
  - `Private key file not found: <path>`
  - `No certificates found in <path>`
  - `Private key is required for client certificate.`
  - `Encrypted private keys are not supported.`
  - `Unsupported private key format.`

## Unix socket

- `# @reqrun.unix-socket` and `--unix-socket` (from cURL import) are parsed but not supported for execution yet.
- The request fails with `Unix socket is not supported yet.`

## Proxy / cookie jar behavior

- `# @reqrun.proxy` overrides IDE proxy settings for that request.
- If the proxy URL is malformed, ReqRun reports `Invalid proxy URL: <value>`.
- `# @reqrun.cookie-jar` writes cookies in Netscape format and reuses them for requests with the same path.

## cURL parse errors

- `Paste cURL as HTTP` expects a valid cURL command.
- If parsing fails, try copying a full cURL command including the URL.
- If parsing succeeds with warnings, ReqRun still inserts the request and shows a warning with line/column.
- Typical warning cases: invalid header format, `-O` filename cannot be inferred, missing cURL config file, or unsupported `--unix-socket`.

## Logs

- ReqRun writes to the IDE log (`idea.log`).
- Open it via `Help | Show Log in Explorer/Finder`.
