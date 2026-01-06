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
- If you still see certificate errors, verify your JVM truststore or proxy settings.

## cURL parse errors

- `Paste cURL as HTTP` expects a valid cURL command.
- If parsing fails, try copying a full cURL command including the URL.

## Logs

- ReqRun writes to the IDE log (`idea.log`).
- Open it via `Help | Show Log in Explorer/Finder`.
