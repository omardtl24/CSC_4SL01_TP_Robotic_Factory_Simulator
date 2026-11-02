package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import fr.tp.inf112.projects.robotsim.TestConditions;
import fr.tp.inf112.projects.robotsim.json.FactoryObjectMapper;
import fr.tp.inf112.projects.robotsim.model.Factory;

/**
 * Simulation service with the same REST methods as the {@code SimulationController} of the simulation-service module,
 * running on the HTTP server of the JDK so that the remote controller can be tested without Spring.
 */
class FakeSimulationService implements AutoCloseable {

	private static final String START_PATH = "/simulation/start/";

	private static final String STOP_PATH = "/simulation/stop/";

	private static final String GET_PATH = "/simulation/";

	private final HttpServer server;

	private final ExecutorService executor;

	private final ObjectMapper objectMapper = FactoryObjectMapper.create();

	/**
	 * Plays the role of the persistence server: returns the factory of an ID, or {@code null} if there is none.
	 */
	private final Function<String, Factory> factoryReader;

	private final Map<String, Factory> simulatedFactories = new ConcurrentHashMap<>();

	private final Map<String, Thread> simulationThreads = new ConcurrentHashMap<>();

	private final List<String> startedFactoryIds = new CopyOnWriteArrayList<>();

	private final AtomicInteger startRequestCount = new AtomicInteger();

	private final AtomicInteger getRequestCount = new AtomicInteger();

	private boolean closed;

	FakeSimulationService(final Function<String, Factory> factoryReader)
	throws IOException {
		this.factoryReader = factoryReader;

		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		executor = Executors.newCachedThreadPool();
		server.setExecutor(executor);
		server.createContext(GET_PATH, this::handle);
		server.start();
	}

	int getPort() {
		return server.getAddress().getPort();
	}

	private void handle(final HttpExchange exchange)
	throws IOException {
		// The path is decoded, as the factory IDs received by Spring.
		final String path = exchange.getRequestURI().getPath();

		try {
			if (path.startsWith(START_PATH)) {
				send(exchange, 200, String.valueOf(start(path.substring(START_PATH.length()))));
			}
			else if (path.startsWith(STOP_PATH)) {
				send(exchange, 200, String.valueOf(stop(path.substring(STOP_PATH.length()))));
			}
			else {
				final Factory factory = simulatedFactories.get(path.substring(GET_PATH.length()));

				if (factory == null) {
					exchange.sendResponseHeaders(404, -1);
				}
				else {
					getRequestCount.incrementAndGet();
					send(exchange, 200, objectMapper.writeValueAsString(factory));
				}
			}
		}
		catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		finally {
			exchange.close();
		}
	}

	private static void send(final HttpExchange exchange,
							 final int status,
							 final String body)
	throws IOException {
		final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);

		try (final OutputStream outStream = exchange.getResponseBody()) {
			outStream.write(bytes);
		}
	}

	private boolean start(final String factoryId)
	throws InterruptedException {
		startRequestCount.incrementAndGet();
		startedFactoryIds.add(factoryId);

		if (simulatedFactories.containsKey(factoryId)) {
			return true;
		}

		final Factory factory = factoryReader.apply(factoryId);

		if (factory == null) {
			return false;
		}

		simulatedFactories.put(factoryId, factory);
		simulationThreads.put(factoryId, TestConditions.startSimulation(factory));

		return true;
	}

	private boolean stop(final String factoryId)
	throws InterruptedException {
		final Factory factory = simulatedFactories.remove(factoryId);

		if (factory == null) {
			return false;
		}

		TestConditions.stopSimulation(factory, simulationThreads.remove(factoryId));

		return true;
	}

	/**
	 * Stops a simulation on the service side, as if the service had been restarted.
	 */
	void endSimulation(final String factoryId)
	throws InterruptedException {
		stop(factoryId);
	}

	boolean isSimulated(final String factoryId) {
		return simulatedFactories.containsKey(factoryId);
	}

	Factory getSimulatedFactory(final String factoryId) {
		return simulatedFactories.get(factoryId);
	}

	List<String> getStartedFactoryIds() {
		return startedFactoryIds;
	}

	int getStartRequestCount() {
		return startRequestCount.get();
	}

	int getGetRequestCount() {
		return getRequestCount.get();
	}

	/**
	 * Stops the simulations and the server. Can be called several times.
	 */
	@Override
	public synchronized void close()
	throws InterruptedException {
		if (closed) {
			return;
		}

		closed = true;

		for (final String factoryId : simulatedFactories.keySet()) {
			stop(factoryId);
		}

		server.stop(0);
		executor.shutdownNow();
	}
}
