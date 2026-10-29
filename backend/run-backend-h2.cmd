@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "MVN_CMD=%ROOT_DIR%\.tools\apache-maven-3.9.9\bin\mvn.cmd"
set "LOG_FILE=%~dp0spring-boot.log"

if exist "%LOG_FILE%" del /f /q "%LOG_FILE%"

cd /d "%~dp0"
call "%MVN_CMD%" spring-boot:run ^
  -Dspring-boot.run.arguments="--spring.profiles.active=local" ^
  > "%LOG_FILE%" 2>&1

endlocal
