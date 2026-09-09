package com.example.sinapop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AlbumDto(
        @JsonProperty("album_name") String albumName,
        String artist,
        @JsonProperty("release_year") Integer releaseYear,
        List<TrackDto> tracks
) {
}
