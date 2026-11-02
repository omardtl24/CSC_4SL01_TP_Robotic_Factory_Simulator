package fr.tp.slr201.projects.robotsim.service.simulation;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Factory;

/**
 * REST methods simulating factory models identified by their ID (the file name of the model on the persistence
 * server). All methods use GET so that they can be called from a web browser.
 */
@RestController
@RequestMapping("/simulation")
public class SimulationController {

	private static final Logger LOGGER = LoggerFactory.getLogger(SimulationController.class);

	private final CanvasPersistenceManager persistenceManager;

	private final Map<String, Factory> simulatedFactories = new ConcurrentHashMap<>();

	public SimulationController(final CanvasPersistenceManager persistenceManager) {
		this.persistenceManager = persistenceManager;
	}

	/**
	 * Reads the factory model from the persistence server and starts simulating it.
	 * @return {@code true} if the simulation is running, {@code false} if the model could not be read.
	 */
	@GetMapping("/start/{factoryId}")
	public boolean startSimulation(@PathVariable final String factoryId) {
		final Factory simulatedFactory = simulatedFactories.get(factoryId);

		if (simulatedFactory != null) {
			LOGGER.info("Factory '{}' is already simulated.", factoryId);

			return true;
		}

		final Factory factory;

		try {
			factory = (Factory) persistenceManager.read(factoryId);
		}
		catch (final IOException | RuntimeException ex) {
			LOGGER.error("Unable to read factory '{}' from the persistence server.", factoryId, ex);

			return false;
		}

		if (factory == null) {
			LOGGER.warn("Factory '{}' was not found on the persistence server.", factoryId);

			return false;
		}

		simulatedFactories.put(factoryId, factory);
		LOGGER.info("Starting the simulation of factory '{}'.", factoryId);

		// Factory.startSimulation() only returns when the simulation is stopped, since it waits for the threads of
		// the mobile components.
		final Thread simulationThread = new Thread(factory::startSimulation, "simulation-" + factoryId);
		simulationThread.start();

		// Wait for the thread to mark the simulation as started, otherwise a stop request received in the meantime
		// would be lost and the simulation would never end.
		while (!factory.isSimulationStarted() && simulationThread.isAlive()) {
			Thread.onSpinWait();
		}

		return true;
	}

	/**
	 * @return The factory model currently simulated, or a 404 response if it is not simulated.
	 */
	@GetMapping("/{factoryId}")
	public ResponseEntity<Factory> getSimulatedFactory(@PathVariable final String factoryId) {
		final Factory factory = simulatedFactories.get(factoryId);

		if (factory == null) {
			LOGGER.warn("Factory '{}' is not simulated.", factoryId);

			return ResponseEntity.notFound().build();
		}

		LOGGER.debug("Sending factory '{}' (simulation started: {}).", factoryId, factory.isSimulationStarted());

		return ResponseEntity.ok(factory);
	}

	/**
	 * Stops the simulation of the factory model and removes it from the simulated ones.
	 * @return {@code true} if the factory was simulated, {@code false} otherwise.
	 */
	@GetMapping("/stop/{factoryId}")
	public boolean stopSimulation(@PathVariable final String factoryId) {
		final Factory factory = simulatedFactories.remove(factoryId);

		if (factory == null) {
			LOGGER.warn("Factory '{}' is not simulated.", factoryId);

			return false;
		}

		LOGGER.info("Stopping the simulation of factory '{}'.", factoryId);
		factory.stopSimulation();

		return true;
	}
}
