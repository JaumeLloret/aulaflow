package es.aulaflow.application.auth;

public interface PasswordHasher {

    PasswordVerifier hash(char[] password);

    boolean verify(
            char[] password,
            PasswordVerifier passwordVerifier
    );

    PasswordVerifier createDummyVerifier();
}
