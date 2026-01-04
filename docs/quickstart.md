# Quickstart

Run your first request in about 5 minutes.

## 1) Install the plugin

- Open `Settings | Plugins | Marketplace`
- Search for `ReqRun`
- Install and restart if prompted

## 2) Create a `.http` file

Create `requests.http` and paste this request:

```http
# First request
GET https://httpbin.org/get
Accept: application/json
```

## 3) Run the request

- Place the caret inside the request block
- Click the gutter run icon, or press `Ctrl+Alt+R`

## 4) View the response

Results appear in the **service tool window** under ReqRun. Select the latest entry to open the **response viewer**.

## 5) Next steps

- Try more requests in `examples/basic.http`
- Learn the syntax in `request-format.md`
- Add variables in `variables.md`
- Configure environments in `environments.md`
- Set up static auth in `auth.md`

## Related

- `request-format.md`
- `variables.md`
- `environments.md`
- `auth.md`
- `examples/basic.http`
