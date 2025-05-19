package org.the4thlaw.bm3;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.mpatric.mp3agic.AbstractID3v2Tag;
import com.mpatric.mp3agic.EncodedText;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v2Frame;
import com.mpatric.mp3agic.ID3v2FrameSet;
import com.mpatric.mp3agic.ID3v2TextFrameData;
import com.mpatric.mp3agic.InvalidDataException;

// Note: this class is kept for historical reasons but it seems that the issue is that EncodedText cannot
// deal with editors using \0 as a separator for multiple values in a single frame (namely, the artist)
// The library is no longer maintained so we can't merge the values as I would have wanted
public class ID3v24MultiValueTag {
    private final ID3v2 wrapped;
    public ID3v24MultiValueTag(ID3v2 wrapped) {
        this.wrapped = wrapped;
    }

    protected List<ID3v2TextFrameData> extractMultiTextFrameData(String id) {
        wrapped.getFrameSets().entrySet().forEach(e -> {
            System.err.println("\n" + e.getKey() + " --> " +
                e.getValue().getFrames().stream()
                .map(ID3v2Frame.class::cast)
                .map(f -> {
                    try {
                        return  new ID3v2TextFrameData(false, f.getData());
                    } catch (InvalidDataException|RuntimeException e2) {
                       return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(ID3v2TextFrameData::getText)
                .filter(Objects::nonNull)
                .map(EncodedText::toString)
                .collect(Collectors.toList()));
        });
		ID3v2FrameSet frameSet = wrapped.getFrameSets().get(id);
		if (frameSet != null) {
            return frameSet.getFrames()
                .stream()
                .map(ID3v2Frame.class::cast)
                .map(f -> {
                    try {
                        return  new ID3v2TextFrameData(false, f.getData());
                    } catch (InvalidDataException e) {
                       return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

    public List<String> getArtists() {
        return extractMultiTextFrameData(wrapped.getObseleteFormat() ? AbstractID3v2Tag.ID_ARTIST_OBSELETE : AbstractID3v2Tag.ID_ARTIST)
            .stream()
            .map(ID3v2TextFrameData::getText)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.toList());
	}
}
