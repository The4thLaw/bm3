package org.the4thlaw.bm3;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
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
	private final File playlistDirectory;
	private final boolean useSlashes;
	private final boolean syncMode;
	private final boolean dryRun;

	private final SummaryStatistics sourceFileTotalStats = new SummaryStatistics();
	private final SummaryStatistics targetFileTotalStats = new SummaryStatistics();
	private final SummaryStatistics syncSavedStats = new SummaryStatistics();
	private final SummaryStatistics syncRemovedStats = new SummaryStatistics();
	private final StopWatch stopWatch = new StopWatch();

	public FileProcessor(File sourceDirectory, File targetDirectory, boolean syncMode) {
		this(sourceDirectory, targetDirectory, null, true, syncMode, false);
	}

	public FileProcessor(File sourceDirectory, File targetDirectory, File playlistDirectory, boolean useSlashes, boolean syncMode, boolean dryRun) {
		if (!sourceDirectory.isDirectory()) {
			throw new IllegalArgumentException("Not a directory or doesn't exist: " + sourceDirectory);
		}
		
		this.sourceDirectory = sourceDirectory;
		this.targetDirectory = targetDirectory;
		if (playlistDirectory == null) {
			this.playlistDirectory = sourceDirectory;
		} else {
			this.playlistDirectory = playlistDirectory;
		}
		this.useSlashes = useSlashes;
		this.syncMode = syncMode;
		this.dryRun = dryRun;
	}

	public void process(ProgressReporter reporter) throws IOException {
		resetStats();

		// Use a tree set to maximise cache hits for covers
		Set<File> includedFiles = new TreeSet<>();
		Map<String, List<File>> loadedPlaylists = new HashMap<>();

		Collection<File> allPlaylists = getPlaylists(reporter);
		Collection<File> excludedPlaylists = new ArrayList<>();
		Collection<File> includedPlaylists = new ArrayList<>();
		filterPlaylists(allPlaylists, excludedPlaylists, includedPlaylists);
		LOGGER.info("Playlists found: {} ({} exclusion playlists, {} inclusion playlists)", allPlaylists.size(),
				excludedPlaylists.size(), includedPlaylists.size());

		Set<File> excludedFiles = loadExclusions(reporter, excludedPlaylists);
		findFiles(reporter, excludedFiles, includedPlaylists, includedFiles, loadedPlaylists);
		if (syncMode) {
			// Remove before copying to make room
			removeFiles(reporter, includedFiles);
		}
		recreatePlaylists(reporter, includedFiles, loadedPlaylists);
		copyFiles(reporter, includedFiles);

		reporter.setStatus("Done");
		LOGGER.info("Process complete");
		reporter.endTracking();
		outputStatistics();
	}

	private void removeFiles(ProgressReporter reporter, Set<File> includedFiles) {
		removePlaylists();
		removeObsoleteAudio(reporter, includedFiles);
	}

	private boolean deleteFile(File f) {
		if (dryRun) {
			return true;
		}
		try {
			Files.delete(f.toPath());
		} catch (IOException e) {
			LOGGER.warn("Failed to delete {}", f, e);
			return false;
		}
		return true;
	}

	private void removePlaylists() {
		File plsDir = getTargetPlaylistDirectory();
		File[] playlists = plsDir.listFiles((d, n) -> n.endsWith(".m3u"));
		if (playlists == null) {
			playlists = new File[0];
		}
		LOGGER.debug("Found {} existing playlists which will be removed", playlists.length);
		if (!dryRun) {
			for (File pls : playlists) {
				deleteFile(pls);
			}
		}
	}

	private void removeObsoleteAudio(ProgressReporter reporter, Set<File> includedFiles) {
		reporter.setStatus("Finding de-synced files to remove");
		reporter.setProgressUnknown(true);
		Path sourcePath = sourceDirectory.toPath();
		// Build the set of relative file names
		Set<String> includedPaths = includedFiles.stream().map(f -> sourcePath.relativize(f.toPath()).toString())
				.collect(Collectors.toSet());
		// List the files which have been removed from the source data
		NotInSourceFileFilter filter = new NotInSourceFileFilter(includedPaths, targetDirectory.toPath());
		Collection<File> filesToRemove = FileUtils.listFiles(targetDirectory, filter, TrueFileFilter.INSTANCE);
		// Actually remove those files from destination
		LOGGER.debug("There are {} files to delete", filesToRemove.size());
		reporter.setProgressUnknown(false);
		if (!filesToRemove.isEmpty()) {
			AtomicInteger step = new AtomicInteger(0);
			reporter.setStep(0);
			reporter.setTotal(filesToRemove.size());
			filesToRemove.stream().forEach(f -> {
				long fileSize = f.length();
				if (deleteFile(f)) {
					syncRemovedStats.addValue(fileSize);
				}
				reporter.setStep(step.incrementAndGet());
			});
		}

		// Some empty directories could be left behind but it's no big deal and it's a bit complex to prune them
	}

	/**
	 * Filters input playlists in two sets: inclusions and exclusions.
	 * 
	 * @param m3uFiles The files to filters.
	 * @param excludedPlaylists The list to populate with exclusions.
	 * @param includedPlaylists The list to populate with inclusions.
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
	 * @param reporter The progress reporter.
	 * @param excludedPlaylists The list of playlists to process as exclusions.
	 * @return The set of excluded files. The returned set contains only canonical files as per
	 *         {@link File#getCanonicalFile()}.
	 * @throws IOException If loading the playlist fails (this method does not throw exceptions if a file mentioned in a
	 *             playlist does not exist).
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
		Collection<File> playlists = FileUtils.listFiles(playlistDirectory, new String[] { "m3u" }, true);
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
					if (!musicFile.exists()) {
						// Silently skip missing files
						LOGGER.debug("Playlist {} references non-existing file {}, the file will be skipped",
								playlistName, musicFile);
						continue;
					}
					includedFiles.add(musicFile);
					playlistFiles.add(musicFile);
				}
				LOGGER.info("Playlist \"{}\" had {} files", playlistName, playlistFiles.size());
			}

			reporter.setStep(i++);
		}
		LOGGER.info("Found {} files", includedFiles.size());
	}

	private File getTargetPlaylistDirectory() {
		File targetPlaylistDirectory = new File(targetDirectory, "BM3_Playlists");
		if (!dryRun) {
			targetPlaylistDirectory.mkdirs();
		}
		return targetPlaylistDirectory;
	}

	private void recreatePlaylists(ProgressReporter reporter, Set<File> allFiles,
			Map<String, List<File>> loadedPlaylists) throws IOException {
		File targetPlaylistDirectory = getTargetPlaylistDirectory();

		int i = 0;
		reporter.setStatus("Creating playlists...");
		reporter.setStep(i);
		reporter.setTotal(loadedPlaylists.size());
		for (Entry<String, List<File>> playlistEntry : loadedPlaylists.entrySet()) {
			String plsName = playlistEntry.getKey();
			File playlistFile = new File(targetPlaylistDirectory, plsName + ".m3u");
			PlaylistWriter writer = new PlaylistWriter(playlistFile, sourceDirectory.toPath(), useSlashes, dryRun);
			List<File> plsEntries = playlistEntry.getValue();
			writer.writeEntries(plsEntries, reporter);
			reporter.setStep(i++);
			reporter.endSubTracking();
		}
	}

	private PrintWriter getPlaylistWriter(File playlistFile) throws IOException {
		if (dryRun) {
			return new PrintWriter(new StringWriter());
		} else {
			return new PrintWriter(new BufferedWriter(new FileWriter(playlistFile)));
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
		if (!dryRun) {
			targetFile.getParentFile().mkdirs();
		}

		// Check for cover. Only for MP3
		Cover cover = null;
		if (FilenameUtils.getExtension(sourceFile.getName()).equalsIgnoreCase("mp3")) {
			cover = Cover.forMusicFile(sourceFile);
		}

		// Safety check to avoid corruption
		Path canonicalSource = sourceFile.toPath().toRealPath();
		Path targetPath = targetFile.toPath();
		if (Files.exists(targetPath)) {
			Path canonicalTarget = targetPath.toRealPath();
			if (canonicalSource.equals(canonicalTarget)) {
				LOGGER.error("Fatal error: source and target are equal, this could lead to data corruption "
					+ "(nothing was corrupted here): source = {}, target = {}", canonicalSource, canonicalTarget);
				throw new IllegalArgumentException("Fatal error: source and target are equal");
			}
		}

		long originalSize = sourceFile.length();
		if (shouldCopy(sourceFile, targetFile, cover)) {
			if (!dryRun) {
				// If there is no cover, copy the file as-is
				if (cover == null) {
					FileUtils.copyFile(sourceFile, targetFile);
				} else {
					// We can integrate the cover on the fly
					try {
						cover.writeToFile(sourceFile, targetFile);
					} catch (Exception e) {
						LOGGER.warn("Failed to write cover in file {}, the file will be copied without cover", targetFile, e);
						FileUtils.copyFile(sourceFile, targetFile);
					}
				}
				// Update the target date so that it's used in future synced runs
				targetFile.setLastModified(System.currentTimeMillis());
			}
		} else {
			syncSavedStats.addValue(originalSize);
		}

		long destinationSize;
		if (dryRun) {
			// Will be different without the dry run but we can't estimate the increase
			destinationSize = originalSize;
		} else {
			destinationSize = targetFile.length();
		}
		sourceFileTotalStats.addValue(originalSize);
		targetFileTotalStats.addValue(destinationSize);

		LOGGER.trace("Copied {}", path.toString());
	}

	/**
	 * Applies heuristics to check if it's worth copying the file to the destination.
	 * 
	 * @param sourceFile The file to copy.
	 * @param targetFile The destination.
	 * @param cover The cover.
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

	/**
	 * Resets all statistics.
	 */
	private void resetStats() {
		sourceFileTotalStats.clear();
		targetFileTotalStats.clear();
		syncSavedStats.clear();
		syncRemovedStats.clear();
		stopWatch.start();
	}

	/**
	 * Closes and outputs all statistics to the logger.
	 */
	private void outputStatistics() {
		stopWatch.stop();

		LOGGER.info("Processed {} files in {} seconds.", sourceFileTotalStats.getN(),
				Math.round(stopWatch.getTime() / 1000));
		double sourceTotal = sourceFileTotalStats.getSum();
		LOGGER.info("Source files weighted {} MB in total (average: {})", byteCountToMB((long) sourceTotal),
				FileUtils.byteCountToDisplaySize((long) sourceFileTotalStats.getMean()));
		double targetTotal = targetFileTotalStats.getSum();
		float increase = Math.round(((targetTotal / sourceTotal) - 1) * 10000) / 100;
		LOGGER.info("Target files weighted {} MB in total (average: {}), a {}% increase with the covers",
				byteCountToMB((long) targetTotal),
				FileUtils.byteCountToDisplaySize((long) targetFileTotalStats.getMean()), increase);
		LOGGER.info("Sync removed {} files worth {} MB", syncRemovedStats.getN(),
				byteCountToMB((long) syncRemovedStats.getSum()));
		LOGGER.info("Sync saved the copy of {} MB in {} files", byteCountToMB((long) syncSavedStats.getSum()),
				syncSavedStats.getN());
	}

	private static long byteCountToMB(long bytes) {
		return Math.round(((double) bytes) / 1024 / 1024);
	}
}
