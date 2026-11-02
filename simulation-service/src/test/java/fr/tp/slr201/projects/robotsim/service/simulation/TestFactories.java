package fr.tp.slr201.projects.robotsim.service.simulation;

import java.util.List;

import fr.tp.inf112.projects.canvas.model.impl.BasicPoint;
import fr.tp.inf112.projects.robotsim.model.Area;
import fr.tp.inf112.projects.robotsim.model.Battery;
import fr.tp.inf112.projects.robotsim.model.ChargingStation;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Conveyor;
import fr.tp.inf112.projects.robotsim.model.Door;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Machine;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.path.CustomDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.path.JGraphTDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.BasicPolygonShape;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

/**
 * Factory models used by the tests, built as in the {@code main} method of {@code SimulatorApplication}.
 */
final class TestFactories {

	static final String FACTORY_ID = "test.factory";

	private TestFactories() {
	}

	static Factory createFactory() {
		final Factory factory = new Factory(200, 200, "Simple Test Puck Factory", 5);
		factory.setId(FACTORY_ID);

		final Room room1 = new Room(factory, new RectangularShape(20, 20, 75, 75), "Production Room 1");
		new Door(room1, Room.WALL.BOTTOM, 10, 20, true, "Entrance");
		final Area area1 = new Area(room1, new RectangularShape(35, 35, 50, 50), "Production Area 1");
		final Machine machine1 = new Machine(area1, new RectangularShape(50, 50, 15, 15), "Machine 1");

		final Room room2 = new Room(factory, new RectangularShape(120, 22, 75, 75), "Production Room 2");
		new Door(room2, Room.WALL.LEFT, 10, 20, true, "Entrance");
		final Area area2 = new Area(room2, new RectangularShape(135, 35, 50, 50), "Production Area 1");
		final Machine machine2 = new Machine(area2, new RectangularShape(150, 50, 15, 15), "Machine 1");

		final int baselineSize = 3;
		final int xCoordinate = 10;
		final int yCoordinate = 165;
		final int width = 10;
		final int height = 30;
		final BasicPolygonShape conveyorShape = new BasicPolygonShape();
		conveyorShape.addPoint(new BasicPoint(xCoordinate, yCoordinate));
		conveyorShape.addPoint(new BasicPoint(xCoordinate + width, yCoordinate));
		conveyorShape.addPoint(new BasicPoint(xCoordinate + width, yCoordinate + height - baselineSize));
		conveyorShape.addPoint(new BasicPoint(xCoordinate + width + baselineSize, yCoordinate + height - baselineSize));
		conveyorShape.addPoint(new BasicPoint(xCoordinate + width + baselineSize, yCoordinate + height));
		conveyorShape.addPoint(new BasicPoint(xCoordinate - baselineSize, yCoordinate + height));
		conveyorShape.addPoint(new BasicPoint(xCoordinate - baselineSize, yCoordinate + height - baselineSize));
		conveyorShape.addPoint(new BasicPoint(xCoordinate, yCoordinate + height - baselineSize));
		new Conveyor(factory, conveyorShape, "Conveyor 1");

		final Room chargingRoom = new Room(factory, new RectangularShape(125, 125, 50, 50), "Charging Room");
		new Door(chargingRoom, Room.WALL.RIGHT, 10, 20, false, "Entrance");
		new ChargingStation(factory, new RectangularShape(150, 145, 15, 15), "Charging Station");

		final int pathResolution = factory.getPathResolution();
		final Robot robot1 = new Robot(factory, new JGraphTDijkstraFactoryPathFinder(factory, pathResolution),
									   new CircularShape(5, 5, 2), new Battery(10), "Robot 1");
		robot1.addTargetComponent(machine1);
		robot1.addTargetComponent(machine2);

		final Robot robot2 = new Robot(factory, new CustomDijkstraFactoryPathFinder(factory, pathResolution),
									   new CircularShape(45, 5, 2), new Battery(10), "Robot 2");
		robot2.addTargetComponent(machine1);
		robot2.addTargetComponent(machine2);

		return factory;
	}

	static List<Component> components(final Factory factory) {
		return factory.getFigures().stream().map(Component.class::cast).toList();
	}

	static <T extends Component> List<T> components(final Factory factory,
													final Class<T> componentClass) {
		return components(factory).stream().filter(componentClass::isInstance).map(componentClass::cast).toList();
	}
}
