@echo off
REM 启动 docqa 本地开发用的 Redis（双击即可，关掉这个窗口 Redis 就停）
REM 配置在 D:\Redis\redis.conf，只监听 127.0.0.1:6379
cd /d D:\Redis
start "" /min D:\Redis\bin\redis-server.exe D:\Redis\redis.conf
timeout /t 2 >nul
D:\Redis\bin\redis-cli.exe -h 127.0.0.1 -p 6379 ping
pause
