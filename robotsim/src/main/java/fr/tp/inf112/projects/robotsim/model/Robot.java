package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.canvas.model.impl.RGBColor;
import fr.tp.inf112.projects.robotsim.model.motion.Motion;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Robot extends Component {
	
	private static final long serialVersionUID = -1218857231970296747L;

	private static final Logger LOGGER = Logger.getLogger(Robot.class.getName());

	private static final Style STYLE = new ComponentStyle(RGBColor.GREEN, RGBColor.BLACK, 3.0f, null);

	private static final Style BLOCKED_STYLE = new ComponentStyle(RGBColor.RED, RGBColor.BLACK, 3.0f, new float[]{4.0f});

	@JsonProperty
	private final Battery battery;
	
	@JsonProperty
	private int speed;
	
	@JsonIdentityReference(alwaysAsId = true)
	@JsonProperty
	private List<Component> targetComponents;
	
	@JsonIgnore
	private transient Iterator<Component> targetComponentsIterator;
	
	@JsonIdentityReference(alwaysAsId = true)
	@JsonProperty
	private Component currTargetComponent;
	
	@JsonIgnore
	private transient Iterator<Position> currentPathPositionsIter;
	
	// Not saved with Java serialization, but sent in JSON so that remote viewers show blocked robots.
	@JsonProperty
	private transient boolean blocked;

	// Read by other robots' threads from isLivelyLocked(), so visibility across threads matters.
	private volatile Position blockedTargetPosition;

	// True while the robot is trying to step aside to a free neighbouring cell to solve a livelock.
	@JsonIgnore
	private transient boolean detouring;

	@JsonProperty
	private FactoryPathFinder pathFinder;

	// Grid position currently locked by this robot; held between behave() calls to keep other
	// robots from moving onto this robot's cell, and released as soon as this robot moves away from it.
	@JsonIgnore
	private transient Position lockedPosition;

	protected Robot() {
		this(null, null, null, null, null);
	}

	public Robot(final Factory factory,
				 final FactoryPathFinder pathFinder,
				 final CircularShape shape,
				 final Battery battery,
				 final String name ) {
		super(factory, shape, name);
		
		this.pathFinder = pathFinder;
		
		this.battery = battery;
		
		targetComponents = new ArrayList<>();
		currTargetComponent = null;
		currentPathPositionsIter = null;
		speed = 5;
		blocked = false;
		blockedTargetPosition = null;
		detouring = false;
	}

	@Override
	public String toString() {
		return super.toString() + " battery=" + battery + "]";
	}

	@JsonIgnore
	public boolean isLivelyLocked() {
		final Position blockedTargetPosition = getBlockedTargetPosition();
		if (blockedTargetPosition == null) {
			return false;
		}
		final Component otherRobot =
			getFactory().getMobileComponentAt(blockedTargetPosition, this);
		return otherRobot != null &&
			getPosition().equals(((Robot) otherRobot).getBlockedTargetPosition());
	}

	protected int getSpeed() {
		return speed;
	}

	protected void setSpeed(final int speed) {
		this.speed = speed;
	}
	
	public Position getBlockedTargetPosition() {
		return blockedTargetPosition;
	}
	
	private List<Component> getTargetComponents() {
		if (targetComponents == null) {
			targetComponents = new ArrayList<>();
		}
		
		return targetComponents;
	}
	
	public boolean addTargetComponent(final Component targetComponent) {
		return getTargetComponents().add(targetComponent);
	}
	
	public boolean removeTargetComponent(final Component targetComponent) {
		return getTargetComponents().remove(targetComponent);
	}
	
	@Override
	public boolean isMobile() {
		return true;
	}

	@Override
	public boolean behave() {
		if (getTargetComponents().isEmpty()) {
			return false;
		}
		
		if (currTargetComponent == null || hasReachedCurrentTarget()) {
			currTargetComponent = nextTargetComponentToVisit();
			
			computePathToCurrentTargetComponent();
		}
		else if (currentPathPositionsIter == null) {
			// The robot was deserialized while heading to its target, so its transient path must be recomputed.
			computePathToCurrentTargetComponent();
		}

		return moveToNextPathPosition() != 0;
	}
		
	private Component nextTargetComponentToVisit() {
		if (targetComponentsIterator == null || !targetComponentsIterator.hasNext()) {
			targetComponentsIterator = getTargetComponents().iterator();
		}
		
		return targetComponentsIterator.hasNext() ? targetComponentsIterator.next() : null;
	}

	private Position findFreeNeighbouringPosition(){
		final Position currentPosition = getPosition();
		final int x = currentPosition.getxCoordinate();
		final int y = currentPosition.getyCoordinate();
		final int x_blocked = blockedTargetPosition.getxCoordinate();
		final int y_blocked = blockedTargetPosition.getyCoordinate();

		// Move by the path grid resolution (the smallest step of the path finder and of the position locks),
		// so that the robot always lands on a grid cell and can't get stuck between two cells.
		final int step = getFactory().getPathResolution();

		//Closed operation to move in a direction to avoid collision, it might be blocked too, so process may land to on a change grid of position

		final Position positionCandidate = new Position(x + step * Integer.signum(y-y_blocked),
				                                        y + step * Integer.signum(x-x_blocked));

		if (isFreeGridPosition(positionCandidate)) {
			LOGGER.info(getName() + " has found a free neighbour after livelock to " + positionCandidate + " from " + currentPosition);
			return positionCandidate;
		}

		// Generic grid search solution (Feasible only to reallocate robots if they are stuck in a corner, not to escape from live-lock)

		final Position[] neighbouringPositions = new Position[]{
				new Position(x - step, y),
				new Position(x + step, y),
				new Position(x, y - step),
				new Position(x, y + step)
		};

		for (final Position position : neighbouringPositions) {
			if (isFreeGridPosition(position)) {
				LOGGER.info(getName() + " has found a free neighbour after livelock to " + position + " from " + currentPosition);
				return position;
			}
		}

		return null;
	}

	/**
	 * Checks that the given position is inside the factory, and that its whole grid cell is free of
	 * obstacles and of other robots, using the same cell size as the path finder.
	 */
	private boolean isFreeGridPosition(final Position position) {
		final int step = getFactory().getPathResolution();
		final int xCoordinate = position.getxCoordinate();
		final int yCoordinate = position.getyCoordinate();

		if (xCoordinate < 0 || yCoordinate < 0 ||
			xCoordinate + step > getFactory().getWidth() || yCoordinate + step > getFactory().getHeight()) {
			return false;
		}

		final PositionedShape cell = new RectangularShape(xCoordinate, yCoordinate, step, step);

		return !getFactory().hasObstacleAt(cell) && !getFactory().hasMobileComponentAt(cell, this);
	}
	
	/**
	 * Claims the lock of the robot's own starting position the first time it moves, so that no other
	 * robot can move onto this robot's cell while it hasn't started moving yet.
	 */
	private void ensureCurrentPositionLocked() {
		if (lockedPosition == null) {
			final Position currentPosition = getPosition();

			getFactory().lockPosition(currentPosition);

			lockedPosition = new Position(currentPosition.getxCoordinate(), currentPosition.getyCoordinate());
		}
	}

	private int moveToNextPathPosition() {
		ensureCurrentPositionLocked();

		final Motion motion = computeMotion();

		int displacement = motion == null ? 0 : motion.moveToTarget();

		if (displacement != 0){
			// The robot is moving again, so it is no longer blocked
			blocked = false;

			// The move succeeded: this robot now owns the lock of the target position (acquired in
			// computeMotion()), so it can safely release the lock of the position it just left.
			getFactory().unlockPosition(lockedPosition);

			lockedPosition = new Position(motion.getTargetPosition().getxCoordinate(), motion.getTargetPosition().getyCoordinate());

			// The robot stepped aside to solve a livelock: replan from its new position
			if (detouring) {
				detouring = false;
				computePathToCurrentTargetComponent();
			}

			notifyObservers();
		}
		else if (motion != null) {
			// The lock on the target position was acquired but the robot did not actually move there
			// (e.g. target equals current position): release it immediately to avoid leaking it.
			getFactory().unlockPosition(motion.getTargetPosition());
		}
		else if (detouring) {
			// The detour cell was taken in the meantime: give up this detour and replan from the current
			// position. If the livelock is still there, it will be detected again and another detour tried.
			detouring = false;
			blockedTargetPosition = null;
			computePathToCurrentTargetComponent();
		}
		else if (isLivelyLocked()) {
			final Position freeNeighbouringPosition = findFreeNeighbouringPosition();

			// If no neighbour is free (e.g. borders and other robots all around), blockedTargetPosition is
			// kept, so the livelock is checked again and solving it is retried on the next behave() tick.
			if (freeNeighbouringPosition != null) {
				blockedTargetPosition = freeNeighbouringPosition;
				detouring = true;
				displacement = moveToNextPathPosition();
			}
		}

		return displacement;
	}

	private void computePathToCurrentTargetComponent() {
		final List<Position> currentPathPositions = pathFinder.findPath(this, currTargetComponent);
		currentPathPositionsIter = currentPathPositions.iterator();

		// A path was found again, so the robot is no longer blocked
		if (!currentPathPositions.isEmpty()) {
			blocked = false;
		}
	}

	private Motion computeMotion() {
		// A memorized blocked position must be retried even if the path has no more positions
		// (e.g. the robot was waiting to enter the last cell of its path).
		if (blockedTargetPosition == null && !currentPathPositionsIter.hasNext()) {
			// The path is used up but the target was not reached (e.g. no path was found): try to
			// compute a new one, so the robot keeps retrying on every tick until a path is available.
			computePathToCurrentTargetComponent();

			if (!currentPathPositionsIter.hasNext()) {
				// There is no free path to the target
				blocked = true;

				return null;
			}
		}

		final Position targetPosition = getTargetPosition();

		// Lock the target grid cell before checking it for occupancy, so that no other robot can slip
		// into it between the check and the actual move. If another robot already holds the lock,
		// memorize the blocked target position and retry on the next behave() tick.
		if (!getFactory().tryLockPosition(targetPosition)) {
			this.blockedTargetPosition = targetPosition;

			return null;
		}

		final PositionedShape shape = new RectangularShape(targetPosition.getxCoordinate(),
														   targetPosition.getyCoordinate(),
				   										   2,
				   										   2);

		// If there is another robot already there, memorize the blocked target position for the next run
		if (getFactory().hasMobileComponentAt(shape, this)) {
			getFactory().unlockPosition(targetPosition);

			this.blockedTargetPosition = targetPosition;

			return null;
		}

		// Reset the memorized position
		this.blockedTargetPosition = null;

		return new Motion(getPosition(), targetPosition);
	}

	@Override
	protected void onSimulationStopped() {
		if (lockedPosition != null) {
			getFactory().unlockPosition(lockedPosition);

			lockedPosition = null;
		}
	}
	
	private Position getTargetPosition() {
		// If a target position was memorized, it means that the robot was blocked during the last iteration 
		// so it waited for another robot to pass. So try to move to this memorized position otherwise move to  
		// the next position from the path
		return this.blockedTargetPosition == null ? currentPathPositionsIter.next() : this.blockedTargetPosition;
	}
	
	private boolean hasReachedCurrentTarget() {
		return getPositionedShape().overlays(currTargetComponent.getPositionedShape());
	}
	
	@Override
	public boolean canBeOverlayed(final PositionedShape shape) {
		return true;
	}
	
	@Override
	public Style getStyle() {
		return blocked ? BLOCKED_STYLE : STYLE;
	}
}
