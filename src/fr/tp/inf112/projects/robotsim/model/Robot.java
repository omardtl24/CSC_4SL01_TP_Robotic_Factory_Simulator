package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.canvas.model.impl.RGBColor;
import fr.tp.inf112.projects.robotsim.model.motion.Motion;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Robot extends Component {
	
	private static final long serialVersionUID = -1218857231970296747L;

	private static final Style STYLE = new ComponentStyle(RGBColor.GREEN, RGBColor.BLACK, 3.0f, null);

	private static final Style BLOCKED_STYLE = new ComponentStyle(RGBColor.RED, RGBColor.BLACK, 3.0f, new float[]{4.0f});

	private final Battery battery;
	
	private int speed;
	
	private List<Component> targetComponents;
	
	private transient Iterator<Component> targetComponentsIterator;
	
	private Component currTargetComponent;
	
	private transient Iterator<Position> currentPathPositionsIter;
	
	private transient boolean blocked;

	// Read by other robots' threads from isLivelyLocked(), so visibility across threads matters.
	private volatile Position blockedTargetPosition;

	private FactoryPathFinder pathFinder;

	// Grid position currently locked by this robot; held between behave() calls to keep other
	// robots from moving onto this robot's cell, and released as soon as this robot moves away from it.
	private transient Position lockedPosition;

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
	}

	@Override
	public String toString() {
		return super.toString() + " battery=" + battery + "]";
	}

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
		final int width = ((CircularShape) getShape()).getWidth();
		final int height = ((CircularShape) getShape()).getHeight();
		
		//Closed operation to move in a direction to avoid collision, it might be blocked too, so process may land to on a change grid of position
		
		final Position positionCandidate = new Position(x + width * Integer.signum(y-y_blocked),
				                                        y + height * Integer.signum(x-x_blocked));
		
		
		final PositionedShape shapeCandidate = new RectangularShape(positionCandidate.getxCoordinate(),
				positionCandidate.getyCoordinate(),
				                                           2,
				                                           2);
		
		if (!getFactory().hasObstacleAt(shapeCandidate)) {
			System.out.printf("%s has found a free neighbour after livelock to %s from %s\n", getName(), positionCandidate, currentPosition);
			return positionCandidate;
		}
		
		// Generic grid search solution (Feasible only to reallocate robots if they are stuck in a corner, not to escape from live-lock)
		
		final Position[] neighbouringPositions = new Position[]{
				new Position(x - width, y),
				new Position(x + width, y),
				new Position(x, y - height),
				new Position(x, y + height)
		};
		
		for (final Position position : neighbouringPositions) {
			final PositionedShape shape = new RectangularShape(position.getxCoordinate(),
															   position.getyCoordinate(),
															   2,
															   2);
			
			if (!getFactory().hasObstacleAt(shape)) {
				System.out.printf("%s has found a free neighbour after livelock to %s from %s\n", getName(), position, currentPosition);
				return position;
			}
		}
		
		return null;
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
			// The move succeeded: this robot now owns the lock of the target position (acquired in
			// computeMotion()), so it can safely release the lock of the position it just left.
			getFactory().unlockPosition(lockedPosition);

			lockedPosition = new Position(motion.getTargetPosition().getxCoordinate(), motion.getTargetPosition().getyCoordinate());

			notifyObservers();
		}
		else if (motion != null) {
			// The lock on the target position was acquired but the robot did not actually move there
			// (e.g. target equals current position): release it immediately to avoid leaking it.
			getFactory().unlockPosition(motion.getTargetPosition());
		}
		else if (isLivelyLocked()) {
			final Position freeNeighbouringPosition = findFreeNeighbouringPosition();
			if (freeNeighbouringPosition != null) {
				blockedTargetPosition = freeNeighbouringPosition;
				displacement = moveToNextPathPosition();
				computePathToCurrentTargetComponent();
			}
		}

		return displacement;
	}

	private void computePathToCurrentTargetComponent() {
		final List<Position> currentPathPositions = pathFinder.findPath(this, currTargetComponent);
		currentPathPositionsIter = currentPathPositions.iterator();
	}

	private Motion computeMotion() {
		if (!currentPathPositionsIter.hasNext()) {

			// There is no free path to the target
			blocked = true;

			return null;
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
