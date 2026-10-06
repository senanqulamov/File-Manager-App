@echo off
rem Deletes the local test database and files so the server creates fresh demo data on its next start.
rem Stop the server first (Ctrl+C in its window).
cd /d "%~dp0server"
if exist docket-data (
  rmdir /s /q docket-data
  echo Local demo data deleted. Start run-server-local.cmd again.
) else (
  echo There is no local demo data to delete.
)
pause
