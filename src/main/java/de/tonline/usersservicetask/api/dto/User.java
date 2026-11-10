package de.tonline.usersservicetask.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Full user as returned by the users service. Unknown upstream fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record User(long id, String name, String username, String email, Address address,
                   String phone, String website, Company company) {
}
