package de.tonline.usersservicetask.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Geo(String lat, String lng) {
}
