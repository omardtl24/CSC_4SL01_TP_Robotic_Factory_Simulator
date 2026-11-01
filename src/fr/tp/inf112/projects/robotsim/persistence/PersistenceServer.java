package fr.tp.inf112.projects.robotsim.persistence;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;

/**
 * Server storing the factory models in its working directory. Each client connection is handled by a
 * {@link PersistenceRequestProcessor} running in its own thread.
 */
public class PersistenceServer {

	public static final int DEFAULT_PORT = 8090;

	private static final Logger LOGGER = Logger.getLogger(PersistenceServer.class.getName());

	public static void main(final String[] args) {
		final int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
		final FactoryPersistenceManager persistenceManager = new FactoryPersistenceManager(null);

		try (final ServerSocket serverSocket = new ServerSocket(port)) {
			LOGGER.info("Persistence server listening on port " + port + "...");

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
