# Robotic Factory Simulator

A simulator of robots moving in a factory, built during the CSC 4SL01 course. The factory model can be
simulated locally in a Swing viewer or remotely by a Spring Boot microservice, and is stored on a persistence
server.

## Architecture

The project is a Maven multi-module build with three programs:

| Program | Module | Main class | Port | Role |
|---|---|---|---|---|
| Persistence server | `robotsim` | `fr.tp.inf112.projects.robotsim.persistence.PersistenceServer` | 8090 (socket) | Stores the factory models (Java serialization) in `robotsim/data/`. |
| Simulation service | `simulation-service` | `fr.tp.slr201.projects.robotsim.service.simulation.SimulationServiceApplication` | 8080 (HTTP) | Spring Boot REST service that reads models from the persistence server and simulates them. |
| Simulator | `robotsim` | `fr.tp.inf112.projects.robotsim.app.SimulatorApplication` | none | Swing user interface displaying the factory. |

```
pom.xml                       parent: Java 17, Spring Boot and library versions, plugin versions
libs/                         course libraries not published on Maven Central (canvas viewer, graph)
robotsim/                     factory model, Swing simulator and persistence server (plain Java)
  .env                        local overrides of the configuration
  config/                     java.util.logging configuration and Eclipse launch configurations
  src/main/resources/application.properties
simulation-service/           Spring Boot simulation microservice
  .env
  src/main/resources/application.properties
  src/test/java/              JUnit tests of the JSON serialization and of the REST methods
tools/run.sh                  builds and runs the programs
tools/log-viewer.html         viewer for the XML log files of robotsim
```

The simulation service depends on the `robotsim` module for the factory model. Models are exchanged as Java
serialized objects with the persistence server and as JSON text with the clients of the simulation service.

## Requirements

- JDK 17 or later.
- Maven 3.9 or later (`mvn` on the `PATH`). In Eclipse, the M2E plugin embeds its own Maven.
- Bash, for `tools/run.sh` (Linux, macOS, WSL or Git Bash).

The first build downloads the dependencies (Spring Boot, Jackson, JGraphT...) from Maven Central.

## Configuration

Each module reads its configuration from `src/main/resources/application.properties`. Any property can be
overridden, from the lowest to the highest priority:

1. in the `.env` file of the module, using the environment variable name of the property;
2. by an environment variable: the property name in upper case, with dots and dashes replaced by underscores
   (`persistence.port` becomes `PERSISTENCE_PORT`);
3. by a system property for `robotsim` (`-Dpersistence.port=9000`), or a command line argument for the Spring Boot
   service (`--server.port=9000`).

The `.env` file is read from the working directory, so the programs must be started from their module directory
(`tools/run.sh`, the Maven commands below and the Eclipse launch configurations all do it).

| Module | Property | `.env` / environment variable | Default | Meaning |
|---|---|---|---|---|
| `robotsim` | `persistence.host` | `PERSISTENCE_HOST` | `localhost` | Host of the persistence server. |
| `robotsim` | `persistence.port` | `PERSISTENCE_PORT` | `8090` | Port of the persistence server. |
| `robotsim` | `persistence.storage.directory` | `PERSISTENCE_STORAGE_DIRECTORY` | `data` | Directory of the stored factories. |
| `robotsim` | `simulation.host` | `SIMULATION_HOST` | `localhost` | Host of the simulation service. |
| `robotsim` | `simulation.port` | `SIMULATION_PORT` | `8080` | Port of the simulation service. |
| `simulation-service` | `server.port` | `SERVER_PORT` | `8080` | HTTP port of the REST methods. |
| `simulation-service` | `persistence.host` | `PERSISTENCE_HOST` | `localhost` | Host of the persistence server. |
| `simulation-service` | `persistence.port` | `PERSISTENCE_PORT` | `8090` | Port of the persistence server. |

When changing a port, change it on both sides: `SERVER_PORT` of the simulation service must match
`SIMULATION_PORT` of `robotsim`, and the `PERSISTENCE_PORT` values of both modules must match.

## Building and testing

From the root directory:

```bash
mvn package            # compiles both modules, runs the tests and builds the jars
mvn test               # only runs the tests
mvn package -DskipTests
```

