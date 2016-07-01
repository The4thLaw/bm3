package org.the4thlaw.bm3;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.NotSupportedException;
import com.mpatric.mp3agic.UnsupportedTagException;

public class Cover {
    private static final Logger LOGGER = LoggerFactory.getLogger(Cover.class);
    private static final Pattern FOLDER_PATTERN = Pattern.compile("folder\\.(png|jpg|jpeg)",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_SIZE = 500;
    private static final int COVER_CACHE_SIZE = 50;
    private static final Cache<String, byte[]> COVER_CACHE = CacheBuilder.newCache(String.class, byte[].class)
            .name("Covers").eternal(true).maxSize(COVER_CACHE_SIZE).build();

    public static Cover forMusicFile(File file) {
        File sourceParent = file.getParentFile();
        File coverFile = null;

        // Is it named like the file, with a supported extension ?
        String basename = FilenameUtils.getBaseName(file.getName());
        coverFile =
                findFile(sourceParent,
                        Pattern.compile(Pattern.quote(basename) + "\\.(png|jpg|jpeg)", Pattern.CASE_INSENSITIVE));

        if (coverFile == null) {
            // Is there a folder.jpg file?
            coverFile = findFile(sourceParent, FOLDER_PATTERN);
        }

        return coverFile == null ? null : new Cover(coverFile.getAbsoluteFile());
    }

    private static File findFile(File directory, Pattern pattern) {
        Collection<File> potentialFiles =
                FileUtils.listFiles(directory, new RegexFileFilter(pattern), FalseFileFilter.INSTANCE);
        if (!potentialFiles.isEmpty()) {
            return potentialFiles.iterator().next();
        }
        return null;
    }

    private final File coverFile;

    private Cover(File coverFile) {
        this.coverFile = coverFile;
    }

    public byte[] getBytes() {
        String coverPath = coverFile.toString();

        byte[] cachedCover = COVER_CACHE.peek(coverPath);
        if (cachedCover != null) {
            LOGGER.debug("Cache hit for {}", coverFile);
            return cachedCover;
        }

        BufferedImage coverImg;
        try {
            coverImg = ImageIO.read(coverFile);
        } catch (IOException e1) {
            LOGGER.warn("Failed to save cover at {}", coverFile);
            return null;
        }

        // Convert non-RGB to RGB if needed
        if (coverImg.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage convertedImg =
                    new BufferedImage(coverImg.getWidth(), coverImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            convertedImg.createGraphics().drawImage(coverImg, 0, 0, Color.BLACK, null);
            coverImg = convertedImg;
        }

        // Resize if needed
        if (coverImg.getWidth() > MAX_SIZE || coverImg.getHeight() > MAX_SIZE) {
            BufferedImage resizedImg =
                    Scalr.resize(coverImg, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.AUTOMATIC, MAX_SIZE, MAX_SIZE,
                            Scalr.OP_ANTIALIAS);
            coverImg = resizedImg;
        }

        // Write final result to a byte stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(coverImg, "jpg", baos);
        } catch (IOException e) {
            LOGGER.warn("Failed to save cover at {}", coverFile);
            return null;
        }

        byte[] bytes = baos.toByteArray();
        COVER_CACHE.put(coverPath, bytes);
        return bytes;
    }

    public void writeToFile(File sourceFile, File targetFile) throws IOException {
        Mp3File mp3;
        try {
            mp3 = new Mp3File(sourceFile);
        } catch (UnsupportedTagException | InvalidDataException | IOException e) {
            LOGGER.error("Failed to open file as MP3", e);
            throw new IOException("Failed to open file as MP3", e);
        }
        ID3v2 tag;
        if (!mp3.hasId3v2Tag()) {
            tag = new ID3v24Tag();
            mp3.setId3v2Tag(tag);
        } else {
            tag = mp3.getId3v2Tag();
        }

        tag.setAlbumImage(getBytes(), "image/jpeg");

        try {
            mp3.save(targetFile.getAbsolutePath());
        } catch (NotSupportedException | IOException e) {
            LOGGER.error("Failed to save file as MP3", e);
            throw new IOException("Failed to save file as MP3", e);
        }
    }

    /**
     * Gets the last modification time of the cover. Follows the contract of {@link File#lastModified()}.
     * 
     * @return the modification time.
     */
    public long lastModified() {
        return coverFile.lastModified();
    }
}
