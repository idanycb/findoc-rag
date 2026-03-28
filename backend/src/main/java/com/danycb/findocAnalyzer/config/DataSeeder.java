package com.danycb.findocAnalyzer.config;

import com.danycb.findocAnalyzer.tenant.Tenant;
import com.danycb.findocAnalyzer.tenant.TenantOnboardingService;
import com.danycb.findocAnalyzer.tenant.TenantRepository;
import com.danycb.findocAnalyzer.user.User;
import com.danycb.findocAnalyzer.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(TenantRepository tenantRepo, UserRepository userRepo, PasswordEncoder encoder, TenantOnboardingService onboardingService) {
        return args -> {
            if (tenantRepo.count() == 0 && userRepo.count() == 0) {
                Tenant tenant = onboardingService.onboardTenant("Demo Tenant");

                User user = User.builder()
                        .username("Demo User")
                        .password(encoder.encode("password"))
                        .tenantId(tenant.getId())
                        .build();

                userRepo.save(user);

                log.info("Seeded default tenant + user");
            }
        };
    }
}
