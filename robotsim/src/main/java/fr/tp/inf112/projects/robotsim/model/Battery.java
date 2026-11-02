package fr.tp.inf112.projects.robotsim.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Battery implements Serializable {
	
	private static final long serialVersionUID = 5744149485828674046L;

	@JsonProperty
	private final float capacity;
	
	@JsonProperty
	private float level;

	protected Battery() {
		this(0);
	}

	public Battery(final float capacity) {
		this.capacity = capacity;
		level = capacity;
	}
	
	public float consume(float energy) {
		level-= energy;
		
		return level;
	}
	
	public float charge(float energy) {
		level+= energy;
		
		return level;
	}

	@Override
	public String toString() {
		return "Battery [capacity=" + capacity + "]";
	}
}
