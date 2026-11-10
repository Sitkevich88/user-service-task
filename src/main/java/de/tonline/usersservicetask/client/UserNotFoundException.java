package de.tonline.usersservicetask.client;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long id) {
        super("User " + id + " not found");
    }
}
