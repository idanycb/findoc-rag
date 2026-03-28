package com.danycb.findocAnalyzer.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantOnboardingService {
    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;

    @Transactional
    public Tenant onboardTenant(String tenantName) {
        Tenant tenant_b = Tenant.builder()
                .name(tenantName)
                .build();

        Tenant tenant = tenantRepository.save(tenant_b);

        // Create partition + HNSW index
        jdbcTemplate.execute(
                "SELECT create_document_embeddings_partition('" + tenant.getId() + "')"
        );

        return tenant;
    }
}
