package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.tp.inf112.projects.canvas.controller.Observable;
import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.Figure;
import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Factory extends Component implements Canvas, Observable {

	private static final long serialVersionUID = 5156526483612458192L;

	private static final Logger LOGGER = Logger.getLogger(Factory.class.getName());
	
	private static final ComponentStyle DEFAULT = new ComponentStyle(5.0f);

	@JsonManagedReference("factory-components")
    private final List<Component> components;

	@JsonIgnore
	private transient List<Observer> observers;

	// Not saved with Java serialization, but sent in JSON so that remote viewers know if the simulation runs.
	// Volatile since it is read by the threads of the mobile components to know when to stop.
	@JsonProperty
	private transient volatile boolean simulationStarted;
	
	private int pathResolution;

	// Built on first use from the size and path resolution of the factory, which are only known after
	// deserialization when the factory is created by Jackson or read from a file.
	@JsonIgnore
	private transient ReentrantLock[][] positionLocks;

	protected Factory() {
		this(0, 0, null, 1);
	}

	public Factory(final int width,
				   final int height,
				   final String name,
				   final int pathResolution) {
		super(null, new RectangularShape(0, 0, width, height), name);
		
		this.pathResolution = pathResolution;
		components = new ArrayList<>();
		observers = null;
		simulationStarted = false;
		positionLocks = null;
	}
	
	protected List<Observer> getObservers() {
		if (observers == null) {
			observers = new ArrayList<>();
		}
		
		return observers;
	}

	@Override
	public boolean addObserver(Observer observer) {
		return getObservers().add(observer);
	}

	@Override
	public boolean removeObserver(Observer observer) {
		return getObservers().remove(observer);
	}
	
	protected void notifyObservers() {
		for (final Observer observer : getObservers()) {
			observer.modelChanged();
		}
	}
	
	public boolean addComponent(final Component component) {
		if (components.add(component)) {
			notifyObservers();
			
			return true;
		}
		
		return false;
	}

	public boolean removeComponent(final Component component) {
		if (components.remove(component)) {
			notifyObservers();
			
			return true;
		}
		
		return false;
	}

	protected List<Component> getComponents() {
		return components;
	}

	@JsonIgnore
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Collection<Figure> getFigures() {
		return (Collection) components;
	}
	
	public int getPathResolution() {
		return pathResolution;
	}

	private synchronized ReentrantLock[][] getPositionLocks() {
		if (positionLocks == null) {
			positionLocks = new ReentrantLock[getWidth() / pathResolution][getHeight() / pathResolution];

			for (int i = 0; i < positionLocks.length; i++) {
				for (int j = 0; j < positionLocks[i].length; j++) {
					positionLocks[i][j] = new ReentrantLock();
				}
			}
		}

		return positionLocks;
	}

	private ReentrantLock getPositionLock(final Position position) {
		final int xIndex = position.getxCoordinate() / pathResolution;
		final int yIndex = position.getyCoordinate() / pathResolution;

		return getPositionLocks()[xIndex][yIndex];
	}

	/**
	 * Blocks the calling thread until the lock guarding the grid cell of the given position is acquired.
	 */
	public void lockPosition(final Position position) {
		getPositionLock(position).lock();
	}

	/**
	 * Attempts to acquire the lock guarding the grid cell of the given position without waiting.
	 *
	 * @return true if the lock was acquired, false if another robot is currently holding it.
	 */
	public boolean tryLockPosition(final Position position) {
		return getPositionLock(position).tryLock();
	}

	public void unlockPosition(final Position position) {
		getPositionLock(position).unlock();
	}

	public void setPathResolution(int pathResolution) {
		this.pathResolution = pathResolution;
	}

	@Override
	public String toString() {
		return super.toString() + " components=" + components + "]";
	}
	
	public boolean isSimulationStarted() {
		return simulationStarted;
	}

	public void startSimulation() {
		if (!isSimulationStarted()) {
			this.simulationStarted = true;
			notifyObservers();
            // Remove loop and call behave() on each component in a separate thread to avoid blocking the main thread
			behave();
		}
	}

	public void stopSimulation() {
		if (isSimulationStarted()) {
			this.simulationStarted = false;
			
			notifyObservers();
		}
	}

	@Override
	public boolean behave() {

		boolean behaved = true;
		ArrayList<Thread> threads = new ArrayList<>();

		//Build threads for each mobile component to behave in parallel
		for (int i = 0; i < getComponents().size(); i++) {
			final Component component = getComponents().get(i);
			if (component.isMobile()) {
				threads.add(new Thread(component));
			}

		}
		//Start all threads
		for (Thread thread : threads) {
			thread.start();
		}
		//Wait for all threads to finish
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				LOGGER.log(Level.SEVERE, "Interrupted while waiting for the components threads to finish.", e);
				behaved = false;
			}
		}
		
		return behaved;
	}
	
	@Override
	public Style getStyle() {
		return DEFAULT;
	}
	
	public boolean hasObstacleAt(final PositionedShape shape) {
		for (final Component component : getComponents()) {
			if (component.overlays(shape) && !component.canBeOverlayed(shape)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean hasMobileComponentAt(final PositionedShape shape,
										final Component movingComponent) {
		for (final Component component : getComponents()) {
			if (component != movingComponent && component.isMobile() && component.overlays(shape)) {
				return true;
			}
		}
		
		return false;
	}
	
	public Component getMobileComponentAt(	final Position position,
											final Component ignoredComponent) {
		if (position == null) {
			return null;
		}
		
		return getMobileComponentAt(new RectangularShape(position.getxCoordinate(), position.getyCoordinate(), 2, 2), ignoredComponent);
	}
	
	public Component getMobileComponentAt(	final PositionedShape shape,
											final Component ignoredComponent) {
		if (shape == null) {
			return null;
		}
		
		for (final Component component : getComponents()) {
			if (component != ignoredComponent && component.isMobile() && component.overlays(shape)) {
				return component;
			}
		}
		
		return null;
	}
}
