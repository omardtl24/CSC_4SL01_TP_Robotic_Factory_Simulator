package fr.tp.inf112.projects.robotsim.model;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import fr.tp.inf112.projects.canvas.model.Figure;
import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.canvas.model.Shape;

/**
 * Components are referenced from several places of the model (the factory, rooms, areas, robot targets...), so
 * in JSON each one is only written once, in the components of its factory, and referred to by its {@code @id}
 * everywhere else.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public abstract class Component implements Figure, Serializable, Runnable {
	
	private static final long serialVersionUID = -5960950869184030220L;

	private static final Logger LOGGER = Logger.getLogger(Component.class.getName());

	private String id;

	@JsonBackReference("factory-components")
	private final Factory factory;
	
	private final PositionedShape positionedShape;
	
	private final String name;

	protected Component() {
		this(null, null, null);
	}

	protected Component(final Factory factory,
						final PositionedShape shape,
						final String name) {
		this.factory = factory;
		this.positionedShape = shape;
		this.name = name;

		if (factory != null) {
			factory.addComponent(this);
		}
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public PositionedShape getPositionedShape() {
		return positionedShape;
	}
	
	@JsonIgnore
	public Position getPosition() {
		final PositionedShape shape = getPositionedShape();

		return shape == null ? null : shape.getPosition();
	}

	protected Factory getFactory() {
		return factory;
	}

	@JsonIgnore
	@Override
	public int getxCoordinate() {
		final PositionedShape shape = getPositionedShape();

		return shape == null ? -1 : shape.getxCoordinate();
	}

	protected boolean setxCoordinate(int xCoordinate) {
		if ( getPositionedShape().setxCoordinate( xCoordinate ) ) {
			notifyObservers();
			
			return true;
		}
		
		return false;
	}

	@JsonIgnore
	@Override
	public int getyCoordinate() {
		final PositionedShape shape = getPositionedShape();

		return shape == null ? -1 : shape.getyCoordinate();
	}

	protected boolean setyCoordinate(final int yCoordinate) {
		if (getPositionedShape().setyCoordinate(yCoordinate) ) {
			notifyObservers();
			
			return true;
		}
		
		return false;
	}

	protected void notifyObservers() {
		final Factory factory = getFactory();

		if (factory != null) {
			factory.notifyObservers();
		}
	}

	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + " [name=" + name + " xCoordinate=" + getxCoordinate() + ", yCoordinate=" + getyCoordinate()
				+ ", shape=" + getPositionedShape();
	}

	@JsonIgnore
	public int getWidth() {
		final PositionedShape shape = getPositionedShape();

		return shape == null ? -1 : shape.getWidth();
	}

	@JsonIgnore
	public int getHeight() {
		final PositionedShape shape = getPositionedShape();

		return shape == null ? -1 : shape.getHeight();
	}
	
	public boolean behave() {
		return false;
	}
	
	@JsonIgnore
	public boolean isMobile() {
		return false;
	}
	
	public boolean overlays(final Component component) {
		return overlays(component.getPositionedShape());
	}
	
	public boolean overlays(final PositionedShape shape) {
		return getPositionedShape().overlays(shape);
	}
	
	public boolean canBeOverlayed(final PositionedShape shape) {
		return false;
	}
	
	@JsonIgnore
	@Override
	public Style getStyle() {
		return ComponentStyle.DEFAULT;
	}
	
	@JsonIgnore
	@Override
	public Shape getShape() {
		return getPositionedShape();
	}
	
	@JsonIgnore
	public boolean isSimulationStarted() {
		final Factory factory = getFactory();

		return factory != null && factory.isSimulationStarted();
	}

	@Override
	public void run() {
		try {
			while (isSimulationStarted()) {
				behave();
				Thread.sleep(50);
			}
		} catch (InterruptedException e) {
			LOGGER.log(Level.SEVERE, "Simulation thread of " + getName() + " was interrupted.", e);
		} finally {
			onSimulationStopped();
		}
	}

	/**
	 * Called on this component's own worker thread right before it terminates because the simulation
	 * was stopped. Subclasses that acquire thread-owned resources (e.g. locks) during behave() should
	 * release them here, since that is guaranteed to still run on the owning thread.
	 */
	protected void onSimulationStopped() {
	}
}
