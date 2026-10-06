# Prepare a release

The `1.0.0` strings in the project identify a build version; they do not prove that a validated commercial release exists. Follow this process for an authorised release candidate.

## Validate and build

1. Complete the applicable [readiness checks](STATUS.md) and record the tested environment.
2. Keep the root/server/desktop POM versions, `AppConfig.APP_VERSION`, and installer version aligned. The in-app updater compares numeric version components; do not use a prerelease suffix to distinguish deployments. Use GitHub's prerelease designation and keep evaluation distribution separate.
3. Run `python scripts/check_repository.py` and `mvn --batch-mode --no-transfer-progress clean verify` from a clean checkout with JDK 21.
4. Build the Windows installer using [the installation guide](INSTALL.md). Test actual launch on a machine without a separately installed JDK; package creation alone does not verify launch.
5. Validate the installer signature once publisher signing is configured. Record its actual status; never describe an unsigned artifact as signed.

## Assemble the distribution

Include the server JAR, the tested client installer, applicable licence terms, third-party notices, installation instructions, release notes, and known issues. Do not include runtime databases, document blobs, signing keys, `.env` files, or private certificates.

Generate SHA-256 checksums for the exact distributed artifacts. In PowerShell:

```powershell
Get-FileHash server\target\docket-server.jar -Algorithm SHA256
Get-FileHash build\installer\PMIS-Docket-Setup-1.0.0.exe -Algorithm SHA256
```

Adjust the installer filename to the release version. Publish the hashes with the artifacts. Checksums identify file content; they are not publisher authentication.

## Release notes must state

- Version and source commit.
- Tested operating systems, database, and optional document-tool versions.
- Changes users will notice and fixes included.
- Installation and upgrade steps, backup requirements, and schema changes.
- Known issues, unsupported workflows, and signing status.
- Artifact filenames and SHA-256 checksums.

Mark evaluation builds as prereleases. Do not publish customer-specific installers, internal hostnames, or deployment credentials into a public release.

## Deliver an update

The admin console accepts an installer, numeric version, and release notes; clients check for a newer version at sign-in. Validate the update path on an evaluation server before distributing it to users. The current upload flow is not a substitute for installer authenticity verification.

Back up the matching database/storage set before a server upgrade. Restoring only an old JAR may be insufficient after a schema change; recovery may require the corresponding database and storage backup.

[Installation](INSTALL.md) · [Support](../SUPPORT.md)
