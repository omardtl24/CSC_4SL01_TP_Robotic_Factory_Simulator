package fr.tp.inf112.projects.robotsim.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;

/**
 * Processes a single client request of the {@link PersistenceServer}. The request is deserialized and its
 * type determines the operation:
 * <ul>
 * <li>{@code String}: the identifier of the factory to read, which is sent back to the client.</li>
 * <li>{@link Factory}: the factory to persist, using its identifier as file name.</li>
 * <li>{@link ListCanvasesRequest}: the names of the stored files are sent back to the client.</li>
 * </ul>
 * If the request fails, an {@code IOException} describing the problem is sent back instead.
 */
public class PersistenceRequestProcessor implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(PersistenceRequestProcessor.class.getName());

	/**
	 * Only classes of the simulator, the canvas viewer, JGraphT and the JDK may be deserialized from the network.
	 */
	private static final ObjectInputFilter REQUEST_FILTER = ObjectInputFilter.Config.createFilter(
		"fr.tp.inf112.projects.**;org.jgrapht.**;org.jheaps.**;java.base/*;!*"
	);

	private final Socket socket;

	private final FactoryPersistenceManager persistenceManager;

	public PersistenceRequestProcessor(final Socket socket,
									   final FactoryPersistenceManager persistenceManager) {
		this.socket = socket;
		this.persistenceManager = persistenceManager;
	}

	@Override
	public void run() {
		try (socket) {
			final ObjectInputStream objInStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
			objInStream.setObjectInputFilter(REQUEST_FILTER);
			final Object request = objInStream.readObject();

			Object response;

			try {
				response = process(request);
			}
			catch (final IOException | RuntimeException ex) {
				LOGGER.log(Level.WARNING, "Failed to process request " + request + ".", ex);
				response = new IOException(ex.getMessage());
			}

			final ObjectOutputStream objOutStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			objOutStream.writeObject(response);
			objOutStream.flush();
		}
		catch (final EOFException ex) {
			// The client closed the connection without sending a request (e.g. a connectivity check).
			LOGGER.log(Level.FINE, "Client " + socket.getRemoteSocketAddress() + " disconnected without sending a request.");
		}
		catch (final IOException | ClassNotFoundException ex) {
			LOGGER.log(Level.SEVERE, "Communication with client " + socket.getRemoteSocketAddress() + " failed.", ex);
		}
	}

	private Object process(final Object request)
	throws IOException {
		if (request instanceof String) {
			final String canvasId = (String) request;
			LOGGER.info("Reading factory '" + canvasId + "'.");

			return persistenceManager.read(checkCanvasId(canvasId));
		}

		if (request instanceof Factory) {
			final Factory factory = (Factory) request;
			LOGGER.info("Persisting factory '" + factory.getId() + "'.");
			checkCanvasId(factory.getId());
			persistenceManager.persist(factory);

			return Boolean.TRUE;
		}

		if (request instanceof ListCanvasesRequest) {
			return listCanvasIds(((ListCanvasesRequest) request).getFileExtension());
		}

		throw new IOException("Unsupported request type " + (request == null ? null : request.getClass().getName()) + ".");
	}

	/**
	 * Ensures that the identifier is a plain file name, so that clients cannot access files outside of the
	 * working directory of the server.
	 */
	private String checkCanvasId(final String canvasId)
	throws IOException {
		if (canvasId == null || canvasId.isBlank() || canvasId.equals(".") || canvasId.equals("..")
				|| canvasId.indexOf('/') >= 0 || canvasId.indexOf(File.separatorChar) >= 0) {
			throw new IOException("Invalid canvas identifier '" + canvasId + "'.");
		}

		return canvasId;
	}

	private String[] listCanvasIds(final String fileExtension) {
		final String suffix = "." + fileExtension;
		final File[] files = new File(".").listFiles(file -> file.isFile() && file.getName().endsWith(suffix));

		if (files == null) {
			return new String[0];
		}

		final String[] canvasIds = Arrays.stream(files).map(File::getName).sorted().toArray(String[]::new);
		LOGGER.info("Listing " + canvasIds.length + " factories.");

		return canvasIds;
	}
}
