@echo off
title Chat Client
echo ====================================================
echo  Launching Swing Chat Client
echo ====================================================

start javaw -cp "lib/mysql-connector-j-9.7.0.jar;." client.ChatGUI
