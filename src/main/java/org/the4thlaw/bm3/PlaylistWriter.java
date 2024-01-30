package org.the4thlaw.bm3;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for writing a playlist to a file */
public class PlaylistWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaylistWriter.class);

    private final File playlistFile;
    private final Path sourceDirectory;
    private final boolean useSlashes;
    private final boolean dryRun;

    public PlaylistWriter(File playlistFile, Path sourceDirectory, boolean useSlashes, boolean dryRun) {
        this.playlistFile = playlistFile;
        this.sourceDirectory = sourceDirectory;
        this.useSlashes = useSlashes;
        this.dryRun = dryRun;
    }

    public void writeEntries(List<File> plsEntries, ProgressReporter reporter) throws IOException {
        String plsName = playlistFile.getName();
        int numEntries = plsEntries.size();
        int curEntry = 0;

        LOGGER.info("Creating playlist \"{}\" for {} files", playlistFile, numEntries);
        reporter.setSubTotal(numEntries);
        reporter.setSubStep(curEntry);

        try (PrintWriter m3uWriter = getWriter(playlistFile)) {
            m3uWriter.println("#EXTM3U");
            m3uWriter.println("#EXTENC:UTF-8");
            LOGGER.trace("{}: wrote header", plsName);
            for (File record : plsEntries) {
                String adjustedPath = adjustSeparators(
                        ".." + File.separator + sourceDirectory.relativize(record.toPath()).toString());
                LOGGER.trace("{}: writing path for file {}", plsName, adjustedPath);
                m3uWriter.println(adjustedPath);
                reporter.setSubStep(++curEntry);
            }
        }
    }

    private PrintWriter getWriter(File playlistFile) throws IOException {
        if (dryRun) {
            return new PrintWriter(new StringWriter());
        } else {
            return new PrintWriter(new BufferedWriter(new FileWriter(playlistFile, StandardCharsets.UTF_8)));
        }
    }

    private String adjustSeparators(String path) {
        if (useSlashes) {
            return path.replace(File.separatorChar, '/');
        }
        return path.replace(File.separatorChar, '\\');
    }
}
