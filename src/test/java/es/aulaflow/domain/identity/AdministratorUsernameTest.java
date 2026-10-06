package es.aulaflow.domain.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdministratorUsernameTest {

    @Test
    void normalizesCaseAndSurroundingWhitespace() {
        AdministratorUsername username =
                new AdministratorUsername(
                        "  AulaFlow.Admin  "
                );

        assertEquals(
                "aulaflow.admin",
                username.value()
        );
    }

    @Test
    void acceptsDocumentedCharacters() {
        AdministratorUsername username =
                new AdministratorUsername(
                        "admin_1-test"
                );

        assertEquals(
                "admin_1-test",
                username.value()
        );
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdministratorUsername(
                        "administrador escolar"
                )
        );
    }

    @Test
    void rejectsTooShortUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdministratorUsername("ab")
        );
    }
}
