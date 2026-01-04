# Authentication

## TL;DR

Define static auth configs in your env file and reference them with `{{$auth.token("id")}}` or `{{$auth.header("id")}}`.

```json
{
  "local": {
    "token": "demo",
    "Security": {
      "Auth": {
        "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" }
      }
    }
  }
}
```

```http
GET https://httpbin.org/get
Authorization: Bearer {{$auth.token("bearer")}}
```

## Supported schemes

- `Bearer`
- `Basic`
- `ApiKey` (also accepts `api-key` and `API_KEY`)

## Config structure

Auth configs live under `Security.Auth` inside an environment in:

- `http-client.env.json`
- `http-client.private.env.json`

Private configs override shared configs by id.

```json
{
  "local": {
    "Security": {
      "Auth": {
        "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" },
        "basic": { "Type": "Static", "Scheme": "Basic", "Username": "{{user}}", "Password": "{{pass}}" },
        "api": { "Type": "Static", "Scheme": "ApiKey", "Token": "{{apiKey}}", "Header": "X-Api-Key" }
      }
    }
  }
}
```

## Token helper

Use `{{$auth.token("id")}}` to insert the auth token:

```http
Authorization: Bearer {{$auth.token("bearer")}}
```

- For `Basic`, if `Token` is set it is used as the raw Basic token.
- Otherwise, `Username` and `Password` are Base64 encoded as `username:password`.

## Header helper

Use `{{$auth.header("id")}}` to insert the full header line:

```http
{{$auth.header("api")}}
```

Default header names:

- `Authorization` for `Bearer` and `Basic`
- `X-API-Key` for `ApiKey`

You can override the header name in the config (placeholders allowed):

```json
{ "Header": "{{headerName}}" }
```

## Variables and precedence

- `Token`, `Username`, `Password`, and `Header` support `{{var}}` placeholders and built-in variables.
- File variables override environment variables; built-ins are resolved only via `{{$...}}`.

## Error reporting

ReqRun reports problems before execution:

- Missing auth config id: `Missing auth config: <id>`
- Incomplete config: `Auth config '<id>' Username/Password is missing.`
- Unresolved variables in auth fields are reported with details.

## Related

- `variables.md`
- `environments.md`
- `request-format.md`
- `examples/auth.http`
