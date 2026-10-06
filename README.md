# PMIS Docket

File manager and document control for the PMIS company server.

| Part | Folder | What it is |
|---|---|---|
| **Docket Server** | `server/` | Spring Boot service on the physical server. It is the only thing that touches the storage disk. It handles sign-in, folders, files, versions, permissions, sharing, the audit log, conversions, signatures, locking and stamps. |
| **Docket Desktop** | `desktop/` | JavaFX app installed on each PC. It talks to the server over HTTP(S). |
| **Installer** | `installer/` | Builds `PMIS-Docket-Setup-x.y.z.exe`, which includes its own Java. |

```
Desktop app (each PC)  ⇄  HTTPS  ⇄  Docket Server (server IP)  →  storage folder on the server's disk
                                                     ↘  LibreOffice (Office files), FFmpeg (video/audio) — optional
```

## What's in this version

**Places**
- **Home** shows recent files and items shared with me.
- **My files** is each person's private folder on the server.
- **Company** holds shared folders with permissions per folder.
- **Shared with me** lists what colleagues shared from their My files.
- **Admin** is for IT administrators only.

**Browsing**
- Folders and files appear together. Double-click a folder to open it.
- Navigation: Back, Forward, Up, a clickable path bar, and Refresh.
- Search inside the current folder, and filter by type: Documents, Images, Videos, Audio, Text & code, Archives, Apps & other.
- Sort by name, date, type or size.
- Views: large icons or details list, plus an optional **preview pane**.

**File operations**
- New folder.
- Upload with the button or by dragging files into the window.
- Download.
- Cut, Copy, Paste and Move.
- Rename.
- Delete with **Undo**. Deleted items go to the Recycle Bin on the server.

**Viewer inside Docket**
- **Pages:** PDF, Word, Excel, PowerPoint, with zoom and page navigation.
- **Pictures**, including WebP.
- **Video and audio player:** MP4, M4A, MP3, WAV.
- **Text notes and code**, read-only.
- **Archives:** lists ZIP, 7z and TAR contents, with **Extract here**.
- **Anything else:** a "no preview" card with **Open with…** and **Download**.

**Check-out and versions**
- Check out a file to edit it in Word, Excel and so on.
- Others see it as being edited and can't change it.
- Check in with a comment to create a new version, or discard the check-out.
- Version history: open any old version, or restore it as a new version.

**Share with a colleague**
- Choose people, then "can view" or "can edit", and when access ends.
- See and stop sharing in Properties › Sharing.

**Document actions**

| Action | What it does |
|---|---|
| **Convert** | Offers only formats that fit the file and that the server can make. The original stays. |
| **Sign** | Drawn or typed visible signature, plus a real digital signature with each user's PMIS certificate. PDFs get a new version. Office files become a signed PDF copy. |
| **Lock** | AES-256 password. PDFs (with print and copy permissions) and DOCX/XLSX/PPTX keep their format. Other types become an encrypted ZIP. Docket never stores the password. |
| **Mark** | Approved, Confidential, Draft, Copy, Paid, Rejected, or your own text. Applies to PDFs and pictures as a new version; Word and PowerPoint get a stamped PDF copy. |

**Properties** has four tabs: General, Versions, Sharing and Activity. Activity is the audit trail for that item.

**Access denied** shows a **Request access** button, which sends a request to IT.

**Accounts**
- Sign-in, change password, and forced password change for new accounts and resets.

**Admin console** (IT administrators)
- **Users:** add users (shows a temporary password), and edit department, storage, role, active status, or reset the password.
- **Folder permissions** per company folder: groups and people get No access, Read only, Read & write or Full control. Folders inherit from the folder above unless they have their own settings.
- **Access requests:** approve or deny.
- **Audit log:** filter, search and export to CSV.
- **Server:** disk, users, check-outs, and whether LibreOffice and FFmpeg are installed.
- **Recycle Bin:** restore items, or empty those older than 30 days.
- **App updates:** upload a new installer, and every PC offers it at sign-in.

**Feel**
- Custom window, animations everywhere, and Explorer keyboard shortcuts: Enter, Del, F2, F5, Alt+←/→/↑, Ctrl+C/X/V, Ctrl+D, Ctrl+E, Ctrl+F, Ctrl+U, Ctrl+Shift+N, Alt+Enter.

---

## 1. Test everything on one PC

