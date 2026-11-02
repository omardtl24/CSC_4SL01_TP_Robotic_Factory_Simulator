package fr.tp.inf112.projects.robotsim.persistence;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.robotsim.config.ServiceConfig;
import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;

/**
 * Server storing the factory models in the directory given by the {@code persistence.storage.directory} property
 * of its {@link ServiceConfig}, and listening on the {@code persistence.port} one. A port given as first argument overrides
 * the configured one. Each client connection is handled by a {@link PersistenceRequestProcessor} running in its
 * own thread.
 */
public class PersistenceServer {

	private static final Logger LOGGER = Logger.getLogger(PersistenceServer.class.getName());

	public static void main(final String[] args) {
		final int port;
		final Path storageDirectory;

		try {
			final ServiceConfig config = ServiceConfig.load(PersistenceServer.class);
			port = args.length > 0 ? Integer.parseInt(args[0]) : config.getInt("persistence.port");
			storageDirectory = Files.createDirectories(Path.of(config.getString("persistence.storage.directory")));
		}
		catch (final IOException | RuntimeException ex) {
			LOGGER.log(Level.SEVERE, "Unable to configure the persistence server.", ex);

			return;
		}

		final FactoryPersistenceManager persistenceManager = new FactoryPersistenceManager(null, storageDirectory);

		try (final ServerSocket serverSocket = new ServerSocket(port)) {
			LOGGER.info("Persistence server listening on port " + port + ", storing factories in "
						+ storageDirectory.toAbsolutePath() + "...");

			while (true) {
				try {
					final Socket socket = serverSocket.accept();
					new Thread(new PersistenceRequestProcessor(socket, persistenceManager)).start();
				}
				catch (final IOException ex) {
					LOGGER.log(Level.WARNING, "Failed to accept a client connection.", ex);
				}
			}
		}
		catch (final IOException ex) {
			LOGGER.log(Level.SEVERE, "Unable to start the persistence server on port " + port + ".", ex);
		}
	}
}
