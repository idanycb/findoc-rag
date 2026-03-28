package com.danycb.findocAnalyzer.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tenants")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {
    @Id
    @Column(name = "tenant_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;
}
