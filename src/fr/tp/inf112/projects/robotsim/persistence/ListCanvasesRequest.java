package fr.tp.inf112.projects.robotsim.persistence;

import java.io.Serializable;

/**
 * Request sent to the {@link PersistenceServer} to obtain the identifiers (file names) of the canvases stored
 * in its working directory. The server answers with a {@code String[]}.
 */
public class ListCanvasesRequest implements Serializable {

	private static final long serialVersionUID = -3264016935742110594L;

	private final String fileExtension;

	public ListCanvasesRequest(final String fileExtension) {
		this.fileExtension = fileExtension;
	}

	public String getFileExtension() {
		return fileExtension;
	}
}
