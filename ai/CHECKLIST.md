# Checklist

PR checklist
- No EDT blocking; background work is cancelable.
- No Ultimate-only APIs used.
- Parser changes have tests.
- Env merge and variable precedence unchanged unless documented.
- UI actions still work with multiple request blocks.
- Logging is quiet and structured.

Definition of done
- Feature works on IC 2024.3+.
- Unit tests added/updated and passing.
- Error messages are user-friendly; stack traces only in logs.
- Performance is unchanged or improved.

Edge cases to validate
- Empty file and file with only comments.
- Multiple `###` in sequence (empty blocks).
- `#` comment lines are ignored even inside request bodies.
- Missing env file(s); private env only.
- File variable shadows env variable.
- Built-in variable used in URL, headers, and body.
- Large response payloads and history updates.
