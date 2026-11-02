package fr.tp.slr201.projects.robotsim.service.simulation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;

import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.json.FactoryObjectMapper;
import fr.tp.inf112.projects.robotsim.persistence.PersistenceClient;
import fr.tp.inf112.projects.robotsim.persistence.RemoteFactoryPersistenceManager;

@Configuration
public class SimulationServiceConfig {

	/**
	 * Replaces the object mapper of Spring so that the factory models are converted to JSON text the same way
	 * as by the clients of the service.
	 */
	@Bean
	@Primary
	public ObjectMapper objectMapper() {
		return FactoryObjectMapper.create();
	}

	/**
	 * Reads the factory models to simulate from the persistence server.
	 */
	@Bean
	public CanvasPersistenceManager persistenceManager(@Value("${persistence.host}") final String persistenceHost,
													   @Value("${persistence.port}") final int persistencePort) {
		return new RemoteFactoryPersistenceManager(null, new PersistenceClient(persistenceHost, persistencePort));
	}
}
