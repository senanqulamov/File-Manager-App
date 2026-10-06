# Prepare a server deployment

This is a deployment guide for a **controlled evaluation**. Complete the [readiness checks](STATUS.md) before placing real company documents into service. The local demo profile must not be used for a company deployment.

## 1. Prepare the environment

- Server: JDK 21, a PostgreSQL instance, persistent storage, and a restricted service account.
- Build machine: JDK 21 and Maven 3.9+. Windows installer builds also need Inno Setup 6.
- Clients: Windows PCs that can reach the server. Validate the exact Windows version and architecture you intend to support; the installer targets x64-compatible systems.
- Network: a stable server hostname and HTTPS certificate trusted by the client PCs.
- Optional: LibreOffice for Office preview/conversion and FFmpeg for media conversion.

PostgreSQL, storage sizing, and operating-system compatibility need validation in your target environment. There is no published concurrency or maximum-document benchmark.

## 2. Create the database and storage

Using a PostgreSQL administrator account, create a dedicated login and database. These PostgreSQL utilities prompt for credentials rather than embedding a password in the command:

```text
createuser --pwprompt docket
createdb --owner=docket docket
```

Create a persistent directory such as `D:/DocketData/storage`. Give the Docket service identity access; do not expose it as a user-accessible network share. Keep application files and document storage separate.

The application currently uses Hibernate `ddl-auto: update`. A reviewed migration and rollback strategy is an open release requirement; back up before upgrading.

## 3. Build the server

From the repository root:

```powershell
mvn --batch-mode --no-transfer-progress -pl server -am clean verify
```

The server artifact is `server/target/docket-server.jar`. Copy it into the server's application directory. Keep build output and runtime state outside the Git repository.

## 4. Configure HTTPS and secrets

Provide a PKCS#12 server keystore whose certificate matches the hostname used by clients. The default production location is `./certs/docket.p12`, relative to the process working directory. Use a company/public CA certificate where appropriate, or explicitly distribute trust for a controlled evaluation.

| Variable | Configuration |
| --- | --- |
| `DOCKET_DB_URL` | JDBC URL, for example `jdbc:postgresql://localhost:5432/docket` |
| `DOCKET_DB_USER` | Dedicated database user |
| `DOCKET_DB_PASSWORD` | Database password; required in the production profile |
| `DOCKET_STORAGE_ROOT` | Absolute path to persistent storage |
| `DOCKET_PORT` | HTTPS port; production default `8443` |
| `DOCKET_KEYSTORE` | Keystore location, for example `file:./certs/docket.p12` |
| `DOCKET_KEYSTORE_PASSWORD` | Password for the HTTPS keystore |
| `DOCKET_KEY_PASSWORD` | Strong secret protecting per-user signing keys; preserve it with backups |
| `DOCKET_BOOTSTRAP_ADMIN_LOGIN` | Initial administrator login on an empty database |
| `DOCKET_BOOTSTRAP_ADMIN_PASSWORD` | Initial administrator password |
| `DOCKET_SOFFICE` | Optional full path to LibreOffice's executable |
| `DOCKET_FFMPEG` | Optional full path to FFmpeg's executable |

Set these in the service environment or your deployment's secret manager. A `.env` file is **not automatically read** by the existing launcher. Do not commit passwords, signing keys, keystores, or certificates with private keys.

Keep `DOCKET_SSL_ENABLED` enabled (the production default). Explicitly select the production profile when starting the server:

```powershell
java -jar docket-server.jar --spring.profiles.active=prod
```

For persistent operation, configure a Windows service using your organisation's service-management tooling, with a fixed working directory and the same environment. Running this command in a terminal alone does not install a service.

## 5. Check first startup

1. Confirm the logs show the `prod` profile and a successful PostgreSQL connection.
2. Confirm the service can write to its intended storage directory.
3. Check `https://your-server-hostname:8443/api/health` using the real certificate-matching hostname.
4. Confirm clients trust the certificate; do not disable certificate verification to make connection errors disappear.
5. Sign in using the bootstrap administrator. Change its initial password and remove the bootstrap password from the persistent service configuration.
6. Confirm no seeded demo users are present. Create a normal pilot account and validate its access separately from the administrator.

## 6. Build and install the Windows client

On the Windows build machine, from the repository root, supply the actual HTTPS server address:

```powershell
powershell -ExecutionPolicy Bypass -File installer\build-installer.ps1 -ServerUrl https://docket.internal:8443 -Version 1.0.0
```

`docket.internal` is an example hostname, not a provided service. Replace it with your own. If using a privately trusted server certificate, pass `-ServerCert certs\docket.cer` after verifying its source and fingerprint.

Output: `build/installer/PMIS-Docket-Setup-1.0.0.exe`. The script rebuilds the `build/` directory. Test the installer on a clean Windows machine without a separately installed JDK. Publisher signing is not configured in the current source.

The installer writes the server address to `%LOCALAPPDATA%\PMIS Docket\docket.properties` or `%ProgramData%\PMIS Docket\docket.properties`; user settings take precedence. Existing clients may retain a previous address.

## 7. Establish backup and recovery

Back up **PostgreSQL and the whole storage root together**, including signing keys and update files. Preserve the signing-key password and HTTPS configuration through a separate protected process.

For a simple consistent maintenance backup, stop the Docket service to prevent new writes, take a PostgreSQL dump using `pg_dump`, copy the storage directory, and restart the service. Store the matching set with its application version. The recycle bin is not a backup.

Prove recovery on a separate instance: restore the database and matching storage, reapply the protected configuration, and verify sign-in, current files, older versions, and signatures. Do not declare a backup usable until that restore succeeds.

## 8. Accept the deployment

Run the [release acceptance checklist](STATUS.md), record results and limitations, and agree operational ownership and support terms. An installer build or successful health request alone is not production acceptance.

[Quick start](QUICKSTART.md) · [Release process](RELEASING.md) · [Support](../SUPPORT.md)
