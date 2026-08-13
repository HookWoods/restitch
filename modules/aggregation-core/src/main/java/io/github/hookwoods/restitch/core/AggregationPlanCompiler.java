package io.github.hookwoods.restitch.core;

import io.github.hookwoods.restitch.api.AggregateRef;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compiles and validates aggregation configuration against a root DTO type. */
public final class AggregationPlanCompiler {
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{([^}]+)}");

    private final AggregationLimits limits;
    private final Set<String> rootVariables;

    /** Creates a compiler with default safety limits and no root template variables. */
    public AggregationPlanCompiler() {
        this(AggregationLimits.defaults(), Set.of());
    }

    /**
     * Creates a compiler with the supplied safety limits and no root template variables.
     *
     * @param limits limits applied while compiling plans
     */
    public AggregationPlanCompiler(AggregationLimits limits) {
        this(limits, Set.of());
    }

    /**
     * Creates a compiler with supplied limits and allowed root path-template variables.
     *
     * @param limits limits applied while compiling plans
     * @param rootVariables root values allowed in resolver path templates
     */
    public AggregationPlanCompiler(AggregationLimits limits, Set<String> rootVariables) {
        this.limits = limits == null ? AggregationLimits.defaults() : limits;
        this.rootVariables = Set.copyOf(rootVariables == null ? Set.of() : rootVariables);
    }

    /**
     * Validates resolver references and path templates, then returns an immutable plan.
     *
     * @param rootType DTO type containing aggregate references
     * @param resolvers resolver profiles keyed by their names
     * @return validated immutable aggregation plan
     */
    public AggregationPlan compile(Class<?> rootType, Map<String, ResolverProfile> resolvers) {
        Map<String, ResolverProfile> configuredResolvers = Map.copyOf(resolvers == null ? Map.of() : resolvers);
        scan(rootType, configuredResolvers, new HashSet<>(), new HashSet<>(), 0);
        return new AggregationPlan(rootType, configuredResolvers, limits);
    }

    private void scan(
            Class<?> type,
            Map<String, ResolverProfile> resolvers,
            Set<Class<?>> seenTypes,
            Set<String> seenTargets,
            int depth) {
        if (depth > limits.maxDepth()) {
            throw new IllegalArgumentException("aggregation plan exceeds maxDepth");
        }
        if (type == null || type.isPrimitive() || type.getName().startsWith("java.") || !seenTypes.add(type)) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            AggregateRef aggregateRef = field.getAnnotation(AggregateRef.class);
            if (aggregateRef == null) {
                continue;
            }
            if (!seenTargets.add(type.getName() + "#" + field.getName())) {
                throw new IllegalArgumentException("duplicate aggregate target field " + field.getName());
            }
            ResolverProfile resolver = resolvers.get(aggregateRef.value());
            if (resolver == null) {
                throw new IllegalArgumentException("missing resolver profile " + aggregateRef.value());
            }
            validateTemplateVariables(resolver.path());
            scan(field.getType(), resolvers, seenTypes, seenTargets, depth + 1);
        }
    }

    private void validateTemplateVariables(String path) {
        Matcher matcher = TEMPLATE_VARIABLE.matcher(path);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!"id".equals(variable) && !rootVariables.contains(variable)) {
                throw new IllegalArgumentException("unsupported URI template variable " + variable);
            }
        }
    }
}
