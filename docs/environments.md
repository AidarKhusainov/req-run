# Environments

ReqRun loads environment variables from JSON files and lets you select the active environment from the editor toolbar.

## Files and merge rules

- Shared: `http-client.env.json`
- Private: `http-client.private.env.json`
- Private variables override shared variables on key conflicts.

## Discovery rules

If custom paths are not set, ReqRun searches upward from the current request file to the project root and uses the first directory that contains either env file. Both files are expected in that directory.

You can also configure explicit paths in `Settings | Tools | ReqRun`:

- Scope: Global or Project
- Shared env file path
- Private env file path

## Example env file

```json
{
  "local": {
    "baseUrl": "https://httpbin.org",
    "token": "demo"
  },
  "stage": {
    "baseUrl": "https://stage.example.com",
    "token": "stage-token"
  }
}
```

## Using environments

```http
GET {{baseUrl}}/get
Authorization: Bearer {{token}}
```

Select the environment from the toolbar (or choose **No Environment** to disable env vars).

## Notes

- JSON values are stringified when loaded (numbers and booleans become strings).
- The env selector can create missing env files and open them in the editor.
- When a private env file is created, ReqRun suggests adding it to `.gitignore`.
- History display can shorten URLs (hide host) from `Settings | Tools | ReqRun`.
