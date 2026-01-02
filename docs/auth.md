# Auth Configs

ReqRun supports static auth configs stored in env files under `Security.Auth`.
They are resolved via `{{$auth.token("id")}}` or `{{$auth.header("id")}}`.

Supported schemes
- `Bearer`
- `Basic`
- `ApiKey` (also accepts `api-key` and `API_KEY`)

Where to define
Auth configs live inside an environment block in `http-client.env.json` or
`http-client.private.env.json`. Private overrides shared by id.

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

Token resolution
- `Token`, `Username`, and `Password` support `{{var}}` placeholders and built-ins.
- Variable precedence: file variables `@name = value` override env variables.

Bearer
```http
Authorization: Bearer {{$auth.token("bearer")}}
```

Basic
- If `Token` is provided, it is used as the raw Basic token.
- Otherwise, `Username` + `Password` are Base64 encoded as `username:password`.

```http
Authorization: Basic {{$auth.token("basic")}}
```

ApiKey
```http
X-API-Key: {{$auth.token("api")}}
```

Header helper
Use `{{$auth.header("id")}}` to insert a full header line.

Defaults:
- `Authorization` for `Bearer` and `Basic`
- `X-API-Key` for `ApiKey`

Custom header name:
```json
{ "Header": "X-Api-Key" }
```

```http
{{$auth.header("api")}}
```

Header name placeholders:
```json
{ "Header": "{{headerName}}" }
```

```http
{{$auth.header("api")}}
```

`http-client.private.env.json`:
```json
{ "local": { "headerName": "X-Api-Key" } }
```

Multiple services
You can define multiple auth configs and reference them per request:
```http
Authorization: Bearer {{$auth.token("serviceA")}}
{{$auth.header("serviceB")}}
```

---

# Real-world examples

## 1) Bearer token from private env

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
      "Auth": {
        "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" }
      }
    }
  }
}
```

`http-client.private.env.json`:
```json
{
  "local": {
    "token": "my-secret-token"
  }
}
```

Request:
```http
GET https://api.example.com/users
Authorization: Bearer {{$auth.token("bearer")}}
```

## 2) Basic auth with username/password

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
      "Auth": {
        "basic": {
          "Type": "Static",
          "Scheme": "Basic",
          "Username": "{{user}}",
          "Password": "{{pass}}"
        }
      }
    }
  }
}
```

`http-client.private.env.json`:
```json
{
  "local": {
    "user": "demo",
    "pass": "demo"
  }
}
```

Request:
```http
GET https://api.example.com/secure
Authorization: Basic {{$auth.token("basic")}}
```

## 3) Basic auth with raw token

Use this when you already have the Base64 token.

`http-client.private.env.json`:
```json
{
  "local": {
    "basicToken": "ZGVtbzpkZW1v"
  }
}
```

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
      "Auth": {
        "basic": { "Type": "Static", "Scheme": "Basic", "Token": "{{basicToken}}" }
      }
    }
  }
}
```

Request:
```http
Authorization: Basic {{$auth.token("basic")}}
```

## 4) API key with custom header name

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
      "Auth": {
        "api": {
          "Type": "Static",
          "Scheme": "ApiKey",
          "Token": "{{apiKey}}",
          "Header": "X-Api-Key"
        }
      }
    }
  }
}
```

`http-client.private.env.json`:
```json
{
  "local": {
    "apiKey": "secret-123"
  }
}
```

Request:
```http
{{$auth.header("api")}}
```

## 5) API key with header placeholder

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
      "Auth": {
        "api": {
          "Type": "Static",
          "Scheme": "ApiKey",
          "Token": "{{apiKey}}",
          "Header": "{{headerName}}"
        }
      }
    }
  }
}
```

`http-client.private.env.json`:
```json
{
  "local": {
    "apiKey": "secret-123",
    "headerName": "X-Api-Key"
  }
}
```

Request:
```http
{{$auth.header("api")}}
```

## 6) Multiple environments

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
      "Auth": {
        "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" }
      }
    }
  },
  "stage": {
    "Security": {
      "Auth": {
        "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" }
      }
    }
  }
}
```

`http-client.private.env.json`:
```json
{
  "local": { "token": "local-token" },
  "stage": { "token": "stage-token" }
}
```

Request:
```http
Authorization: Bearer {{$auth.token("bearer")}}
```

## 7) File variables override env variables

`.http`:
```http
@token = file-token

GET https://api.example.com
Authorization: Bearer {{$auth.token("bearer")}}
```

`http-client.env.json`:
```json
{
  "local": {
    "token": "env-token",
    "Security": {
      "Auth": {
        "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" }
      }
    }
  }
}
```

## 8) Built-ins inside auth fields

`http-client.env.json`:
```json
{
  "local": {
    "Security": {
        "Auth": {
        "api": { "Type": "Static", "Scheme": "ApiKey", "Token": "{{$uuid}}" }
      }
    }
  }
}
```

Request:
```http
{{$auth.header("api")}}
```

Error reporting
- Missing auth config id: "Missing auth config: <id>"
- Incomplete auth config: "Auth config '<id>' Username/Password is missing."
- Unresolved variables inside auth fields are reported with details.
