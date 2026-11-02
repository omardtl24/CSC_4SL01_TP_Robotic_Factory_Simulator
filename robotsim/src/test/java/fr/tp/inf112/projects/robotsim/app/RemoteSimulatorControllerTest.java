package fr.tp.inf112.projects.robotsim.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.robotsim.TestConditions;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Position;
import fr.tp.inf112.projects.robotsim.model.TestFactories;

/**
 * Checks that the remote controller drives the simulation service and displays the model it simulates, using a
 * {@link FakeSimulationService} instead of the simulation-service module.
 */
class RemoteSimulatorControllerTest {

	/**
	 * Contains a space, which must be encoded in the URLs of the REST methods.
	 */
	private static final String FACTORY_ID = "remote test.factory";

	private FakeSimulationService service;

	private Factory localFactory;

	private RemoteSimulatorController controller;

	private final AtomicInteger viewerNotificationCount = new AtomicInteger();

	/**
	 * Plays the role of the canvas viewer, which registers itself as an observer through the controller.
	 */
	private final Observer viewer = viewerNotificationCount::incrementAndGet;

	@BeforeEach
	void startService()
	throws Exception {
		service = new FakeSimulationService(factoryId -> FACTORY_ID.equals(factoryId) ? TestFactories.createFactory(FACTORY_ID) : null);
		localFactory = TestFactories.createFactory(FACTORY_ID);
		controller = new RemoteSimulatorController(localFactory, null, "localhost", service.getPort());
		controller.addObserver(viewer);
	}

	@AfterEach
	void stopService()
	throws Exception {
		controller.stopAnimation();
		service.close();
	}

	private Factory displayedFactory() {
		return (Factory) controller.getCanvas();
	}

	private void waitUntilDisplayedFromService()
	throws InterruptedException {
		TestConditions.waitUntil("the model of the service is displayed", () -> displayedFactory() != localFactory);
	}

	@Test
	void setCanvasMovesTheObserversToTheNewModel() {
		final Factory newFactory = TestFactories.createFactory(FACTORY_ID);

		controller.setCanvas(newFactory);

		assertThat(controller.getCanvas()).isSameAs(newFactory);
		assertThat(newFactory.getObservers()).containsExactly(viewer);
		assertThat(viewerNotificationCount).hasValue(1);
	}

	@Test
	void setCanvasDoesNotDuplicateTheObservers() {
		final Factory newFactory = TestFactories.createFactory(FACTORY_ID);
		newFactory.addObserver(viewer);

		controller.setCanvas(newFactory);

		assertThat(newFactory.getObservers()).containsExactly(viewer);
	}

	@Test
	void setCanvasAcceptsNoModel() {
		final RemoteSimulatorController emptyController = new RemoteSimulatorController(null, null, "localhost", service.getPort());

		emptyController.setCanvas(null);
		emptyController.setCanvas(localFactory);

		assertThat(emptyController.getCanvas()).isSameAs(localFactory);
	}

	@Test
	void startSimulatesTheFactoryOnTheService()
	throws Exception {
		controller.startAnimation();

		assertThat(service.isSimulated(FACTORY_ID)).isTrue();
		assertThat(service.getStartedFactoryIds()).containsExactly(FACTORY_ID);
		assertThat(service.getSimulatedFactory(FACTORY_ID).isSimulationStarted()).isTrue();
	}

	@Test
	void displaysTheModelSimulatedByTheService()
	throws Exception {
		final List<Position> initialPositions = TestFactories.robotPositions(localFactory);

		controller.startAnimation();
		waitUntilDisplayedFromService();

		assertThat(controller.isAnimationRunning()).isTrue();
		assertThat(displayedFactory().getObservers()).containsExactly(viewer);

		TestConditions.waitUntil("the displayed robots have moved",
								 () -> !TestFactories.robotPositions(displayedFactory()).equals(initialPositions));

		// The simulation runs on the service only.
		assertThat(TestFactories.robotPositions(localFactory)).isEqualTo(initialPositions);
		assertThat(localFactory.isSimulationStarted()).isFalse();
	}

