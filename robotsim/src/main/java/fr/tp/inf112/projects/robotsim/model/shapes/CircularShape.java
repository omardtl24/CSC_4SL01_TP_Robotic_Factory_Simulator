package fr.tp.inf112.projects.robotsim.model.shapes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.tp.inf112.projects.canvas.model.OvalShape;

public class CircularShape extends PositionedShape implements OvalShape {
	
	private static final long serialVersionUID = -1912941556210518344L;

	@JsonProperty
	private final int radius;
	
	protected CircularShape() {
		this(0, 0, 0);
	}
	
	public CircularShape( 	final int xCoordinate,
							final int yCoordinate,
							final int radius ) {
		super( xCoordinate, yCoordinate );
		
		this.radius = radius;
	}

	@JsonIgnore
	@Override
	public int getWidth() {
		return 2 * radius;
	}

	@JsonIgnore
	@Override
	public int getHeight() {
		return getWidth();
	}

	@Override
	public String toString() {
		return super.toString() + " [radius=" + radius + "]";
	}
}
