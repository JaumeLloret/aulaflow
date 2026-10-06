package es.aulaflow.presentation.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipartFormDataParserTest {

    @Test
    void boundaryLikeTextInsideFileIsNotTreatedAsDelimiter() {
        String boundary = "AaB03x";
        String csrf = "csrf-value";
        String csv = """
                aulaflow_version,record_type,board_name,column_name,column_position,card_title,card_description,card_position
                1,BOARD,Tauler --AaB03x-not-a-boundary,,,,,
                1,COLUMN,Tauler --AaB03x-not-a-boundary,Per fer,0,,,
                """;

        byte[] body = multipartBody(
                boundary,
                csrf,
                csv
        );

        MultipartFormData parsed =
                MultipartFormDataParser.parseBody(
                        body,
                        boundary,
                        Set.of("_csrf", "file")
                );

        assertEquals(csrf, parsed.fields().get("_csrf"));
        assertArrayEquals(
                csv.getBytes(StandardCharsets.UTF_8),
                parsed.file().orElseThrow()
        );
    }

    private static byte[] multipartBody(
            String boundary,
            String csrf,
            String csv
    ) {
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; "
                + "name=\"_csrf\"\r\n\r\n"
                + csrf + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; "
                + "name=\"file\"; filename=\"board.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + csv + "\r\n"
                + "--" + boundary + "--\r\n";

        return body.getBytes(StandardCharsets.UTF_8);
    }
}
