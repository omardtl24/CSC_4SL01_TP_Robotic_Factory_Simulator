package fr.tp.inf112.projects.robotsim.app;

import java.awt.Component;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import fr.tp.inf112.projects.canvas.model.impl.BasicPoint;
import fr.tp.inf112.projects.canvas.view.CanvasViewer;
import fr.tp.inf112.projects.robotsim.config.ServiceConfig;
import fr.tp.inf112.projects.robotsim.model.Area;
import fr.tp.inf112.projects.robotsim.model.Battery;
import fr.tp.inf112.projects.robotsim.model.ChargingStation;
import fr.tp.inf112.projects.robotsim.model.Conveyor;
import fr.tp.inf112.projects.robotsim.model.Door;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Machine;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.path.CustomDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.path.JGraphTDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.BasicPolygonShape;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;
import fr.tp.inf112.projects.robotsim.persistence.PersistenceClient;
import fr.tp.inf112.projects.robotsim.persistence.RemoteFactoryPersistenceManager;
import fr.tp.inf112.projects.robotsim.persistence.RemoteFileCanvasChooser;

public class SimulatorApplication {

	private static final Logger LOGGER = Logger.getLogger(SimulatorApplication.class.getName());

	public static void main(String[] args) {
		LOGGER.info("Starting the robot simulator...");
		
		LOGGER.config("With parameters " + Arrays.toString(args) + ".");
		
		final String persistenceHost;
		final int persistencePort;
		
		try {
			final ServiceConfig config = ServiceConfig.load(SimulatorApplication.class);
			persistenceHost = config.getString("persistence.host");
			persistencePort = config.getInt("persistence.port");
		}
		catch (final IOException | RuntimeException ex) {
			LOGGER.log(Level.SEVERE, "Unable to configure the robot simulator.", ex);
			
			return;
		}
		
		LOGGER.config("Using the persistence server " + persistenceHost + ":" + persistencePort + ".");
		
		final Factory factory = new Factory(200, 200, "Simple Test Puck Factory", 5);
		final Room room1 = new Room(factory, new RectangularShape(20, 20, 75, 75), "Production Room 1");
		new Door(room1, Room.WALL.BOTTOM, 10, 20, true, "Entrance");
		final Area area1 = new Area(room1, new RectangularShape(35, 35, 50, 50), "Production Area 1");
		final Machine machine1 = new Machine(area1, new RectangularShape(50, 50, 15, 15), "Machine 1");

		final Room room2 = new Room(factory, new RectangularShape( 120, 22, 75, 75 ), "Production Room 2");
		new Door(room2, Room.WALL.LEFT, 10, 20, true, "Entrance");
		final Area area2 = new Area(room2, new RectangularShape( 135, 35, 50, 50 ), "Production Area 1");
		final Machine machine2 = new Machine(area2, new RectangularShape( 150, 50, 15, 15 ), "Machine 1");
		
		final int baselineSize = 3;
		final int xCoordinate = 10;
		final int yCoordinate = 165;
		final int width =  10;
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
		final Conveyor conveyor1 = new Conveyor(factory, conveyorShape, "Conveyor 1");

		final Room chargingRoom = new Room(factory, new RectangularShape(125, 125, 50, 50), "Charging Room");
		new Door(chargingRoom, Room.WALL.RIGHT, 10, 20, false, "Entrance");
		final ChargingStation chargingStation = new ChargingStation(factory, new RectangularShape(150, 145, 15, 15), "Charging Station");

		final int factoryPathResolution = factory.getPathResolution();
		final FactoryPathFinder jgraphPahtFinder = new JGraphTDijkstraFactoryPathFinder(factory, factoryPathResolution);
		final Robot robot1 = new Robot(factory, jgraphPahtFinder, new CircularShape(5, 5, 2), new Battery(10), "Robot 1");
		robot1.addTargetComponent(machine1);
		robot1.addTargetComponent(machine2);
		//robot1.addTargetComponent(conveyor1);
		//robot1.addTargetComponent(chargingStation);

		final FactoryPathFinder customPathFinder = new CustomDijkstraFactoryPathFinder(factory, factoryPathResolution);
		final Robot robot2 = new Robot(factory, customPathFinder, new CircularShape(45, 5, 2), new Battery(10), "Robot 2");
		//robot2.addTargetComponent(chargingStation);
		robot2.addTargetComponent(machine1);
		robot2.addTargetComponent(machine2);
		//robot2.addTargetComponent(conveyor1);
		
		SwingUtilities.invokeLater(new Runnable() {
			  
			@Override
	        public void run() {
				final PersistenceClient persistenceClient = new PersistenceClient(persistenceHost, persistencePort);
				final RemoteFileCanvasChooser canvasChooser = new RemoteFileCanvasChooser("factory", "Puck Factory", persistenceClient);
				final Component factoryViewer = new CanvasViewer(new SimulatorController(factory, new RemoteFactoryPersistenceManager(canvasChooser, persistenceClient)));
				canvasChooser.setViewer(factoryViewer);
				//new CanvasViewer(factory);
			}
		});
	}
}
