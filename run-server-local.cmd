@echo off
title PMIS Docket - server (local test)
rem Starts the PMIS Docket server on this PC with demo data (http://localhost:8080).
cd /d "%~dp0server"
call mvn -e spring-boot:run -Dspring-boot.run.profiles=local
echo.
echo The server has stopped. If it did not start, the reason is above.
pause
