@echo off
cd /d "%~dp0"
title LOG empresas
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%D\bin\java.exe" set "JAVA=%%D\bin\java.exe"
set "VER="
"%JAVA%" -version >jv.tmp 2>&1
for /f "tokens=3" %%V in ('findstr /i version jv.tmp') do if not defined VER set "VER=%%~V"
del jv.tmp 2>nul
if not defined VER goto semjava
for /f "delims=.-" %%M in ("%VER%") do set "MAJ=%%M"
if %MAJ% LSS 17 goto antigo
echo Usando Java %VER%
echo Iniciando... deixe esta janela aberta e acesse http://localhost:8080
"%JAVA%" LogEmpresas.java
pause
exit /b
:semjava
echo.
echo Java nao encontrado. Instale o JDK 17 ou superior: oracle.com/java/technologies/downloads
pause
exit /b
:antigo
echo.
echo Seu Java e a versao %VER%, antiga demais. O LOG empresas precisa do JDK 17 ou superior.
echo Instale o JDK em oracle.com/java/technologies/downloads e rode este arquivo de novo.
pause
