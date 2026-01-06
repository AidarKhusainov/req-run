# Variables

ReqRun supports file variables, environment variables, and built-in variables.

## File variables

Define file variables at the top of a `.http` file:

```http
@baseUrl = https://httpbin.org
@token = demo
```

Use them anywhere as placeholders:

```http
GET {{baseUrl}}/get
Authorization: Bearer {{token}}
```

- Variable names allow letters, numbers, `_`, `.`, and `-`.
- File variables are collected from the entire file; comment lines and `###` separators are ignored.

## Placeholders

- Syntax: `{{var}}`
- Whitespace inside braces is allowed: `{{  var  }}`
- Placeholders work in the URL, headers, and body.

## Built-in variables

Use built-ins with the `$` prefix:

- `{{$timestamp}}` - Unix timestamp (seconds)
- `{{$uuid}}` - random UUID
- `{{$randomInt}}` - random integer in range 0..1000

## Precedence

- File variables override environment variables.
- Built-in variables are only resolved via `{{$...}}` and are not shadowed by file or environment variables.

## Unresolved variables

- Before execution, ReqRun checks for unresolved placeholders.
- If unresolved placeholders remain, the request is not executed and a warning is shown.
- Group runs skip requests with unresolved placeholders and report a summary.
