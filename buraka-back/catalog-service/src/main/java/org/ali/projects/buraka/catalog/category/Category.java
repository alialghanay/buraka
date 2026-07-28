package org.ali.projects.buraka.catalog.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    private Long parentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    protected Category() {
    }

    public Category(Long parentId, String title, String slug) {
        this.parentId = parentId;
        this.title = title;
        this.slug = slug;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }
}
