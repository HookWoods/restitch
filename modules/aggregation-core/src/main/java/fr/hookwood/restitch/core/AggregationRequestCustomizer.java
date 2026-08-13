package fr.hookwood.restitch.core;

import java.net.URI;
import java.util.Map;

@FunctionalInterface
public interface AggregationRequestCustomizer {
    Map<String, String> customize(ClientProfile clientProfile, ResolverProfile resolverProfile, URI requestUri, Map<String, String> headers);
}
