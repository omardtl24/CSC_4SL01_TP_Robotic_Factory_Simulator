package fr.tp.inf112.projects.robotsim.persistence;

import java.io.IOException;

import javax.swing.JOptionPane;

import fr.tp.inf112.projects.canvas.view.FileCanvasChooser;

/**
 * Canvas chooser that lets users pick a canvas among the files stored on the {@link PersistenceServer}, or
 * enter the name of a new one, instead of browsing the local file system.
 */
public class RemoteFileCanvasChooser extends FileCanvasChooser {

	private final String fileExtension;

	private final String documentTypeLabel;

	private final PersistenceClient client;

	public RemoteFileCanvasChooser(final String fileExtension,
								   final String documentTypeLabel,
								   final PersistenceClient client) {
		super(fileExtension, documentTypeLabel);

		this.fileExtension = fileExtension;
		this.documentTypeLabel = documentTypeLabel;
		this.client = client;
	}

	@Override
	protected String browseCanvases(final boolean open)
	throws IOException {
		if (open) {
			final String[] canvasIds = (String[]) client.send(new ListCanvasesRequest(fileExtension));

			if (canvasIds.length == 0) {
				JOptionPane.showMessageDialog(getViewer(), "No " + documentTypeLabel + " file found on the server.");

				return null;
			}

			return (String) JOptionPane.showInputDialog(getViewer(),
														"Select the " + documentTypeLabel + " to open:",
														"Open Canvas",
														JOptionPane.QUESTION_MESSAGE,
														null,
														canvasIds,
														canvasIds[0]);
		}

		final String canvasId = JOptionPane.showInputDialog(getViewer(),
															"Enter the " + documentTypeLabel + " file name:",
															"Save Canvas",
															JOptionPane.QUESTION_MESSAGE);

		if (canvasId == null || canvasId.isBlank()) {
			return null;
		}

		return canvasId.trim();
	}
}
