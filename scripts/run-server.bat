@echo off
setlocal
set JAVA_OPTS=-Xms256m -Xmx512m
java %JAVA_OPTS% -jar "%~dp0..\server\build\libs\server-all.jar"