**You need:**
- **JDK 21** and **Maven 3.9+** on Windows 10 or 11.
- **LibreOffice** (free, https://www.libreoffice.org), for Word, Excel and PowerPoint previews and conversions, and for signing and stamping Office files. Recommended.
- **FFmpeg** (https://www.gyan.dev/ffmpeg/builds, "essentials"), for video and audio conversion. Optional. Put `ffmpeg.exe` on PATH or set `docket.ffmpeg-path`.

Without LibreOffice or FFmpeg everything else still works, and the related buttons explain what's missing.

> **If you ran the first version before:** stop the server and double-click **`reset-demo-data.cmd`**. The database layout changed, so the old test data must be removed once.

**Steps:**
1. Double-click **`run-server-local.cmd`**. The server starts at http://localhost:8080 and creates demo data on the first start.
2. Double-click **`run-desktop.cmd`**.
3. Sign in. The password for all demo accounts is **`Docket2026!`**

| Username | Who | Good for testing |
|---|---|---|
| `a.karimova` | Aylin, Finance | My files with every file type. Shares the lease with Tural. Has "Q4 board pack" shared with her (can edit). Finance is read & write. Legal and Templates are read only. HR, IT and Management have no access, so try Request access. |
| `t.mammadli` | Tural, Management | Has **Q3 forecast** checked out (Company › Departments › Finance › Budgets). Sees the lease under Shared with me. |
| `l.rahimli` | Leyla, HR | HR and Policies have full control. |
| `s.novruzova` | Sabina, Operations | Already asked IT for access to Management. |
| `r.aliyev` | Rashad, **IT administrator** | Admin console: users, permissions, requests, audit, server, Recycle Bin, updates. |
| `k.huseynov` | Kamran, Legal | Disabled account. |

**Testing two people at once.** Run `run-desktop.cmd` twice and sign in as different users. For example, check out a file as Aylin and look at it as Tural.

---

## 2. Install on the real server

1. **Install software:** Java 21, PostgreSQL 16+, LibreOffice and FFmpeg (optional). Create database `docket` and user `docket`.
2. **Build the server:** `cd server && mvn clean package` gives you `server/target/docket-server.jar`.
3. **Create the HTTPS certificate** for the server's IP:
   ```
   keytool -genkeypair -alias docket -keyalg RSA -keysize 3072 -validity 1825 -storetype PKCS12 ^
     -keystore certs\docket.p12 -dname "CN=PMIS Docket" -ext "SAN=IP:192.168.1.10"
   keytool -exportcert -alias docket -keystore certs\docket.p12 -rfc -file certs\docket.cer
   ```
4. **Set the environment variables:**

   | Variable | Example / meaning |
   |---|---|
   | `DOCKET_DB_URL` | `jdbc:postgresql://localhost:5432/docket` |
   | `DOCKET_DB_USER`, `DOCKET_DB_PASSWORD` | database user |
   | `DOCKET_STORAGE_ROOT` | `D:/DocketData/storage` (where files are kept) |
   | `DOCKET_KEYSTORE_PASSWORD` | password given to keytool |
   | `DOCKET_KEY_PASSWORD` | long random secret that protects users' signing keys |
   | `DOCKET_BOOTSTRAP_ADMIN_LOGIN` / `_PASSWORD` | first IT admin, created once when the database is empty |
   | `DOCKET_SOFFICE`, `DOCKET_FFMPEG` | full paths if not in the usual places |

5. **Start it:** `java -jar docket-server.jar --spring.profiles.active=prod`. Run it as a Windows service with WinSW or NSSM, and open port 8443 for the office network only.
6. **Create the real users.** Sign in as the bootstrap admin, change the password, then add users and folder permissions in **Admin**.

**Backups:** back up the PostgreSQL database **and** the storage folder together.

---

## 3. Build the installer (.exe)

**You need on the build PC:** JDK 21, Maven, and **Inno Setup 6** (https://jrsoftware.org/isdl.php).

**Run:**

```
powershell -ExecutionPolicy Bypass -File installer\build-installer.ps1 -ServerUrl https://192.168.1.10:8443 -ServerCert certs\docket.cer -Version 1.0.0
```

The result is `build\installer\PMIS-Docket-Setup-1.0.0.exe`.

**Silent install for IT:**

```
PMIS-Docket-Setup-1.0.0.exe /VERYSILENT /server=https://192.168.1.10:8443
```

**Updates:**
1. Change `APP_VERSION` in `desktop/.../AppConfig.java`.
2. Build with the same `-Version`.
3. In **Admin › App updates**, upload the `.exe`.

Every PC offers the update at sign-in.

**Code signing:** enable `SignTool` in `installer/PMIS-Docket.iss` once you have a certificate.

**Branding:** see `installer/assets/README.txt` and `desktop/.../fonts/README.txt`.
