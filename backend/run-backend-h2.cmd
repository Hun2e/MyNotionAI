@echo off
setlocal

set "MVN_CMD=%~dp0mvnw.cmd"
set "LOG_FILE=%~dp0spring-boot.log"

if exist "%LOG_FILE%" del /f /q "%LOG_FILE%"

cd /d "%~dp0"
call "%MVN_CMD%" spring-boot:run ^
  -Dspring-boot.run.arguments="--spring.profiles.active=local" ^
  > "%LOG_FILE%" 2>&1

endlocal
