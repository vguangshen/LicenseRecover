# Phase 5 — Versioning, documentation and stable releases

## Scope

Phase 5 turns the verified rolling build into a versioned product release process without changing authorization logic, patch algorithms or runtime compatibility behavior.

## Version source of truth

`VERSION.txt` contains the current stable semantic version without the `v` prefix, for example:

`1.0.0`

A matching release-note file must exist at:

`release-notes/v1.0.0.md`

The verification pipeline rejects malformed versions or a missing matching release-note file.

## Release channels

Two release channels are maintained:

1. `rolling-latest` — prerelease updated after every successfully verified `main` push.
2. `vX.Y.Z` — immutable stable baseline created only when the version in `VERSION.txt` has no existing GitHub Release.

Creating a stable release is therefore an explicit version-bump operation: update `VERSION.txt`, add the matching release notes, update the changelog, pass CI and merge to `main`.

## Stable release behavior

After `main` verification succeeds, CI reads `VERSION.txt` and checks for `vX.Y.Z`.

- If the stable release already exists, CI leaves it untouched.
- If it does not exist, CI creates the tag/release at the verified `main` commit and uploads the verified ZIP plus checksum.
- A tag that exists without a matching release is treated as an error rather than silently overwritten.

This prevents later `main` commits from mutating an already published stable version.

## Documentation cleanup

- `README.md` becomes the GitHub landing document.
- `README.txt` remains a plain-text user guide inside the Windows-oriented distribution.
- `CHANGELOG.md` records versioned changes.
- User release ZIPs contain runtime files plus user-facing version/docs only; engineering phase documents remain in the repository.

## Compatibility boundary

Phase 5 does not remove legacy launchers, alter Java class packages, modify protocol algorithms or replace the existing .NET helper binaries.
