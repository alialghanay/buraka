package org.ali.projects.buraka.shared.messaging;

public record ReviewEvent(Long reviewId, Long productId, int rating, Type type) {

    public enum Type {
        CREATED, DELETED
    }
}
