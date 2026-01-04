# Releasing ReqRun

ReqRun ships via Git tags. A tag triggers a GitHub Release and then an automatic publish to JetBrains Marketplace after all checks pass.

## Source of release notes

`CHANGELOG.md` is the source of truth for release notes. Update it before tagging.

## Prerequisites

- `CHANGELOG.md` updated for the new version
- CI is green on `main`
- Required GitHub secrets are configured:
  - `PUBLISH_TOKEN`
  - `CERTIFICATE_CHAIN`
  - `PRIVATE_KEY`
  - `PRIVATE_KEY_PASSWORD`

## Release steps

1) Update `CHANGELOG.md` with release notes.
2) Commit and push to `main`.
3) Create and push a version tag:

```bash
git tag v2.1.1
git push origin v2.1.1
```

## What happens in CI

- The Release workflow runs on the tag and executes:
  - full verification suite (`build`, `test`, `verify`, `inspect`, `uiTests`)
  - GitHub Release creation with artifacts from `build/distributions`
- The Publish workflow runs after a successful Release:
  - publishes to JetBrains Marketplace

## Notes

- Plugin version is derived from the Git tag (e.g., `v2.1.1` -> `2.1.1`).
- Tags must match the SemVer pattern `vX.Y.Z`.
- If a tag points to the wrong commit, delete and recreate it:

```bash
git tag -d v2.1.1
git push origin :refs/tags/v2.1.1
git tag v2.1.1
git push origin v2.1.1
```
