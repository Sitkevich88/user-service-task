package de.tonline.usersservicetask.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Company(String name, String catchPhrase, String bs) {
}
