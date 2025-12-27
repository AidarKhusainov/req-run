# Glossary

- Request block: a section of a .http file separated by `###`.
- Request line: HTTP method and URL line at the start of a block.
- Header line: `Name: Value` within a block before the body.
- Body: payload after a blank line in a block.
- File variable: `@name = value` defined inside a .http file.
- Placeholder: `{{var}}` interpolation in URL/headers/body.
- Built-in variable: `{{$timestamp}}`, `{{$uuid}}`, `{{$randomInt}}`.
- Environment: named set of variables in `http-client.env.json`.
- Private environment: overrides in `http-client.private.env.json`.
- Runner: executes an HTTP request and streams response.
- Response viewer: tool window showing response and history.
- Gutter action: run icon next to a request block.
- Toolbar action: “Run with environment” selector and run button.
