package fr.tp.inf112.projects.robotsim.model.shapes;

import java.util.LinkedHashSet;
import java.util.Set;

import fr.tp.inf112.projects.canvas.model.Point;
import fr.tp.inf112.projects.canvas.model.PolygonShape;

public class BasicPolygonShape extends PositionedShape implements PolygonShape {
	
	private static final long serialVersionUID = -1764316101910546849L;

	private final Set<Point> points;
	
	public BasicPolygonShape() {
		super(0, 0);
		
		this.points = new LinkedHashSet<>();
	}
	
	@Override
	public Set<Point> getPoints() {
		return points;
	}
	
	public boolean addPoint(final Point point) {
		final boolean added = getPoints().add(point);
		
		updatePosition();
		
		return added;
	}
	
	private void updatePosition() {
		int minxCoordinate = Integer.MAX_VALUE;
		int minyCoordinate = minxCoordinate;
		
		for (final Point point : getPoints()) {
			minxCoordinate = Math.min(minxCoordinate, point.getxCoordinate());
			minyCoordinate = Math.min(minyCoordinate, point.getyCoordinate());
		}
		
		setxCoordinate(minxCoordinate);
		setyCoordinate(minyCoordinate);
	}
	
	@Override
	public int getWidth() {
		int minCoordinate = Integer.MAX_VALUE;
		int maxCoordinate = 0;
		
		for (final Point point : getPoints()) {
			final int coordinate = point.getxCoordinate();

			minCoordinate = Math.min(minCoordinate, coordinate);
			maxCoordinate = Math.max(maxCoordinate, coordinate);
		}
		
		return maxCoordinate - minCoordinate;
	}

	@Override
	public int getHeight() {
		int minCoordinate = Integer.MAX_VALUE;
		int maxCoordinate = 0;
		
		for (final Point point : getPoints()) {
			final int coordinate = point.getyCoordinate();

			minCoordinate = Math.min(minCoordinate, coordinate);
			maxCoordinate = Math.max(maxCoordinate, coordinate);
		}
		
		return maxCoordinate - minCoordinate;
	}
}
