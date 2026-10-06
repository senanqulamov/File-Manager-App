@echo off
title PMIS Docket - desktop app
cd /d "%~dp0desktop"
echo Building and starting PMIS Docket...
echo (The first run downloads JavaFX and other libraries, so it can take a few minutes.)
echo.
call mvn -e javafx:run
echo.
echo ------------------------------------------------------------
echo The app has stopped. If it did not open, the reason is above.
echo Look for lines that start with [ERROR] or "Exception".
echo ------------------------------------------------------------
pause
