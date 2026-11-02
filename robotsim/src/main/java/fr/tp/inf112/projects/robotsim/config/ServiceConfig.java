package fr.tp.inf112.projects.robotsim.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration of a service. The value of a property such as {@code server.port} is looked up, from the
 * lowest to the highest priority, in:
 * <ol>
 * <li>the {@code application.properties} resource of the service module,</li>
 * <li>the {@code .env} file of the working directory, under the name {@code SERVER_PORT},</li>
 * <li>the environment variable {@code SERVER_PORT},</li>
 * <li>the system property {@code server.port} (e.g. {@code -Dserver.port=9000}).</li>
 * </ol>
 */
public class ServiceConfig {

	public static final String PROPERTIES_RESOURCE = "/application.properties";

	public static final String ENV_FILE = ".env";

	private final Properties properties;

	private final Map<String, String> envFileVariables;

	private ServiceConfig(final Properties properties,
						  final Map<String, String> envFileVariables) {
		this.properties = properties;
		this.envFileVariables = envFileVariables;
	}

	/**
	 * Loads the configuration of the service whose classes are loaded by the class loader of the given class.
	 * @param serviceClass A class of the service module, used to locate its {@code application.properties}.
	 * @return The configuration.
	 * @throws IOException If the properties resource or the {@code .env} file cannot be read.
	 */
	public static ServiceConfig load(final Class<?> serviceClass)
	throws IOException {
		final Properties properties = new Properties();

		try (final InputStream inStream = serviceClass.getResourceAsStream(PROPERTIES_RESOURCE)) {
			if (inStream != null) {
				properties.load(inStream);
			}
		}

		return new ServiceConfig(properties, readEnvFile(Path.of(ENV_FILE)));
	}

	/**
	 * Parses the {@code KEY=VALUE} lines of a {@code .env} file, ignoring blank lines, comments starting with
	 * {@code #}, an optional {@code export} prefix and the quotes surrounding the values.
	 */
	public static Map<String, String> readEnvFile(final Path envFile)
	throws IOException {
		final Map<String, String> variables = new HashMap<>();

		if (!Files.isRegularFile(envFile)) {
			return variables;
		}

		for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
			line = line.strip();

			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith("export ")) {
				line = line.substring("export ".length()).strip();
			}

			final int separatorIndex = line.indexOf('=');

			if (separatorIndex <= 0) {
				continue;
			}

			String value = line.substring(separatorIndex + 1).strip();

			if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
				value = value.substring(1, value.length() - 1);
			}

			variables.put(line.substring(0, separatorIndex).strip(), value);
		}

		return variables;
	}

	/**
	 * @return The environment variable name of a property, e.g. {@code SERVER_PORT} for {@code server.port}.
	 */
	public static String toEnvironmentName(final String key) {
		return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
	}

	/**
	 * @param key The property name.
	 * @return The value of the property.
	 * @throws IllegalStateException If the property is not defined in any of the sources.
	 */
	public String getString(final String key) {
		final String envName = toEnvironmentName(key);
		String value = System.getProperty(key);

		if (value == null) {
			value = System.getenv(envName);
		}

		if (value == null) {
			value = envFileVariables.get(envName);
		}

		if (value == null) {
			value = properties.getProperty(key);
		}

		if (value == null) {
			throw new IllegalStateException("Missing configuration property '" + key + "' (or environment variable " + envName + ").");
		}

		return value.strip();
	}

	/**
	 * @param key The property name.
	 * @return The integer value of the property.
	 * @throws IllegalStateException If the property is not defined or is not an integer.
	 */
	public int getInt(final String key) {
		final String value = getString(key);

		try {
			return Integer.parseInt(value);
		}
		catch (final NumberFormatException ex) {
			throw new IllegalStateException("Configuration property '" + key + "' must be an integer, not '" + value + "'.", ex);
		}
	}
}