	@Test
	void viewerIsNotifiedOnEachUpdate()
	throws Exception {
		controller.startAnimation();

		TestConditions.waitUntil("the viewer has been notified of several updates",
								 () -> viewerNotificationCount.get() >= 5 && service.getGetRequestCount() >= 5);
	}

	@Test
	void stopEndsTheSimulationAndTheUpdates()
	throws Exception {
		controller.startAnimation();
		waitUntilDisplayedFromService();

		controller.stopAnimation();
		final int getRequestCount = service.getGetRequestCount();

		assertThat(service.isSimulated(FACTORY_ID)).isFalse();
		assertThat(controller.isAnimationRunning()).isFalse();
		assertThat(displayedFactory().getObservers()).containsExactly(viewer);

		// Longer than a few update periods: no request may be sent once stopped.
		Thread.sleep(500);
		assertThat(service.getGetRequestCount()).isEqualTo(getRequestCount);
	}

	@Test
	void startingTwiceUsesASingleSimulation()
	throws Exception {
		controller.startAnimation();
		controller.startAnimation();
		waitUntilDisplayedFromService();

		assertThat(service.getStartRequestCount()).isEqualTo(1);
	}

	@Test
	void canBeRestartedAfterAStop()
	throws Exception {
		controller.startAnimation();
		waitUntilDisplayedFromService();
		controller.stopAnimation();

		final Factory stoppedFactory = displayedFactory();
		controller.startAnimation();

		TestConditions.waitUntil("a new model of the service is displayed", () -> displayedFactory() != stoppedFactory);
		assertThat(controller.isAnimationRunning()).isTrue();
		assertThat(service.getStartRequestCount()).isEqualTo(2);
	}

	@Test
	void simulationEndedByTheServiceStopsTheUpdates()
	throws Exception {
		controller.startAnimation();
		waitUntilDisplayedFromService();

		service.endSimulation(FACTORY_ID);

		TestConditions.waitUntil("the animation is reported as stopped", () -> !controller.isAnimationRunning());
		assertThat(displayedFactory().getObservers()).containsExactly(viewer);
	}

	@Test
	void factoryUnknownToTheServiceIsNotDisplayed()
	throws Exception {
		final Factory unknownFactory = TestFactories.createFactory("unknown.factory");
		final RemoteSimulatorController unknownController = new RemoteSimulatorController(unknownFactory, null, "localhost", service.getPort());

		unknownController.startAnimation();

		assertThat(service.getStartedFactoryIds()).containsExactly("unknown.factory");
		assertThat(unknownController.isAnimationRunning()).isFalse();
		assertThat(unknownController.getCanvas()).isSameAs(unknownFactory);
		assertThat(service.getGetRequestCount()).isZero();
	}

	@Test
	void unsavedFactoryIsNotSentToTheService()
	throws Exception {
		final Factory unsavedFactory = TestFactories.createFactory(null);
		final RemoteSimulatorController unsavedController = new RemoteSimulatorController(unsavedFactory, null, "localhost", service.getPort());

		unsavedController.startAnimation();
		unsavedController.stopAnimation();

		assertThat(service.getStartRequestCount()).isZero();
		assertThat(unsavedController.isAnimationRunning()).isFalse();
	}

	@Test
	void unreachableServiceIsReported()
	throws Exception {
		final int port = service.getPort();
		service.close();

		final RemoteSimulatorController unreachableController = new RemoteSimulatorController(localFactory, null, "localhost", port);
		unreachableController.startAnimation();
		unreachableController.stopAnimation();

		assertThat(unreachableController.isAnimationRunning()).isFalse();
		assertThat(unreachableController.getCanvas()).isSameAs(localFactory);
	}
}
