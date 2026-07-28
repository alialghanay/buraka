package org.ali.projects.buraka.catalog.brand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    protected Brand() {
    }

    public Brand(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }
}
