@echo off
title Compiling Java Chat Application
echo ====================================================
echo  Compiling Chat Application (JDK 25)
echo ====================================================

REM Compile server and client classes with MySQL Connector JAR in classpath
javac -cp "lib/mysql-connector-j-9.7.0.jar;." server/*.java client/*.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation completed successfully with 0 errors!
) else (
    echo [ERROR] Compilation failed. Please inspect the compiler output above.
)
pause
