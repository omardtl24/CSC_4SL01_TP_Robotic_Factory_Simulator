package fr.tp.inf112.projects.robotsim.persistence;

import java.io.IOException;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasChooser;
import fr.tp.inf112.projects.canvas.model.impl.AbstractCanvasPersistenceManager;

/**
 * Persistence manager that stores the factory models on a remote {@link PersistenceServer}. The model is
 * serialized and sent to the server, which writes it to its own file system.
 */
public class RemoteFactoryPersistenceManager extends AbstractCanvasPersistenceManager {

	private final PersistenceClient client;

	public RemoteFactoryPersistenceManager(final CanvasChooser canvasChooser,
										   final PersistenceClient client) {
		super(canvasChooser);

		this.client = client;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Canvas read(final String canvasId)
	throws IOException {
		return (Canvas) client.send(canvasId);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void persist(final Canvas canvasModel)
	throws IOException {
		client.send(canvasModel);
	}

	/**
	 * Not supported since it is never called by the canvas viewer.
	 */
	@Override
	public boolean delete(final Canvas canvasModel)
	throws IOException {
		throw new UnsupportedOperationException("Deleting remote canvases is not supported.");
	}
}
