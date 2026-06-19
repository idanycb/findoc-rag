package com.danycb.findocAnalyzer.features.identity.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "users")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING) // Default is ORDINAL, but STRING is safer for future changes
    @Column(nullable = false)
    private UserRole role;
}
