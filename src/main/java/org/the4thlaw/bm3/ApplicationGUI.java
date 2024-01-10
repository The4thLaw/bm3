package org.the4thlaw.bm3;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

public class ApplicationGUI implements ProgressReporter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationGUI.class);

	private JFrame frame;
	private File inputDirectory;
	private File outputDirectory;
	private final Action browseSourceAction = new BrowseSourceAction();
	private final Action browseDestinationAction = new BrowseDestinationAction();
	private final Action runAction = new RunAction();
	private JProgressBar mainProgressBar;
	private JProgressBar subProgressBar;
	private JLabel statusLabel;
	private int currentTotal;

	private JCheckBox syncCheckbox;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					ApplicationGUI window = new ApplicationGUI();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public ApplicationGUI() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			LOGGER.warn("Failed to set the system look & feel, continuing happily", e);
		}
		frame = new JFrame();
		frame.setTitle("BM3");
		frame.setBounds(100, 100, 386, 240);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(
				new FormLayout(
						// Columns
						new ColumnSpec[]
						{ FormFactory.RELATED_GAP_COLSPEC,
								FormFactory.LABEL_COMPONENT_GAP_COLSPEC, FormFactory.RELATED_GAP_COLSPEC,
								ColumnSpec.decode("default:grow"), FormFactory.RELATED_GAP_COLSPEC,
								FormFactory.DEFAULT_COLSPEC, FormFactory.RELATED_GAP_COLSPEC,
								ColumnSpec.decode("default:grow"), FormFactory.RELATED_GAP_COLSPEC,
								FormFactory.LABEL_COMPONENT_GAP_COLSPEC, },
						// Rows
						new RowSpec[]
						{ FormFactory.RELATED_GAP_ROWSPEC,
								FormFactory.DEFAULT_ROWSPEC, FormFactory.RELATED_GAP_ROWSPEC,
								FormFactory.DEFAULT_ROWSPEC,
								FormFactory.RELATED_GAP_ROWSPEC, FormFactory.DEFAULT_ROWSPEC,
								FormFactory.RELATED_GAP_ROWSPEC,
								FormFactory.DEFAULT_ROWSPEC, FormFactory.RELATED_GAP_ROWSPEC,
								FormFactory.DEFAULT_ROWSPEC,
								FormFactory.RELATED_GAP_ROWSPEC, FormFactory.DEFAULT_ROWSPEC,
								FormFactory.RELATED_GAP_ROWSPEC, FormFactory.DEFAULT_ROWSPEC, }));

		JLabel lblSourceDirectory = new JLabel("Source directory:");
		frame.getContentPane().add(lblSourceDirectory, "4, 2");

		JButton browseSourceButton = new JButton();
		browseSourceButton.setAction(browseSourceAction);
		frame.getContentPane().add(browseSourceButton, "8, 2");

		JLabel lblTargetDirectory = new JLabel("Target directory:");
		frame.getContentPane().add(lblTargetDirectory, "4, 4");

		JButton browseDestinationButton = new JButton();
		browseDestinationButton.setAction(browseDestinationAction);
		frame.getContentPane().add(browseDestinationButton, "8, 4");

		syncCheckbox = new JCheckBox("Sync mode");
		syncCheckbox.setSelected(true);
		syncCheckbox.setToolTipText("Based on modification times, avoid copying the source file if it hasn't changed.");
		frame.getContentPane().add(syncCheckbox, "4, 6");

		JButton btnRun = new JButton();
		btnRun.setAction(runAction);
		frame.getContentPane().add(btnRun, "6, 8");

		statusLabel = new JLabel("No action running.");
		statusLabel.setEnabled(false);
		frame.getContentPane().add(statusLabel, "4, 10");

		mainProgressBar = new JProgressBar();
		mainProgressBar.setEnabled(false);
		frame.getContentPane().add(mainProgressBar, "4, 12, 5, 1");

		subProgressBar = new JProgressBar();
		subProgressBar.setEnabled(false);
		frame.getContentPane().add(subProgressBar, "4, 14, 5, 1");
	}

	private class BrowseSourceAction extends AbstractAction {
		private static final long serialVersionUID = 7161515715112370918L;

		public BrowseSourceAction() {
			putValue(NAME, "Browse...");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.showOpenDialog(frame);
			File f = chooser.getSelectedFile();
			if (f != null) {
				putValue(SHORT_DESCRIPTION, f.getName());
			} else {
				putValue(SHORT_DESCRIPTION, "");
			}
			inputDirectory = f;
		}
	}

	private class BrowseDestinationAction extends AbstractAction {
		private static final long serialVersionUID = -9189946591935750651L;

		public BrowseDestinationAction() {
			putValue(NAME, "Browse...");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.showSaveDialog(frame);
			File f = chooser.getSelectedFile();
			if (f != null) {
				putValue(SHORT_DESCRIPTION, f.getName());
			} else {
				putValue(SHORT_DESCRIPTION, "");
			}
			outputDirectory = f;
		}
	}

	private class RunAction extends AbstractAction {
		private static final long serialVersionUID = -7368014443711935709L;

		public RunAction() {
			putValue(NAME, "Run");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			lockUnlockUI(true);
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						new FileProcessor(inputDirectory, outputDirectory, syncCheckbox.isSelected(), false)
								.process(ApplicationGUI.this);
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								lockUnlockUI(false);
							}
						});
					} catch (final IOException e) {
						LOGGER.error("Failed to process playlists", e);
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								JOptionPane.showMessageDialog(frame, "Failed to process playlists:\n" + e.getMessage(),
										"Warning", JOptionPane.WARNING_MESSAGE);
							}
						});
					}
				}
			}).start();
		}
	}

	private void lockUnlockUI(boolean locked) {
		statusLabel.setEnabled(locked);
		mainProgressBar.setEnabled(locked);
		runAction.setEnabled(!locked);
		browseSourceAction.setEnabled(!locked);
		browseDestinationAction.setEnabled(!locked);
		syncCheckbox.setEnabled(!locked);
		if (locked) {
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		} else {
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}
	}

	@Override
	public void setStatus(final String status) {
		LOGGER.info(status);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				statusLabel.setText(status);
			}
		});
	}

	@Override
	public void setProgressUnknown(final boolean unknown) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				mainProgressBar.setIndeterminate(unknown);
				mainProgressBar.setStringPainted(!unknown);
			}
		});
	}

	@Override
	public void setTotal(final int total) {
		currentTotal = total;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				mainProgressBar.setMaximum(total);
			}
		});
	}

	@Override
	public void setStep(final int step) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				mainProgressBar.setString(step + " / " + currentTotal);
				mainProgressBar.setValue(step);
			}
		});
	}

	@Override
	public void reportError(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JOptionPane.showMessageDialog(frame, message, "Warning", JOptionPane.WARNING_MESSAGE);
			}
		});
	}

	@Override
	public void setSubTotal(final int total) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				subProgressBar.setEnabled(true);
				subProgressBar.setStringPainted(true);
				subProgressBar.setMaximum(total);
			}
		});
	}

	@Override
	public void setSubStep(final int step) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				subProgressBar.setString(step + " / " + subProgressBar.getMaximum());
				subProgressBar.setValue(step);
			}
		});
	}

	@Override
	public void endSubTracking() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				subProgressBar.setMaximum(1);
				subProgressBar.setValue(0);
				subProgressBar.setEnabled(false);
				subProgressBar.setStringPainted(false);
			}
		});
	}
}
