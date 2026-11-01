#!/usr/bin/env bash
# Compiles the project, starts the persistence server in the background, then runs the simulator.
# The server is stopped when the simulator exits.
set -euo pipefail

cd "$(dirname "$0")/.."

CP="bin:libs/canvas-viewer.jar:libs/graph.jar:libs/jgrapht/lib/jgrapht-core-1.5.2.jar:libs/jgrapht/lib/jheaps-0.14.jar"
JVM_ARGS=(-Djava.util.logging.config.file=config/logging.properties -cp "$CP")
PORT=8090

javac --release 17 -d bin -cp "$CP" $(find src -name "*.java")

java "${JVM_ARGS[@]}" fr.tp.inf112.projects.robotsim.persistence.PersistenceServer &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null' EXIT

# Wait until the server accepts connections.
for _ in $(seq 50); do
	(echo > "/dev/tcp/localhost/$PORT") 2>/dev/null && break
	kill -0 "$SERVER_PID" 2>/dev/null || { echo "Persistence server failed to start." >&2; exit 1; }
	sleep 0.1
done

java "${JVM_ARGS[@]}" fr.tp.inf112.projects.robotsim.app.SimulatorApplication
