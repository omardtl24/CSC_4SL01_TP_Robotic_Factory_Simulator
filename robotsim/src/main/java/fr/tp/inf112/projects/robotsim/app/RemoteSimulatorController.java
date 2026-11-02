package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.json.FactoryObjectMapper;
import fr.tp.inf112.projects.robotsim.model.Factory;

/**
 * Controller running the simulation of the factory on the simulation microservice instead of in this application.
 * The factory is identified by its ID, so it must have been saved on the persistence server read by the service.
 * While the simulation runs, the model simulated by the service is periodically read and displayed in place of the
 * local one.
 */
public class RemoteSimulatorController extends SimulatorController {

	private static final Logger LOGGER = Logger.getLogger(RemoteSimulatorController.class.getName());

	private static final long VIEWER_UPDATE_PERIOD_MS = 100;

	private static final int HTTP_OK = 200;

	private static final int HTTP_NOT_FOUND = 404;

	private final String simulationHost;

	private final int simulationPort;

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper;

	private Thread viewerUpdateThread;

	public RemoteSimulatorController(final Factory factoryModel,
									 final CanvasPersistenceManager persistenceManager,
									 final String simulationHost,
									 final int simulationPort) {
		super(factoryModel, persistenceManager);

		this.simulationHost = simulationHost;
		this.simulationPort = simulationPort;
		httpClient = HttpClient.newHttpClient();
		objectMapper = FactoryObjectMapper.create();
	}

	/**
	 * Asks the simulation service to start simulating the factory, then updates the viewer with the simulated model
	 * until the simulation is stopped.
	 */
	@Override
	public synchronized void startAnimation() {
		final String factoryId = getFactoryId();

		if (factoryId == null) {
			LOGGER.warning("The factory must be saved before being simulated by the simulation service.");

			return;
		}

		if (viewerUpdateThread != null && viewerUpdateThread.isAlive()) {
			LOGGER.info("Factory '" + factoryId + "' is already simulated.");

			return;
		}

		try {
			if (!callSimulationMethod("start", factoryId)) {
				LOGGER.warning("The simulation service could not start simulating factory '" + factoryId + "'.");

				return;
			}
		}
		catch (final IOException | URISyntaxException ex) {
			LOGGER.log(Level.SEVERE, "Unable to start the simulation of factory '" + factoryId + "'.", ex);

			return;
		}
		catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();

			return;
		}

		LOGGER.info("Factory '" + factoryId + "' is simulated by the simulation service.");

		viewerUpdateThread = new Thread(() -> updateViewer(factoryId), "viewer-update-" + factoryId);
		viewerUpdateThread.setDaemon(true);
		viewerUpdateThread.start();
	}

	/**
	 * Asks the simulation service to stop simulating the factory, and stops updating the viewer.
	 */
	@Override
	public synchronized void stopAnimation() {
		final String factoryId = getFactoryId();

		if (factoryId == null) {
			return;
		}

		try {
			if (!callSimulationMethod("stop", factoryId)) {
				LOGGER.warning("Factory '" + factoryId + "' was not simulated by the simulation service.");
			}
		}
		catch (final IOException | URISyntaxException ex) {
			LOGGER.log(Level.SEVERE, "Unable to stop the simulation of factory '" + factoryId + "'.", ex);
		}
		catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		finally {
			stopViewerUpdates();
		}
	}

	private void stopViewerUpdates() {
		if (viewerUpdateThread != null) {
			viewerUpdateThread.interrupt();

			try {
				viewerUpdateThread.join();
			}
			catch (final InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			viewerUpdateThread = null;
		}

		stopDisplayedModel();
	}

	/**
	 * The last model read from the service may still be marked as simulated: since it is only a copy that is not
	 * simulated here, stopping it just updates its state and notifies the viewer.
	 */
	private void stopDisplayedModel() {
		final Factory factory = (Factory) getCanvas();

		if (factory != null && factory.isSimulationStarted()) {
			factory.stopSimulation();
		}
	}

	/**
	 * Periodically reads the model simulated by the service and displays it, until the simulation is stopped.
	 */
	private void updateViewer(final String factoryId) {
		try {
			do {
				final Factory remoteFactory = getRemoteFactory(factoryId);

				if (remoteFactory == null) {
					LOGGER.info("Factory '" + factoryId + "' is no longer simulated by the simulation service.");
					stopDisplayedModel();

					break;
				}

				setCanvas(remoteFactory);

				Thread.sleep(VIEWER_UPDATE_PERIOD_MS);
			}
			while (((Factory) getCanvas()).isSimulationStarted());
		}
		catch (final InterruptedException ex) {
			// Interrupted by stopAnimation().
		}
		catch (final IOException | URISyntaxException ex) {
			LOGGER.log(Level.SEVERE, "Unable to read factory '" + factoryId + "' from the simulation service.", ex);
			stopDisplayedModel();
		}
	}

	/**
	 * Replaces the displayed model, moving the observers (the viewer) of the previous model to the new one and
	 * notifying them so that the viewer refreshes its display.
	 */
	@Override
	public void setCanvas(final Canvas canvasModel) {
		final Factory previousFactory = (Factory) getCanvas();
		final List<Observer> observers = previousFactory == null ? List.of()
																  : new ArrayList<>(previousFactory.getObservers());

		super.setCanvas(canvasModel);

		final Factory factory = (Factory) getCanvas();

		if (factory != null) {
			for (final Observer observer : observers) {
				if (!factory.getObservers().contains(observer)) {
					factory.addObserver(observer);
				}
			}

			factory.notifyObservers();
		}
	}

	private String getFactoryId() {
		final Canvas canvas = getCanvas();

		return canvas == null ? null : canvas.getId();
	}

	/**
	 * Calls the start or stop REST method of the simulation service.
	 * @return The boolean answered by the service.
	 */
	private boolean callSimulationMethod(final String method,
										 final String factoryId)
	throws IOException, InterruptedException, URISyntaxException {
		final HttpResponse<String> response = sendRequest("/simulation/" + method + "/" + factoryId);

		if (response.statusCode() != HTTP_OK) {
			throw new IOException("The simulation service answered " + response.statusCode() + " to " + response.uri() + ".");
		}

		return Boolean.parseBoolean(response.body().strip());
	}

	/**
	 * @return The factory model simulated by the service, or {@code null} if it is not simulated.
	 */
	private Factory getRemoteFactory(final String factoryId)
	throws IOException, InterruptedException, URISyntaxException {
		final HttpResponse<String> response = sendRequest("/simulation/" + factoryId);

		if (response.statusCode() == HTTP_NOT_FOUND) {
			return null;
		}

		if (response.statusCode() != HTTP_OK) {
			throw new IOException("The simulation service answered " + response.statusCode() + " to " + response.uri() + ".");
		}

		return objectMapper.readValue(response.body(), Factory.class);
	}

	private HttpResponse<String> sendRequest(final String path)
	throws IOException, InterruptedException, URISyntaxException {
		// This URI constructor encodes the characters of the factory ID that are not allowed in URLs.
		final URI uri = new URI("http", null, simulationHost, simulationPort, path, null, null);
		final HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
