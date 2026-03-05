@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "MVN_CMD=%ROOT_DIR%\.tools\apache-maven-3.9.9\bin\mvn.cmd"
set "LOG_FILE=%~dp0spring-boot.log"

if exist "%LOG_FILE%" del /f /q "%LOG_FILE%"

cd /d "%~dp0"
call "%MVN_CMD%" spring-boot:run ^
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:mynotion;MODE=MySQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.hibernate.ddl-auto=update" ^
  > "%LOG_FILE%" 2>&1

endlocal
