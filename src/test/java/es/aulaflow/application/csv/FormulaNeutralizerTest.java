package es.aulaflow.application.csv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormulaNeutralizerTest {

    @Test
    void protectsEqualsPrefixedFormula() {
        assertEquals(
                "'=1+1",
                FormulaNeutralizer.protect("=1+1")
        );
    }

    @Test
    void protectsPlusPrefixedFormula() {
        assertEquals(
                "'+SUM(A1:A2)",
                FormulaNeutralizer.protect("+SUM(A1:A2)")
        );
    }

    @Test
    void protectsMinusPrefixedFormula() {
        assertEquals(
                "'-1+2",
                FormulaNeutralizer.protect("-1+2")
        );
    }

    @Test
    void protectsAtPrefixedFormula() {
        assertEquals(
                "'@cmd",
                FormulaNeutralizer.protect("@cmd")
        );
    }

    @Test
    void duplicatesLeadingApostrophe() {
        assertEquals(
                "''=1+1",
                FormulaNeutralizer.protect("'=1+1")
        );
        assertEquals(
                "''texto",
                FormulaNeutralizer.protect("'texto")
        );
    }

    @Test
    void leavesPlainTextUnchanged() {
        assertEquals(
                "texto",
                FormulaNeutralizer.protect("texto")
        );
    }

    @Test
    void protectsDangerousCharacterAfterLeadingSpaces() {
        assertEquals(
                "'  =1+1",
                FormulaNeutralizer.protect("  =1+1")
        );
    }

    @Test
    void protectsDangerousCharacterAfterLeadingTab() {
        assertEquals(
                "'\t=1+1",
                FormulaNeutralizer.protect("\t=1+1")
        );
    }

    @Test
    void reversesEqualsPrefixedFormula() {
        assertEquals(
                "=1+1",
                FormulaNeutralizer.unprotect("'=1+1")
        );
    }

    @Test
    void reversesDuplicatedApostrophe() {
        assertEquals(
                "'=1+1",
                FormulaNeutralizer.unprotect("''=1+1")
        );
        assertEquals(
                "'texto",
                FormulaNeutralizer.unprotect("''texto")
        );
    }

    @Test
    void reversesLeadingSpacesBeforeDangerousCharacter() {
        assertEquals(
                "  =1+1",
                FormulaNeutralizer.unprotect("'  =1+1")
        );
    }

    @Test
    void leavesPlainTextUnchangedOnImport() {
        assertEquals(
                "texto",
                FormulaNeutralizer.unprotect("texto")
        );
    }

    @Test
    void leavesLoneApostropheWithoutDangerousContentUnchanged() {
        assertEquals(
                "'hola",
                FormulaNeutralizer.unprotect("'hola")
        );
    }

    @Test
    void roundTripsAllContractExamples() {
        String[] originals = {
                "=1+1",
                "+SUM(A1:A2)",
                "-1+2",
                "@cmd",
                "'=1+1",
                "'texto",
                "texto",
                "  =1+1"
        };

        for (String original : originals) {
            String protectedValue =
                    FormulaNeutralizer.protect(original);
            String restored =
                    FormulaNeutralizer.unprotect(
                            protectedValue
                    );

            assertEquals(
                    original,
                    restored,
                    "Round trip fallido para: " + original
            );
        }
    }
}
