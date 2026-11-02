package fr.tp.inf112.projects.robotsim;

import static org.assertj.core.api.Assertions.fail;

import java.util.function.BooleanSupplier;

import fr.tp.inf112.projects.robotsim.model.Factory;

/**
 * Waits for the conditions depending on other threads (simulations, viewer updates), instead of sleeping for a fixed
 * time, so that the tests are both fast and reliable on slow machines.
 */
public final class TestConditions {

	public static final long TIMEOUT_MS = 10_000;

	private static final long POLL_PERIOD_MS = 20;

	private TestConditions() {
	}

	public static void waitUntil(final String description,
								 final BooleanSupplier condition)
	throws InterruptedException {
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;

		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				fail("Timed out after " + TIMEOUT_MS + " ms waiting until " + description + ".");
			}

			Thread.sleep(POLL_PERIOD_MS);
		}
	}

	/**
	 * Starts the simulation of a factory in a new thread, since {@code Factory.startSimulation()} only returns when
	 * the simulation is stopped, and waits until it is marked as started.
	 */
	public static Thread startSimulation(final Factory factory)
	throws InterruptedException {
		final Thread simulationThread = new Thread(factory::startSimulation, "test-simulation-" + factory.getId());
		simulationThread.start();
		waitUntil("the simulation is started", factory::isSimulationStarted);

		return simulationThread;
	}

	public static void stopSimulation(final Factory factory,
									  final Thread simulationThread)
	throws InterruptedException {
		factory.stopSimulation();
		simulationThread.join(TIMEOUT_MS);
	}
}
