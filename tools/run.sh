#!/usr/bin/env bash
# Builds the modules with Maven, then runs the programs, each one from its module directory so that it reads its
# own .env file:
#   tools/run.sh              starts the persistence server and the simulation service in the background, then
#                             the simulator user interface; the servers are stopped when the simulator exits.
#   tools/run.sh persistence  only runs the persistence server (robotsim module).
#   tools/run.sh simulation   only runs the simulation service (simulation-service module).
#   tools/run.sh simulator    only runs the simulator user interface (robotsim module).
set -euo pipefail

MODE="${1:-all}"

case "$MODE" in
	all|persistence|simulation|simulator) ;;
	*) echo "Usage: $0 [all|persistence|simulation|simulator]" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mvn -q -DskipTests package
mvn -q -pl robotsim dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
ROBOTSIM_CP="$ROOT/robotsim/target/classes:$(cat robotsim/target/classpath.txt)"

# Reads a variable from the environment or from the .env file of a module, or prints the given default value.
env_value() {
	local module=$1 name=$2 default=$3 value
	value=$(sed -n "s/^[[:space:]]*\(export[[:space:]]\+\)\?$name[[:space:]]*=[[:space:]]*//p" "$module/.env" 2>/dev/null | tail -1 | tr -d "\"'\r")
	echo "${!name:-${value:-$default}}"
}

# Waits until a server accepts connections, or exits if its process died.
wait_for() {
	local name=$1 pid=$2 host=$3 port=$4
	for _ in $(seq 300); do
		(echo > "/dev/tcp/$host/$port") 2>/dev/null && return
		kill -0 "$pid" 2>/dev/null || { echo "The $name failed to start." >&2; exit 1; }
		sleep 0.1
	done
	echo "The $name is not listening on $host:$port." >&2
	exit 1
}

run_robotsim() {
	cd "$ROOT/robotsim"
	exec java -Djava.util.logging.config.file=config/logging.properties -cp "$ROBOTSIM_CP" "$1"
}

run_persistence() {
	run_robotsim fr.tp.inf112.projects.robotsim.persistence.PersistenceServer
}

run_simulator() {
	run_robotsim fr.tp.inf112.projects.robotsim.app.SimulatorApplication
}

run_simulation() {
	cd "$ROOT/simulation-service"
	exec java -jar target/simulation-service-*.jar
}

case "$MODE" in
	persistence) run_persistence ;;
	simulation) run_simulation ;;
	simulator) run_simulator ;;
	all)
		( run_persistence ) &
		PERSISTENCE_PID=$!
		trap 'kill "$PERSISTENCE_PID" ${SIMULATION_PID:+"$SIMULATION_PID"} 2>/dev/null' EXIT
		wait_for "persistence server" "$PERSISTENCE_PID" \
			"$(env_value robotsim PERSISTENCE_HOST localhost)" "$(env_value robotsim PERSISTENCE_PORT 8090)"

		( run_simulation ) &
		SIMULATION_PID=$!
		wait_for "simulation service" "$SIMULATION_PID" \
			"$(env_value robotsim SIMULATION_HOST localhost)" "$(env_value robotsim SIMULATION_PORT 8080)"

		( run_simulator )
		;;
esac
