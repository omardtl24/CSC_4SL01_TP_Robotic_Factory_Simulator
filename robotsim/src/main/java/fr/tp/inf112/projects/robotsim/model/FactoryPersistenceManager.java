package fr.tp.inf112.projects.robotsim.model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasChooser;
import fr.tp.inf112.projects.canvas.model.impl.AbstractCanvasPersistenceManager;

public class FactoryPersistenceManager extends AbstractCanvasPersistenceManager {
	
	/**
	 * Directory against which relative canvas identifiers are resolved.
	 */
	private final Path directory;
	
	public FactoryPersistenceManager(final CanvasChooser canvasChooser) {
		this(canvasChooser, Path.of(""));
	}
	
	public FactoryPersistenceManager(final CanvasChooser canvasChooser,
									 final Path directory) {
		super(canvasChooser);
		
		this.directory = directory;
	}
	
	public Path getDirectory() {
		return directory;
	}
	
	private File getFile(final String canvasId) {
		return directory.resolve(canvasId).toFile();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Canvas read(final String canvasId)
	throws IOException {
		try (
			final InputStream fileInputStream = new FileInputStream(getFile(canvasId));
			final InputStream bufInputStream = new BufferedInputStream(fileInputStream);
			final ObjectInputStream objectInputStrteam = new ObjectInputStream(bufInputStream);
		) {
			return (Canvas) objectInputStrteam.readObject();
		}
		catch (ClassNotFoundException | IOException ex) {
			throw new IOException(ex);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void persist(Canvas canvasModel)
	throws IOException {
		try (
			final OutputStream fileOutStream = new FileOutputStream(getFile(canvasModel.getId()));
			final OutputStream bufOutStream = new BufferedOutputStream(fileOutStream);
			final ObjectOutputStream objOutStream = new ObjectOutputStream(bufOutStream);
		) {	
			objOutStream.writeObject(canvasModel);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean delete(final Canvas canvasModel)
	throws IOException {
		final File canvasFile = getFile(canvasModel.getId());
		
		return canvasFile.delete();
	}
}
