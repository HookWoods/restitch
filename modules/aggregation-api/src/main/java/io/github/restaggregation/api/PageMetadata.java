package io.github.restaggregation.api;

public record PageMetadata(long totalItems, int page, int pageSize, int totalPages) {}
