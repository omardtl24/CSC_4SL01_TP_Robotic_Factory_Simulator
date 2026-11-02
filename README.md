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
| Simulator | `robotsim` | `fr.tp.inf112.projects.robotsim.app.SimulatorApplication` | none | Swing user interface displaying the factory simulated by the simulation service. |

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

Tests of the `robotsim` module (plain JUnit, no server needed):

- `model.FactoryJsonSerializationTest` converts a factory model to JSON and back with `FactoryObjectMapper`, and
  checks that the model is unchanged, that the components still refer to their factory, that shared components
  (robot targets, room areas and doors...) are not duplicated, that derived and runtime attributes are not
  serialized, that classes outside of the model are rejected, that the round-trip model can still be simulated and
  that Java serialization (used by the persistence server) still works.
- `app.RemoteSimulatorControllerTest` drives the remote controller against `FakeSimulationService`, a small server
  with the same REST methods running on the HTTP server of the JDK. It checks that the factory is simulated by the
  service and not locally, that the viewer displays and is notified of the models read from the service, that
  stopping ends the simulation and the updates, and the error cases (unsaved factory, unknown factory, service not
  running).

Tests of the `simulation-service` module:

- `FactoryJsonSerializationTest` runs the same kind of round-trip checks with the object mapper of the Spring
  context, which also checks that Spring uses `FactoryObjectMapper`.
- `SimulationControllerTest` calls the REST methods with MockMvc, using an in-memory persistence manager instead
  of the persistence server, and parses the returned JSON as a client of the service would.

To run the tests of a single module: `mvn test -pl robotsim`, or `mvn test -pl simulation-service -am` (the `-am`
option also builds `robotsim`, which the simulation service depends on).

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

## How a simulation runs

The simulator window does not simulate anything: it uses `RemoteSimulatorController`, so the robots are moved by
the simulation service, and the window only displays copies of the simulated model that it downloads from the
service. Each program holds its own copy of the factory and they only exchange copies over the network:

| Program | Copy of the factory | Role |
|---|---|---|
| Simulator window | The model currently displayed | Draws it and sends the start and stop requests. |
| Simulation service | The model being simulated | Moves the robots. |
| Persistence server | The saved files in `robotsim/data/` | Stores the factories and sends them back. |

### Step by step

1. **Launch.** `SimulatorApplication` builds a factory and the window displays it. This factory has no identifier
   and is only known by the window.
2. **Save.** The window sends a copy of the factory (Java serialization) to the persistence server, which writes it
   to `robotsim/data/<name>`. The file name becomes the identifier of the factory.
3. **Start.** The window calls `GET /simulation/start/{id}`. The service reads the factory from the persistence
   server, keeps this copy, starts the threads of its robots and answers `true`. The window then starts an update
   thread. The factory of the window itself never moves.
4. **While the simulation runs**, every 100 ms the update thread calls `GET /simulation/{id}`, parses the JSON text
   into a new `Factory` object and passes it to `setCanvas()`, which moves the viewer (the observer of the model) to
   the new object and notifies it. The viewer always draws the model of its controller, so it displays the latest
   copy of the simulated factory, replaced about ten times per second.
5. **Stop.** The window calls `GET /simulation/stop/{id}`: the service stops the robots and forgets its copy. The
   window stops its update thread and marks the last copy it received as stopped, which updates the menus. The
   robots stay displayed where they were when the simulation was stopped.

```
Window ── GET /simulation/start/{id} ──► Simulation service ── read {id} ──► Persistence server
                                           │◄──────────── saved factory ──────────┘
                                           │ simulates its copy
Window ◄──── every 100 ms: JSON copy ──────┤
Window ── GET /simulation/stop/{id} ───► │ stops and forgets its copy
```

### Consequences

- **Only saved factories can be simulated.** The service reads the factory from the persistence server when the
  simulation starts. Starting a factory that was never saved only logs a warning in the console of the simulator.
- **Starting again after a stop restarts from the saved file**, not from the positions where the robots were
  stopped, since the service forgets its copy when the simulation stops. To continue from the current positions,
  save the displayed factory before starting again.
- **Opening** a factory loads it from the persistence server and displays it with the identifier of its file;
  starting then simulates that file.
- **Several windows simulating the same identifier share one simulation**: the service answers that the factory is
  already simulated, and every window displays the same robots.
- **If the service stops simulating the factory** (for instance because it was restarted), the window receives a
  404 answer, stops updating and marks the simulation as stopped.
- **The persistence server is only used to save, open and start factories.** It is not involved while a
  simulation runs.

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
