# Releasing ReqRun

This project ships via Git tags. A tag triggers a GitHub Release and then an automatic publish to JetBrains Marketplace (after all checks pass).

## Prerequisites
- `CHANGELOG.md` updated for the new version
- CI is green on `main`
- Secrets configured in GitHub:
  - `PUBLISH_TOKEN`
  - `CERTIFICATE_CHAIN`
  - `PRIVATE_KEY`
  - `PRIVATE_KEY_PASSWORD`

## Release Steps
1) Add release notes to `CHANGELOG.md`.
2) Commit and push to `main`.
3) Create and push a version tag:
   ```bash
   git tag 2.1.1
   git push origin 2.1.1
   ```

## What Happens in CI
- `Release` workflow runs on the tag and executes:
  - full verification suite (`build`, `test`, `verify`, `inspect`, `uiTests`)
  - GitHub Release creation (or update) with artifacts from `build/distributions`
  - changelog patch + PR if needed
- `Publish` workflow runs automatically after a successful `Release`:
  - publishes to JetBrains Marketplace

## Notes
- The plugin version is derived from the Git tag (e.g. tag `2.1.1` -> version `2.1.1`).
- Tags must match the SemVer pattern `*.*.*`.
- CI always runs Linux UI tests for release gating. For broader coverage, use the manual `Run UI Tests` workflow to run Windows/macOS UI tests.
- If a tag points to the wrong commit, delete and recreate it:
  ```bash
  git tag -d 2.1.1
  git push origin :refs/tags/2.1.1
  git tag 2.1.1
  git push origin 2.1.1
  ```
