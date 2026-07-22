package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ObjectKeyParserTest {

    @Test
    void parse_validKey_returnsDocId() {
        UUID docId = UUID.randomUUID();

        S3ObjectKeyParser.KeyParts parts = S3ObjectKeyParser.parse("files/" + docId);

        assertThat(parts.docId()).isEqualTo(docId);
    }

    @Test
    void parse_tooFewSegments_throwsInvalidS3KeyException() {
        assertThatThrownBy(() -> S3ObjectKeyParser.parse("files"))
                .isInstanceOf(InvalidS3KeyException.class);
    }

    @Test
    void parse_tooManySegments_throwsInvalidS3KeyException() {
        UUID docId = UUID.randomUUID();

        assertThatThrownBy(() -> S3ObjectKeyParser.parse("files/sub/" + docId))
                .isInstanceOf(InvalidS3KeyException.class);
    }

    @Test
    void parse_nonUuidSecondSegment_throwsInvalidS3KeyException() {
        assertThatThrownBy(() -> S3ObjectKeyParser.parse("files/not-a-uuid"))
                .isInstanceOf(InvalidS3KeyException.class);
    }

    @Test
    void parse_emptyKey_throwsInvalidS3KeyException() {
        assertThatThrownBy(() -> S3ObjectKeyParser.parse(""))
                .isInstanceOf(InvalidS3KeyException.class);
    }

    @Test
    void parse_blankKey_throwsInvalidS3KeyException() {
        assertThatThrownBy(() -> S3ObjectKeyParser.parse("   "))
                .isInstanceOf(InvalidS3KeyException.class);
    }
}
