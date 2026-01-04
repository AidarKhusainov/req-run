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

## Related

- `variables.md`
- `environments.md`
- `response-viewer.md`
- `examples/basic.http`
