package fr.tp.inf112.projects.robotsim.json;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import fr.tp.inf112.projects.canvas.model.impl.BasicPoint;
import fr.tp.inf112.projects.graph.impl.GridVertex;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;

/**
 * Creates the Jackson object mappers converting the factory models to and from JSON text. The simulation
 * microservice and its clients must use the same configuration so that the type information written for the
 * polymorphic attributes (e.g. the components of a factory) can be read back.
 */
public final class FactoryObjectMapper {

	private FactoryObjectMapper() {
	}

	public static ObjectMapper create() {
		final PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
			.allowIfSubType(PositionedShape.class.getPackageName())
			.allowIfSubType(Component.class.getPackageName())
			.allowIfSubType(GridVertex.class.getPackageName())
			// Points of the polygon shapes.
			.allowIfSubType(BasicPoint.class.getPackageName())
			.allowIfSubType(ArrayList.class.getName())
			.allowIfSubType(LinkedHashSet.class.getName())
			.build();

		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);

		return objectMapper;
	}
}
