# Run a local evaluation

Use an isolated Windows PC with sample documents. The local profile enables demo accounts, HTTP, and the H2 database console. It is not a production configuration. Evaluation permission is covered in [commercial enquiries](COMMERCIAL.md).

## Prerequisites

- JDK 21, with `java` and `javac` available in the terminal.
- Maven 3.9 or later, available as `mvn`.
- Git to obtain the source, or an authorised source archive.
- Network access for Maven's initial dependency download.
- Optional LibreOffice and FFmpeg on the server machine for the workflows that need them.

Check the toolchain in PowerShell:

```powershell
java -version
javac -version
mvn -version
```

## Start both components

From the repository directory, run `run-server-local.cmd`. Keep its window open. After startup, the health endpoint is `http://localhost:8080/api/health`.

Open another window and run `run-desktop.cmd`. Connect the desktop to `http://localhost:8080`.

| Account | Use |
| --- | --- |
| `a.karimova` | Finance user's personal files and document actions |
| `t.mammadli` | Management user's view and sample sharing |
| `r.aliyev` | IT administrator console |

The seeded demo password is `Docket2026!`. These are public sample credentials, never production credentials.

## Try a complete workflow

1. Sign in as `a.karimova` and open My files.
2. Upload a disposable sample document and preview it.
3. Check it out, edit and save it in a locally installed application, then check it in with a comment.
4. Open its version history and inspect the previous version.
5. Share a sample item with `t.mammadli`; sign in as that user to check visibility.
6. Sign in as `r.aliyev` to inspect users, access rules, and recorded activity.

This walkthrough demonstrates the workflow; it does not establish security or production readiness.

## Stop and reset

Close the desktop and stop the server with Ctrl+C. The launcher runs from `server/`, so its local database and storage are in `server/docket-data/`.

`reset-demo-data.cmd` permanently deletes that local demo directory. Run it only after stopping the server and only if its documents are disposable. The next local startup seeds fresh sample data. It does not reset production PostgreSQL or an external storage path.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Unsupported release / class version | `mvn -version` must show JDK 21; the IDE and terminal may use different JDKs. |
| Desktop cannot connect | Keep the server running; check `/api/health`, the server address, and firewall rules. |
| Port 8080 already used | Stop the other service or configure a different port and match the desktop URL. |
| Office preview unavailable | Install LibreOffice on the server; configure `docket.soffice-path` if discovery fails (`DOCKET_SOFFICE` in the production profile). |
| Media conversion unavailable | Install FFmpeg on the server; configure `docket.ffmpeg-path` if needed (`DOCKET_FFMPEG` in the production profile). |
| Document does not open for editing | Install an application associated with that file format. |
| Old local schema prevents startup | Preserve any needed files first; reset only disposable demo data. |

For a source build from the repository root:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

[Server installation](INSTALL.md) · [Support](../SUPPORT.md) · [Back to Docket](../README.md)
