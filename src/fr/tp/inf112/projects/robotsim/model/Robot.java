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
	
	private Position blockedTargetPosition;
	
	private FactoryPathFinder pathFinder;

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
		
		if (!getFactory().hasMobileComponentAt(shapeCandidate, this)) {
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
			
			if (!getFactory().hasMobileComponentAt(shape, this)) {
				System.out.printf("%s has found a free neighbour after livelock to %s from %s\n", getName(), position, currentPosition);
				return position;
			}
		}
		
		return null;
	}
	
	private int moveToNextPathPosition() {
		final Motion motion = computeMotion();
		
		int displacement = motion == null ? 0 : motion.moveToTarget();
			
		if (displacement != 0){
			notifyObservers();
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
		final PositionedShape shape = new RectangularShape(targetPosition.getxCoordinate(),
														   targetPosition.getyCoordinate(),
				   										   2,
				   										   2);
		
		// If there is another robot, memorize the blocked target position for the next run
		if (getFactory().hasMobileComponentAt(shape, this)) {
			this.blockedTargetPosition = targetPosition;
			
			return null;
		}

		// Reset the memorized position
		this.blockedTargetPosition = null;
			
		return new Motion(getPosition(), targetPosition);
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
