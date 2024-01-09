package org.the4thlaw.bm3;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

import org.apache.commons.io.filefilter.AbstractFileFilter;

/**
 * A FileFilter that checks if the listed files, relative to their root, are contained in a set of known paths.
 */
public class NotInSourceFileFilter extends AbstractFileFilter {
	private final Set<String> includedPaths;
	private final Path targetPath;

	public NotInSourceFileFilter(Set<String> includedPaths, Path targetPath) {
		this.includedPaths = includedPaths;
		this.targetPath = targetPath;
	}

	@Override
	public boolean accept(File file) {
		String name = file.getName().toLowerCase();
		if (!(name.endsWith(".mp3")) || name.endsWith(".m4a") || name.endsWith(".wma") || name.endsWith(".aac")) {
			// Only filter out known audio files
			return false;
		}
		String relativeFile = targetPath.relativize(file.toPath()).toString();
		return !includedPaths.contains(relativeFile);
	}
}