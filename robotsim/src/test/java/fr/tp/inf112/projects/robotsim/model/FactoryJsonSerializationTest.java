package fr.tp.inf112.projects.robotsim.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.tp.inf112.projects.robotsim.TestConditions;
import fr.tp.inf112.projects.robotsim.json.FactoryObjectMapper;
import fr.tp.inf112.projects.robotsim.model.path.AbstractFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.BasicPolygonShape;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;

/**
 * Checks that the factory models are converted to JSON text and back with {@link FactoryObjectMapper} without losing
 * data or the references between the components.
 */
class FactoryJsonSerializationTest {

	private final ObjectMapper objectMapper = FactoryObjectMapper.create();

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
	void polymorphicValuesKeepTheirClass()
	throws JsonProcessingException {
		final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(factory));

		// The default typing of FactoryObjectMapper wraps polymorphic values in [class name, value] arrays.
		assertThat(json.isArray()).isTrue();
		assertThat(json.get(0).asText()).isEqualTo(Factory.class.getName());
	}

	@Test
	void componentsKeepTheirClassAndShape()
	throws JsonProcessingException {
		final Factory roundTripFactory = roundTrip(factory);
		final List<Component> components = TestFactories.components(factory);
		final List<Component> roundTripComponents = TestFactories.components(roundTripFactory);

		assertThat(roundTripComponents).hasSameSizeAs(components);

		for (int index = 0; index < components.size(); index++) {
			final Component component = components.get(index);
			final Component roundTripComponent = roundTripComponents.get(index);

			assertThat(roundTripComponent).as(component.getName()).hasSameClassAs(component);
			assertThat(roundTripComponent.getName()).isEqualTo(component.getName());
			assertThat(roundTripComponent.getPositionedShape()).hasSameClassAs(component.getPositionedShape());
			assertThat(roundTripComponent.getPosition()).isEqualTo(component.getPosition());
			assertThat(roundTripComponent.getWidth()).as(component.getName()).isEqualTo(component.getWidth());
			assertThat(roundTripComponent.getHeight()).as(component.getName()).isEqualTo(component.getHeight());
		}

		final Conveyor conveyor = TestFactories.components(roundTripFactory, Conveyor.class).get(0);
		assertThat(((BasicPolygonShape) conveyor.getPositionedShape()).getPoints()).hasSize(8);

		final Puck puck = TestFactories.components(roundTripFactory, Puck.class).get(0);
		assertThat(puck.getPositionedShape()).isInstanceOf(CircularShape.class);
	}

	@Test
	void attributesWithoutPublicGetterAreKept()
	throws JsonProcessingException {
		final Robot robot = TestFactories.components(factory, Robot.class).get(0);
		final Robot roundTripRobot = TestFactories.components(roundTrip(factory), Robot.class).get(0);
		final Battery battery = (Battery) TestFactories.getField(robot, Robot.class, "battery");
		final Battery roundTripBattery = (Battery) TestFactories.getField(roundTripRobot, Robot.class, "battery");

		assertThat(TestFactories.getField(roundTripRobot, Robot.class, "speed")).isEqualTo(TestFactories.getField(robot, Robot.class, "speed"));
		assertThat(TestFactories.getField(roundTripBattery, Battery.class, "capacity")).isEqualTo(TestFactories.getField(battery, Battery.class, "capacity"));
		assertThat(TestFactories.getField(roundTripBattery, Battery.class, "level")).isEqualTo(TestFactories.getField(battery, Battery.class, "level"));

		final List<Door> doors = TestFactories.components(factory, Door.class);
		final List<Door> roundTripDoors = TestFactories.components(roundTrip(factory), Door.class);

		for (int index = 0; index < doors.size(); index++) {
			assertThat(TestFactories.getField(roundTripDoors.get(index), Door.class, "open"))
				.isEqualTo(TestFactories.getField(doors.get(index), Door.class, "open"));
		}
	}

	@Test
	void componentsReferToTheirFactory()
	throws JsonProcessingException {
		final Factory roundTripFactory = roundTrip(factory);

		for (final Component component : TestFactories.components(roundTripFactory)) {
			assertThat(component.getFactory()).as(component.getName()).isSameAs(roundTripFactory);
		}

		for (final Robot robot : TestFactories.components(roundTripFactory, Robot.class)) {
			final AbstractFactoryPathFinder<?, ?> pathFinder = (AbstractFactoryPathFinder<?, ?>) TestFactories.getField(robot, Robot.class, "pathFinder");

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
				assertThat(TestFactories.getField(door, Door.class, "room")).isSameAs(room);
			}
		}

		for (final Area area : TestFactories.components(roundTripFactory, Area.class)) {
			assertThat(components).contains((Component) TestFactories.getField(area, Area.class, "machine"));
		}

		for (final Robot robot : TestFactories.components(roundTripFactory, Robot.class)) {
			final List<?> targetComponents = (List<?>) TestFactories.getField(robot, Robot.class, "targetComponents");

			assertThat(targetComponents).as(robot.getName()).hasSize(2).allMatch(components::contains);
		}
	}

	@Test
	void derivedAndRuntimeAttributesAreNotSerialized()
	throws JsonProcessingException {
		final Set<String> propertyNames = new HashSet<>();
		collectPropertyNames(objectMapper.readTree(objectMapper.writeValueAsString(factory)), propertyNames);

		assertThat(propertyNames)
			.contains("@id", "components", "positionedShape", "targetComponents", "pathFinder", "simulationStarted",
					  "blocked")
			.doesNotContain("style", "shape", "figures", "observers", "mobile", "livelyLocked", "positionLocks",
							"walls", "graph", "currentPathPositionsIter", "targetComponentsIterator", "lockedPosition",
							"detouring", "factory");
	}

	private static void collectPropertyNames(final JsonNode node,
											 final Set<String> propertyNames) {
		node.fieldNames().forEachRemaining(propertyNames::add);

		for (final JsonNode child : node) {
			collectPropertyNames(child, propertyNames);
		}
	}

	@Test
	void classesOutsideOfTheModelAreRejected() {
		// A component list containing a JDK class that is not allowed by the polymorphic type validator.
		final String json = "[\"" + Factory.class.getName() + "\",{\"components\":[\"java.util.ArrayList\",[[\"java.net.URL\",\"http://example.com\"]]]}]";

		assertThatThrownBy(() -> objectMapper.readValue(json, Factory.class)).isInstanceOf(JsonMappingException.class);
	}

	@Test
	void runningSimulationStateIsSerialized()
	throws Exception {
		final Thread simulationThread = TestConditions.startSimulation(factory);

		try {
			assertThat(roundTrip(factory).isSimulationStarted()).isTrue();
		}
		finally {
			TestConditions.stopSimulation(factory, simulationThread);
		}

		assertThat(roundTrip(factory).isSimulationStarted()).isFalse();
	}

	@Test
	void roundTripModelCanBeSimulated()
	throws Exception {
		final Factory roundTripFactory = roundTrip(factory);
		final List<Position> initialPositions = TestFactories.robotPositions(roundTripFactory);
		final Thread simulationThread = TestConditions.startSimulation(roundTripFactory);

		try {
			TestConditions.waitUntil("every robot has moved", () -> {
				final List<Position> positions = TestFactories.robotPositions(roundTripFactory);

				for (int index = 0; index < positions.size(); index++) {
					if (positions.get(index).equals(initialPositions.get(index))) {
						return false;
					}
				}

				return true;
			});
		}
		finally {
			TestConditions.stopSimulation(roundTripFactory, simulationThread);
		}

		assertThat(simulationThread.isAlive()).isFalse();
	}

	@Test
	void javaSerializationStillWorks()
	throws IOException, ClassNotFoundException {
		// The persistence server stores the factories with Java serialization.
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (final ObjectOutputStream objOutStream = new ObjectOutputStream(bytes)) {
			objOutStream.writeObject(roundTrip(factory));
		}

		try (final ObjectInputStream objInStream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			assertThat(objInStream.readObject().toString()).isEqualTo(factory.toString());
		}
	}
}
