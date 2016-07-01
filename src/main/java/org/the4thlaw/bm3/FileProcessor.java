package org.the4thlaw.bm3;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not thread-safe.
 */
public class FileProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);
	private static final Pattern EXCLUDE_PATTERN = Pattern.compile("^BM3.Exclu(sion|de)s?.*", Pattern.CASE_INSENSITIVE);
	private final File sourceDirectory;
	private final File targetDirectory;
	private final boolean syncMode;

	public FileProcessor(File sourceDirectory, File targetDirectory, boolean syncMode) {
		this.sourceDirectory = sourceDirectory;
		this.targetDirectory = targetDirectory;
		this.syncMode = syncMode;
	}

	public void process(ProgressReporter reporter) throws IOException {
		Set<File> includedFiles = new TreeSet<>(); // Use a tree set to maximise
													// cache hits for covers
		Map<String, List<File>> loadedPlaylists = new HashMap<>();

		Collection<File> allPlaylists = getPlaylists(reporter);
		Collection<File> excludedPlaylists = new ArrayList<>();
		Collection<File> includedPlaylists = new ArrayList<>();
		filterPlaylists(allPlaylists, excludedPlaylists, includedPlaylists);
		LOGGER.info("Playlists found: {} ({} exclusion playlists, {} inclusion playlists)", allPlaylists.size(),
				excludedPlaylists.size(), includedPlaylists.size());

		Set<File> excludedFiles = loadExclusions(reporter, excludedPlaylists);
		findFiles(reporter, excludedFiles, includedPlaylists, includedFiles, loadedPlaylists);
		recreatePlaylists(reporter, includedFiles, loadedPlaylists);
		copyFiles(reporter, includedFiles);

		reporter.setStatus("Done");
		LOGGER.info("Process complete");
	}

	/**
	 * Filters input playlists in two sets: inclusions and exclusions.
	 * 
	 * @param m3uFiles
	 *            The files to filters.
	 * @param excludedPlaylists
	 *            The list to populate with exclusions.
	 * @param includedPlaylists
	 *            The list to populate with inclusions.
	 */
	private static void filterPlaylists(Collection<File> m3uFiles, Collection<File> excludedPlaylists,
			Collection<File> includedPlaylists) {
		for (File m3uFile : m3uFiles) {
			if (EXCLUDE_PATTERN.matcher(m3uFile.getName()).matches()) {
				excludedPlaylists.add(m3uFile);
			} else {
				includedPlaylists.add(m3uFile);
			}
		}
	}

	/**
	 * Loads the list excluded files.
	 * 
	 * @param reporter
	 *            The progress reporter.
	 * @param excludedPlaylists
	 *            The list of playlists to process as exclusions.
	 * @return The set of excluded files. The returned set contains only
	 *         canonical files as per {@link File#getCanonicalFile()}.
	 * @throws IOException
	 *             If loading the playlist fails (this method does not throw
	 *             exceptions if a file mentioned in a playlist does not exist).
	 */
	private static Set<File> loadExclusions(ProgressReporter reporter, Collection<File> excludedPlaylists)
			throws IOException {
		Set<File> exclusions = new HashSet<>();

		int i = 0;
		reporter.setStatus("Reading exclusions...");
		reporter.setProgressUnknown(false);
		reporter.setStep(i);
		reporter.setTotal(excludedPlaylists.size());

		for (File m3uFile : excludedPlaylists) {
			String playlistName = m3uFile.getName();
			LOGGER.info("Parsing exclusion playlist named \"{}\"", playlistName);
			try (PlaylistReader m3uReader = new PlaylistReader(m3uFile)) {
				int files = 0;
				File musicFile;
				while ((musicFile = m3uReader.getEntry()) != null) {
					exclusions.add(musicFile);
					files++;
				}
				LOGGER.info("Exclusion playlist \"{}\" had {} files", playlistName, files);
			}

			reporter.setStep(i++);
		}

		return exclusions;
	}

	private Collection<File> getPlaylists(ProgressReporter reporter) {
		LOGGER.info("Searching for playlists...");
		reporter.setProgressUnknown(true);
		reporter.setStatus("Searching for playlists...");
		Collection<File> playlists = FileUtils.listFiles(sourceDirectory, new String[] { "m3u" }, true);
		return playlists;
	}

	private static void findFiles(ProgressReporter reporter, Collection<File> excludedFiles,
			Collection<File> includedPlaylists, Set<File> includedFiles, Map<String, List<File>> loadedPlaylists)
			throws IOException {
		LOGGER.info("Listing included files");
		int i = 0;
		reporter.setStatus("Reading playlists...");
		reporter.setProgressUnknown(false);
		reporter.setStep(i);
		reporter.setTotal(includedPlaylists.size());
		for (File m3uFile : includedPlaylists) {
			String playlistName = FilenameUtils.getBaseName(m3uFile.getName());
			List<File> playlistFiles = new ArrayList<>();

			if (loadedPlaylists.containsKey(playlistName)) {
				LOGGER.warn("Already registered a playlist named \"{}\", the following one will be ignored: {}",
						playlistName, m3uFile);
				reporter.reportError("There are at least two playlist named '" + playlistName
						+ "'.\nThe following one will be ignored: " + m3uFile);
				continue;
			}
			loadedPlaylists.put(playlistName, playlistFiles);

			LOGGER.info("Parsing playlist named \"{}\"", playlistName);
			try (PlaylistReader m3uReader = new PlaylistReader(m3uFile)) {
				File musicFile;
				while ((musicFile = m3uReader.getEntry()) != null) {
					if (excludedFiles.contains(musicFile)) {
						LOGGER.debug("File {} has been marked for exclusion", musicFile);
						continue;
					}
					includedFiles.add(musicFile);
					playlistFiles.add(musicFile);
				}
				LOGGER.info("Playlist \"{}\" had {} files", playlistName, playlistFiles.size());
			}

			reporter.setStep(i++);
		}
	}

	private void recreatePlaylists(ProgressReporter reporter, Set<File> allFiles,
			Map<String, List<File>> loadedPlaylists) throws IOException {
		File targetPlaylistDirectory = new File(targetDirectory, "BM3_Playlists");
		targetPlaylistDirectory.mkdirs();

		int i = 0;
		reporter.setStatus("Creating playlists...");
		reporter.setStep(i);
		reporter.setTotal(loadedPlaylists.size());
		for (Entry<String, List<File>> playlistEntry : loadedPlaylists.entrySet()) {
			File playlistFile = new File(targetPlaylistDirectory, playlistEntry.getKey() + ".m3u");
			try (PrintWriter m3uWriter = new PrintWriter(new FileWriter(playlistFile))) {
				m3uWriter.println("#EXTM3U");
				for (File record : playlistEntry.getValue()) {
					m3uWriter.println(".." + File.separator
							+ sourceDirectory.toPath().relativize(record.toPath()).toString());
				}
			}
			reporter.setStep(i++);
		}
	}

	private void copyFiles(ProgressReporter reporter, Set<File> allFiles) {
		LOGGER.info("Copying files and setting covers...");
		int i = 0;
		reporter.setStatus("Copying files and covers...");
		reporter.setStep(i);
		reporter.setTotal(allFiles.size());
		for (File sourceFile : allFiles) {
			try {
				copyFile(sourceFile);
			} catch (IOException e) {
				LOGGER.warn("Failed to copy a file: {}", sourceFile, e);
				reporter.reportError("Failed to copy a file:\n" + e.getMessage() + "\n\nFile was:\n" + sourceFile);
				// Continue happily
			}
			reporter.setStep(i++);
		}
		reporter.setStep(allFiles.size());
		LOGGER.info("Copy complete");
	}

	private void copyFile(File sourceFile) throws IOException {
		Path path = sourceDirectory.toPath().relativize(sourceFile.toPath());

		// Create the parent directory in the target
		File targetFile = new File(targetDirectory, path.toString());
		targetFile.getParentFile().mkdirs();

		// Check for cover. Only for MP3
		Cover cover = null;
		if (FilenameUtils.getExtension(sourceFile.getName()).equalsIgnoreCase("mp3")) {
			cover = Cover.forMusicFile(sourceFile);
		}

		if (shouldCopy(sourceFile, targetFile, cover)) {
			// If there is no cover, copy the file as-is
			if (cover == null) {
				FileUtils.copyFile(sourceFile, targetFile);
			} else {
				// We can integrate the cover on the fly
				cover.writeToFile(sourceFile, targetFile);
			}
		}

		LOGGER.trace("Copied {}", path.toString());
	}

	/**
	 * Applies heuristics to check if it's worth copying the file to the
	 * destination.
	 * 
	 * @param sourceFile
	 *            The file to copy.
	 * @param targetFile
	 *            The destination.
	 * @param cover
	 *            The cover.
	 * @return <code>true</code> if the file should be copied.
	 */
	private boolean shouldCopy(File sourceFile, File targetFile, Cover cover) {
		if (!syncMode) {
			return true;
		}

		if (!targetFile.exists()) {
			LOGGER.debug("Syncing {}: target file does not exist", sourceFile);
			return true;
		}

		long sourceLastModified = sourceFile.lastModified();
		if (cover != null) {
			long coverLastModified = cover.lastModified();
			if (coverLastModified > sourceLastModified) {
				LOGGER.trace("Cover for source file has been modified after it, "
						+ "using it as reference (source file: {})", sourceFile);
				sourceLastModified = coverLastModified;
			}
		}

		if (sourceLastModified > targetFile.lastModified()) {
			LOGGER.debug("Syncing {}: source file or cover is more recent", sourceFile);
			return true;
		}

		LOGGER.debug("Not syncing {}: no change since last copy", sourceFile);
		return false;
	}
}
