package com.danycb.findocAnalyzer.features.vault.application.out;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    void analysisPublicationClaimAndReleaseAreRequiredDatabaseOperations() {
        var methods = Arrays.asList(DocumentRepositoryPort.class.getMethods());
        assertThat(methods).extracting(Method::getName)
                .contains("claimAnalysisPublication", "releaseAnalysisPublication");
        Method claim = methods.stream()
                .filter(method -> method.getName().equals("claimAnalysisPublication"))
                .findFirst().orElseThrow();
        Method release = methods.stream()
                .filter(method -> method.getName().equals("releaseAnalysisPublication"))
                .findFirst().orElseThrow();

        assertThat(claim.getParameterTypes()).containsExactly(UUID.class);
        assertThat(claim.getReturnType()).isEqualTo(boolean.class);
        assertThat(claim.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(claim.getModifiers())).isTrue();
        assertThat(release.getParameterTypes()).containsExactly(UUID.class);
        assertThat(release.getReturnType()).isEqualTo(void.class);
        assertThat(release.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(release.getModifiers())).isTrue();
    }
}
