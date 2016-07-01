package org.the4thlaw.bm3;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaylistReader implements Closeable, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlaylistReader.class);

	private final BufferedReader reader;
	private final File playlistDirectory;

	public PlaylistReader(File playlist) throws FileNotFoundException {
		reader = new BufferedReader(new InputStreamReader(new FileInputStream(playlist), Charsets.UTF_8));
		playlistDirectory = playlist.getParentFile();
	}

	public File getEntry() throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (!line.startsWith("#")) {
				break;
			}
		}
		if (line == null) {
			return null;
		}

		File musicFile = new File(line);
		if (!musicFile.isAbsolute()) {
			musicFile = new File(playlistDirectory, line);
		}
		musicFile = musicFile.getCanonicalFile();

		LOGGER.debug("Found file {}", musicFile);

		return musicFile;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}
}