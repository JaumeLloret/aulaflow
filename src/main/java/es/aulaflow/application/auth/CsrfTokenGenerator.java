package es.aulaflow.application.auth;

@FunctionalInterface
public interface CsrfTokenGenerator {

    CsrfToken generate();
}
