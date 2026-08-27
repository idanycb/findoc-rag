package com.danycb.findocAnalyzer.features.vault.application.out;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRepositoryPortContractTest {

    @Test
    void accessionQueriesAreRequiredAdapterOperationsRatherThanSilentDefaults() throws Exception {
        var exactAccession = DocumentRepositoryPort.class.getMethod(
                "findByTeamIdAndAccessionNumber", UUID.class, String.class);
        var amendments = DocumentRepositoryPort.class.getMethod(
                "findByTeamIdAndAmendsAccessionNumber", UUID.class, String.class);

        assertThat(exactAccession.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(exactAccession.getModifiers())).isTrue();
        assertThat(amendments.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(amendments.getModifiers())).isTrue();
    }

    @Test
    void atomicInsertOrGetIsARequiredPersistenceOperationWithoutSpringExceptionFallback() throws Exception {
        var insertOrGet = DocumentRepositoryPort.class.getMethod(
                "insertOrGet", com.danycb.findocAnalyzer.features.vault.domain.Document.class);

        assertThat(insertOrGet.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(insertOrGet.getModifiers())).isTrue();

        String classResource = "/" + DocumentRepositoryPort.class.getName().replace('.', '/') + ".class";
        try (var bytecode = DocumentRepositoryPort.class.getResourceAsStream(classResource)) {
            assertThat(bytecode).isNotNull();
            assertThat(new String(bytecode.readAllBytes(), StandardCharsets.ISO_8859_1))
                    .doesNotContain("org/springframework/dao/DataIntegrityViolationException");
        }
    }

    @Test
    void analysisPublicationStateIsNotPartOfDocumentPersistence() {
        assertThat(DocumentRepositoryPort.class.getMethods())
                .extracting(method -> method.getName())
                .doesNotContain("claimAnalysisPublication", "releaseAnalysisPublication");
    }
}
