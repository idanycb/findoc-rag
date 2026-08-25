package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorIndexAdapterContractTest {

    @Test
    void everyConstructorRequiresExplicitAtomicVectorPersistence() {
        var constructors = PgVectorIndexAdapter.class.getDeclaredConstructors();

        assertThat(constructors).isNotEmpty();
        assertThat(Arrays.stream(constructors)
                .allMatch(constructor -> Arrays.asList(constructor.getParameterTypes())
                        .contains(DocumentVectorPersistence.class))).isTrue();
        assertThat(PgVectorIndexAdapter.class.getDeclaredConstructors())
                .noneSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(EmbeddingModel.class, EmbeddingStore.class));
    }
}
