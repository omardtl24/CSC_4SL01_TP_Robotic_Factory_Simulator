package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnore;

import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Room extends Component {
	
	private static final long serialVersionUID = 1449569724908316962L;

	public static enum WALL {LEFT, TOP, RIGHT, BOTTOM};
	
	private static final int WALL_THICKNESS = 5;
	
	// Derived from the shape of the room, so built on first use since the shape is only known after
	// deserialization when the room is created by Jackson.
	@JsonIgnore
	private transient volatile PositionedShape[] walls;
	
	@JsonIdentityReference(alwaysAsId = true)
	private final List<Area> areas;

	@JsonIdentityReference(alwaysAsId = true)
	private final List<Door> doors;

	protected Room() {
		this(null, null, null);
	}

	public Room(final Factory factory,
				final RectangularShape shape,
				final String name) {
		super(factory, shape, name);
		
		walls = null;
		areas = new ArrayList<>();
		doors = new ArrayList<>();
	}
	
	private PositionedShape[] getWalls() {
		if (walls == null) {
			walls = new PositionedShape[] {
				new RectangularShape(getxCoordinate(), getyCoordinate(), WALL_THICKNESS, getHeight() + WALL_THICKNESS),
				new RectangularShape(getxCoordinate() + getWidth(), getyCoordinate(), WALL_THICKNESS, getHeight() + WALL_THICKNESS),
				new RectangularShape(getxCoordinate(), getyCoordinate(), getWidth(), WALL_THICKNESS),
				new RectangularShape(getxCoordinate(), getyCoordinate() + getHeight(), getWidth(), WALL_THICKNESS)
			};
		}
		
		return walls;
	}
	
	private boolean wallsOverlay(final PositionedShape shape) {
		for (final PositionedShape wall : getWalls()) {
			if (wall.overlays(shape)) {
				return true;
			}
		}
		
		return false;
	}
	
	protected boolean addArea(final Area area) {
		return areas.add(area);
	}
	
	protected boolean addDoor(final Door door) {
		return doors.add(door);
	}
	
	public List<Area> getAreas() {
		return areas;
	}

	public List<Door> getDoors() {
		return doors;
	}

	@Override
	public boolean overlays(final PositionedShape shape) {
		return wallsOverlay(shape);
	}

	@Override
	public boolean canBeOverlayed(final PositionedShape shape) {
		final Door overlayedDoor = getOverlayedDoor(shape);
		
		if (overlayedDoor != null) {
			return overlayedDoor.canBeOverlayed(shape);
		}
		
		return !wallsOverlay(shape);
	}
	
	private Door getOverlayedDoor(final PositionedShape shape) {
		for (final Door door : getDoors()) {
			if (door.overlays(shape)) {
				return door;
			}
		}
		
		return null;
	}

	@Override
	public String toString() {
		return super.toString() + " areas=" + areas + "]";
	}
}
