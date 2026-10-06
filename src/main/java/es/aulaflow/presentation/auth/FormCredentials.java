package es.aulaflow.presentation.auth;

import java.util.Arrays;

record FormCredentials(
        String username,
        char[] password
) implements AutoCloseable {

    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }
}