The tests are in the `simulation-service` module:

- `FactoryJsonSerializationTest` converts a factory model to JSON and back with the object mapper of the Spring
  context, and checks that the model is unchanged, that the components still refer to their factory, that shared
  components (robot targets, room areas and doors...) are not duplicated, that derived and runtime attributes are
  not serialized, and that the round-trip model can still be simulated.
- `SimulationControllerTest` calls the REST methods with MockMvc, using an in-memory persistence manager instead
  of the persistence server, and parses the returned JSON as a client of the service would.

## Running

The programs must be started in this order: persistence server, simulation service, simulator.

### With `tools/run.sh`

```bash
tools/run.sh                # builds everything, then starts the three programs
tools/run.sh persistence    # only the persistence server
tools/run.sh simulation     # only the simulation service
tools/run.sh simulator      # only the simulator user interface
```

Without argument, the script starts the persistence server and the simulation service in the background, waits
until they accept connections, then opens the simulator. Closing the simulator stops the two servers. The script
builds with `mvn package -DskipTests` first, so Maven is required.

### With Maven

Build and install the modules once, so that the simulation service finds the `robotsim` module:

```bash
mvn install -DskipTests
```

Then, in three terminals from the root directory:

```bash
# 1. Persistence server
mvn -q -pl robotsim compile exec:exec@persistence

# 2. Simulation service
cd simulation-service && java -jar target/simulation-service-1.0.0-SNAPSHOT.jar

# 3. Simulator user interface
mvn -q -pl robotsim compile exec:exec@simulator
```

The `exec:exec` executions are configured in `robotsim/pom.xml` to run from the `robotsim` directory with the
logging configuration of `robotsim/config/logging.properties`.

### With Eclipse

1. Install M2E and Spring Tools, then import the project with *File > Import > Maven > Existing Maven Projects*,
   selecting the root directory. This creates the `robotsim-parent`, `robotsim` and `simulation-service` projects.
2. Run the persistence server and the simulator with the launch configurations of `robotsim/config/`
   (`PersistenceServer`, `SimulatorApplication`, or `ServerAndSimulator` for both).
3. Run the simulation service with *Run As > Spring Boot App* on `SimulationServiceApplication`.

## Using the simulation service

A factory must first be saved on the persistence server, for instance with *Save* in the simulator, which stores
it in `robotsim/data/`. Its file name is its identifier. The REST methods use GET so that they can be tried from a
web browser:

| URL | Result |
|---|---|
| `http://localhost:8080/simulation/start/{factoryId}` | Reads the factory from the persistence server and starts simulating it. Returns `true`, or `false` if it cannot be read. |
| `http://localhost:8080/simulation/{factoryId}` | The factory being simulated, as JSON. 404 if it is not simulated. |
| `http://localhost:8080/simulation/stop/{factoryId}` | Stops the simulation. Returns `false` if the factory was not simulated. |

For example, with a factory saved as `test.factory`:

```bash
curl http://localhost:8080/simulation/start/test.factory
curl http://localhost:8080/simulation/test.factory
curl http://localhost:8080/simulation/stop/test.factory
```

### JSON format

The JSON text is produced by `fr.tp.inf112.projects.robotsim.json.FactoryObjectMapper`, which must be used by
every client of the service to read it back:

- Polymorphic values are written as `[class name, value]` arrays, so that the right component and shape classes
  are recreated. Only the classes of the model, the canvas and graph libraries and the Java collections are
  accepted.
- Each component is written once, in the `components` of its factory, with an `@id`. Other references to it
  (robot targets, room areas and doors, path finder factory...) are written as this `@id`.
- Derived attributes (coordinates of components, styles...) and runtime attributes (observers, locks, path
  iterators...) are not serialized. `simulationStarted` and the `blocked` state of robots are, since a remote
  viewer displays them.

## Logs

- `robotsim` programs log to the console and to `robotsim/config/robotsim0.log` (XML format). When the
  persistence server and the simulator run at the same time, the second one started writes to
  `robotsim0.log.1`. Open `tools/log-viewer.html` in a browser and drop a log file on it to browse it.
- The simulation service logs to the console. The log level of the project classes is set by
  `logging.level.fr.tp` in its `application.properties`.
