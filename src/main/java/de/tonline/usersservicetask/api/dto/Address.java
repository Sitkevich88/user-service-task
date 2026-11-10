package de.tonline.usersservicetask.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Address(String street, String suite, String city, String zipcode, Geo geo) {
}
