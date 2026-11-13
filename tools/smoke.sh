#!/bin/bash
# usage: tools/smoke.sh <commands-file> (from project root).
# Starts runServer, sends the file's commands via RCON ("wait N" lines sleep for real), then stops the server.
# Needs enable-rcon=true and rcon.password=svdev in run/server.properties (docs/NOTES.md, "Smoke tests").
# Not via stdin: Gradle hands console input to the server in one burst, so waits between commands are lost.
T="$(dirname "$0")"
./gradlew runServer -q > run/smoke-server.out 2>&1 &
python -I "$T/rcon.py" "$1"
echo stop > run/smoke-stop.txt
python -I "$T/rcon.py" run/smoke-stop.txt > /dev/null
wait
