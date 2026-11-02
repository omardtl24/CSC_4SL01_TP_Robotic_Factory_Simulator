package fr.tp.slr201.projects.robotsim.service.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.canvas.model.impl.AbstractCanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.json.FactoryObjectMapper;
import fr.tp.inf112.projects.robotsim.model.Factory;

/**
 * Checks the REST methods of the simulation service, and that the JSON text they send back can be parsed by the
 * clients of the service with {@link FactoryObjectMapper}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimulationControllerTest {

	private static final String UNKNOWN_FACTORY_ID = "unknown.factory";

	/**
	 * Replaces the persistence server, so that the tests do not need it to be running.
	 */
	@TestConfiguration
	static class InMemoryPersistenceConfig {

		@Bean
		@Primary
		CanvasPersistenceManager inMemoryPersistenceManager() {
			return new AbstractCanvasPersistenceManager(null) {

				@Override
				public Canvas read(final String canvasId)
				throws IOException {
					if (TestFactories.FACTORY_ID.equals(canvasId)) {
						return TestFactories.createFactory();
					}

					throw new IOException("No factory named " + canvasId + ".");
				}

				@Override
				public void persist(final Canvas canvasModel) {
				}

				@Override
				public boolean delete(final Canvas canvasModel) {
					return false;
				}
			};
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void simulatedFactoryIsSentAsJson()
	throws Exception {
		mockMvc.perform(get("/simulation/start/{factoryId}", TestFactories.FACTORY_ID))
			.andExpect(status().isOk())
			.andExpect(content().string("true"));

		try {
			final String json = mockMvc.perform(get("/simulation/{factoryId}", TestFactories.FACTORY_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

			final Factory factory = FactoryObjectMapper.create().readValue(json, Factory.class);
			final Factory expectedFactory = TestFactories.createFactory();

			assertThat(factory.getId()).isEqualTo(TestFactories.FACTORY_ID);
			assertThat(factory.getName()).isEqualTo(expectedFactory.getName());
			assertThat(factory.getFigures()).hasSameSizeAs(expectedFactory.getFigures());
			assertThat(factory.isSimulationStarted()).isTrue();
		}
		finally {
			mockMvc.perform(get("/simulation/stop/{factoryId}", TestFactories.FACTORY_ID))
				.andExpect(status().isOk())
				.andExpect(content().string("true"));
		}

		mockMvc.perform(get("/simulation/{factoryId}", TestFactories.FACTORY_ID))
			.andExpect(status().isNotFound());
	}

	@Test
	void startingAnUnknownFactoryFails()
	throws Exception {
		mockMvc.perform(get("/simulation/start/{factoryId}", UNKNOWN_FACTORY_ID))
			.andExpect(status().isOk())
			.andExpect(content().string("false"));
	}

	@Test
	void unknownFactoryIsNotFound()
	throws Exception {
		mockMvc.perform(get("/simulation/{factoryId}", UNKNOWN_FACTORY_ID))
			.andExpect(status().isNotFound());
	}

	@Test
	void stoppingAnUnknownFactoryFails()
	throws Exception {
		mockMvc.perform(get("/simulation/stop/{factoryId}", UNKNOWN_FACTORY_ID))
			.andExpect(status().isOk())
			.andExpect(content().string("false"));
	}
}
