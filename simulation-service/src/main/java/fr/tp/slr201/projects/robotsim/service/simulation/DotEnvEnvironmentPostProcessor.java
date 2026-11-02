package fr.tp.slr201.projects.robotsim.service.simulation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import fr.tp.inf112.projects.robotsim.config.ServiceConfig;

/**
 * Adds the variables of the {@code .env} file of the working directory to the Spring environment, with a lower
 * priority than the real environment variables but a higher one than {@code application.properties}. As for
 * environment variables, {@code SERVER_PORT} sets the {@code server.port} property.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	public static final String PROPERTY_SOURCE_NAME = "dotEnv";

	@Override
	public void postProcessEnvironment(final ConfigurableEnvironment environment,
									   final SpringApplication application) {
		final Map<String, Object> variables;

		try {
			variables = new HashMap<>(ServiceConfig.readEnvFile(Path.of(ServiceConfig.ENV_FILE)));
		}
		catch (final IOException ex) {
			throw new UncheckedIOException("Unable to read the " + ServiceConfig.ENV_FILE + " file.", ex);
		}

		if (!variables.isEmpty()) {
			environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
													  new SystemEnvironmentPropertySource(PROPERTY_SOURCE_NAME, variables));
		}
	}
}
