package io.github.hookwoods.restitch.api;

/**
 * Pagination data extracted from a configured root response.
 *
 * @param totalItems total matching items, when supplied by the downstream service
 * @param page zero- or one-based page number as supplied by the downstream service
 * @param pageSize requested or returned page size
 * @param totalPages total available pages
 */
public record PageMetadata(long totalItems, int page, int pageSize, int totalPages) {}
