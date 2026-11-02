package fr.tp.slr201.projects.robotsim.service.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.tp.inf112.projects.robotsim.model.Area;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Conveyor;
import fr.tp.inf112.projects.robotsim.model.Door;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Position;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.path.AbstractFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.BasicPolygonShape;

/**
 * Checks that the factory models are converted to JSON text and back by the object mapper used by the simulation
 * service, without losing data or the references between the components.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FactoryJsonSerializationTest {

	private static final long SIMULATION_TIMEOUT_MS = 10_000;

	@Autowired
	private ObjectMapper objectMapper;

	private Factory factory;

	@BeforeEach
	void createFactory() {
		factory = TestFactories.createFactory();
	}

	private Factory roundTrip(final Factory factoryModel)
	throws JsonProcessingException {
		return objectMapper.readValue(objectMapper.writeValueAsString(factoryModel), Factory.class);
	}

	@Test
	void springUsesTheFactoryObjectMapper()
	throws JsonProcessingException {
		final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(factory));

		// The default typing of FactoryObjectMapper wraps polymorphic values in [class name, value] arrays.
		assertThat(json.isArray()).isTrue();
		assertThat(json.get(0).asText()).isEqualTo(Factory.class.getName());
	}

	@Test
	void roundTripRecreatesTheSameModel()
	throws JsonProcessingException {
		final Factory roundTripFactory = roundTrip(factory);

		assertThat(roundTripFactory).isNotSameAs(factory);
		assertThat(roundTripFactory.toString()).isEqualTo(factory.toString());
		assertThat(roundTripFactory.getId()).isEqualTo(TestFactories.FACTORY_ID);
		assertThat(roundTripFactory.getName()).isEqualTo(factory.getName());
		assertThat(roundTripFactory.getWidth()).isEqualTo(factory.getWidth());
		assertThat(roundTripFactory.getHeight()).isEqualTo(factory.getHeight());
		assertThat(roundTripFactory.getPathResolution()).isEqualTo(factory.getPathResolution());
		assertThat(roundTripFactory.isSimulationStarted()).isFalse();
	}

	@Test
	void serializingTheRoundTripModelGivesTheSameJson()
	throws JsonProcessingException {
		final String json = objectMapper.writeValueAsString(factory);

		assertThat(objectMapper.writeValueAsString(objectMapper.readValue(json, Factory.class))).isEqualTo(json);
	}

	@Test
	void componentsKeepTheirClassAndShape()
	throws JsonProcessingException {
		final List<Component> components = TestFactories.components(factory);
		final List<Component> roundTripComponents = TestFactories.components(roundTrip(factory));

		assertThat(roundTripComponents).hasSameSizeAs(components);

		for (int index = 0; index < components.size(); index++) {
			final Component component = components.get(index);
			final Component roundTripComponent = roundTripComponents.get(index);

			assertThat(roundTripComponent).hasSameClassAs(component);
			assertThat(roundTripComponent.getName()).isEqualTo(component.getName());
			assertThat(roundTripComponent.getPositionedShape()).hasSameClassAs(component.getPositionedShape());
			assertThat(roundTripComponent.getPosition()).isEqualTo(component.getPosition());
			assertThat(roundTripComponent.getWidth()).isEqualTo(component.getWidth());
			assertThat(roundTripComponent.getHeight()).isEqualTo(component.getHeight());
		}

		final BasicPolygonShape conveyorShape = (BasicPolygonShape) TestFactories.components(roundTrip(factory), Conveyor.class).get(0).getPositionedShape();
		assertThat(conveyorShape.getPoints()).hasSize(8);
	}

	@Test
	void componentsReferToTheirFactory()
	throws JsonProcessingException {
		final Factory roundTripFactory = roundTrip(factory);

		for (final Component component : TestFactories.components(roundTripFactory)) {
			assertThat(ReflectionTestUtils.getField(component, "factory")).as(component.getName()).isSameAs(roundTripFactory);
		}

		for (final Robot robot : TestFactories.components(roundTripFactory, Robot.class)) {
			final AbstractFactoryPathFinder<?, ?> pathFinder = (AbstractFactoryPathFinder<?, ?>) ReflectionTestUtils.getField(robot, "pathFinder");

			assertThat(pathFinder).as(robot.getName()).isNotNull();
			assertThat(pathFinder.getFactoryModel()).as(robot.getName()).isSameAs(roundTripFactory);
			assertThat(pathFinder.getResolution()).isEqualTo(factory.getPathResolution());
		}
	}

	@Test
	void sharedComponentsAreNotDuplicated()
	throws JsonProcessingException {
		final Factory roundTripFactory = roundTrip(factory);
		final Set<Component> components = Collections.newSetFromMap(new IdentityHashMap<>());
		components.addAll(TestFactories.components(roundTripFactory));

		for (final Room room : TestFactories.components(roundTripFactory, Room.class)) {
			assertThat(room.getAreas()).allMatch(components::contains);
			assertThat(room.getDoors()).isNotEmpty().allMatch(components::contains);

			for (final Door door : room.getDoors()) {
				assertThat(ReflectionTestUtils.getField(door, "room")).isSameAs(room);
			}
		}

		for (final Area area : TestFactories.components(roundTripFactory, Area.class)) {
			assertThat(components).contains((Component) ReflectionTestUtils.getField(area, "machine"));
		}

		for (final Robot robot : TestFactories.components(roundTripFactory, Robot.class)) {
			final List<?> targetComponents = (List<?>) ReflectionTestUtils.getField(robot, "targetComponents");

			assertThat(targetComponents).as(robot.getName()).hasSize(2).allMatch(components::contains);
		}
	}

	@Test
	void derivedAndRuntimeAttributesAreNotSerialized()
	throws JsonProcessingException {
		final Set<String> propertyNames = new HashSet<>();
		collectPropertyNames(objectMapper.readTree(objectMapper.writeValueAsString(factory)), propertyNames);

		assertThat(propertyNames)
			.contains("@id", "components", "positionedShape", "targetComponents", "pathFinder", "simulationStarted")
			.doesNotContain("style", "shape", "figures", "observers", "mobile", "livelyLocked", "positionLocks", "walls", "graph", "currentPathPositionsIter", "targetComponentsIterator",
							"lockedPosition", "detouring", "factory");
	}

	private static void collectPropertyNames(final JsonNode node,
											 final Set<String> propertyNames) {
		node.fieldNames().forEachRemaining(propertyNames::add);

		for (final JsonNode child : node) {
			collectPropertyNames(child, propertyNames);
		}
	}

	@Test
	void runningSimulationStateIsSerialized()
	throws Exception {
		final Thread simulationThread = startSimulation(factory);

		try {
			waitUntil(factory::isSimulationStarted);

			assertThat(roundTrip(factory).isSimulationStarted()).isTrue();
		}
		finally {
			stopSimulation(factory, simulationThread);
		}

		assertThat(roundTrip(factory).isSimulationStarted()).isFalse();
	}

	@Test
	void roundTripModelCanBeSimulated()
	throws Exception {
		final Factory roundTripFactory = roundTrip(factory);
		final List<Robot> robots = TestFactories.components(roundTripFactory, Robot.class);
		final Map<Robot, Position> initialPositions = new HashMap<>();

		for (final Robot robot : robots) {
			// Copied since the position object of the robot is updated when it moves.
			initialPositions.put(robot, new Position(robot.getxCoordinate(), robot.getyCoordinate()));
		}

		final Thread simulationThread = startSimulation(roundTripFactory);

		try {
			waitUntil(() -> robots.stream().allMatch(robot -> !robot.getPosition().equals(initialPositions.get(robot))));
		}
		finally {
			stopSimulation(roundTripFactory, simulationThread);
		}

		assertThat(simulationThread.isAlive()).isFalse();
	}

	private static Thread startSimulation(final Factory factoryModel) {
		// Factory.startSimulation() only returns when the simulation is stopped.
		final Thread simulationThread = new Thread(factoryModel::startSimulation, "test-simulation");
		simulationThread.start();

		return simulationThread;
	}

	private static void stopSimulation(final Factory factoryModel,
									   final Thread simulationThread)
	throws InterruptedException {
		waitUntil(factoryModel::isSimulationStarted);
		factoryModel.stopSimulation();
		simulationThread.join(SIMULATION_TIMEOUT_MS);
	}

	private static void waitUntil(final BooleanSupplier condition)
	throws InterruptedException {
		final long deadline = System.currentTimeMillis() + SIMULATION_TIMEOUT_MS;

		while (!condition.getAsBoolean()) {
			assertThat(System.currentTimeMillis()).as("condition reached before the timeout").isLessThan(deadline);
			Thread.sleep(50);
		}
	}
}
