package fr.tp.inf112.projects.robotsim.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Sends serialized requests to a {@code PersistenceServer} over a socket and returns its deserialized response.
 * One connection is opened per request.
 */
public class PersistenceClient {

	private static final int CONNECTION_TIMEOUT_MS = 5000;

	private final String host;

	private final int port;

	public PersistenceClient(final String host,
							 final int port) {
		this.host = host;
		this.port = port;
	}

	/**
	 * Serializes the given request, sends it to the server and returns the object sent back.
	 * @param request A {@code Serializable} request object.
	 * @return The response object of the server.
	 * @throws IOException If the communication fails or if the server reports an error.
	 */
	public Object send(final Object request)
	throws IOException {
		try (final Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);

			// The output stream must be created and flushed first since the ObjectInputStream constructor
			// blocks until it reads the stream header written by the peer.
			final ObjectOutputStream objOutStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			objOutStream.writeObject(request);
			objOutStream.flush();

			final ObjectInputStream objInStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
			final Object response = objInStream.readObject();

			if (response instanceof Exception) {
				throw new IOException("The persistence server failed to process the request.", (Exception) response);
			}

			return response;
		}
		catch (final ClassNotFoundException ex) {
			throw new IOException(ex);
		}
	}
}
