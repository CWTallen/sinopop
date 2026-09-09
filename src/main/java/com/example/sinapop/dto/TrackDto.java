package com.example.sinapop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TrackDto(
        @JsonProperty("track_no") Integer trackNo,
        String title,
        String lyricist,
        String composer,
        List<String> lyrics
) {
}
