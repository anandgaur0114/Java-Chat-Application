@echo off
title Chat Server
echo ====================================================
echo  Launching Core Java Chat Server
echo ====================================================

java -cp "lib/mysql-connector-j-9.7.0.jar;." server.ChatServer
pause
