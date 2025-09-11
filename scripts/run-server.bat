@echo off
setlocal
set JAVA_OPTS=-Xms256m -Xmx512m
set VQ_LOG_DIR=%USERPROFILE%\Desktop\Vuong quyen\server
if not exist "%VQ_LOG_DIR%" mkdir "%VQ_LOG_DIR%"
java %JAVA_OPTS% -DVQ_LOG_DIR="%VQ_LOG_DIR%" -jar "%~dp0..\server\build\libs\server-all.jar"
