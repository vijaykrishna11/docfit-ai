package com.docfitai.backend.config;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Runs {@link ProductionSafetyValidator} from {@code ApplicationEnvironmentPreparedEvent} --
 * before the {@code ApplicationContext} is created and therefore before any bean (DataSource,
 * Flyway, JwtService, ...) can be instantiated or attempt a network/database connection.
 *
 * <p>Order is set to run after {@link ConfigDataEnvironmentPostProcessor}, which is what loads
 * {@code application-prod.properties} and resolves active profiles in the first place; without
 * that ordering this listener would see neither.
 *
 * <p>Registered manually in {@code BackendApplication.main} (not component-scanned -- nothing has
 * been scanned yet at the point this needs to run).
 */
public class ProductionSafetyEnvironmentListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            return;
        }

        new ProductionSafetyValidator(
                resolve(environment, "docfitai.auth.jwt-secret"),
                resolveBoolean(environment, "docfitai.auth.cookie-secure"),
                resolve(environment, "docfitai.cors.allowed-origins"),
                resolveBoolean(environment, "docfitai.insurance.synthetic-demo.enabled"),
                resolveBoolean(environment, "docfitai.import.csv.enabled"));
    }

    /**
     * A required property (e.g. {@code JWT_SECRET}) with no default renders as an unresolved
     * {@code ${...}} placeholder rather than throwing here in every Spring version -- normalized
     * to null (and then treated as "missing" by the validator) either way.
     */
    private static String resolve(ConfigurableEnvironment environment, String key) {
        try {
            String value = environment.getProperty(key);
            return (value != null && value.contains("${")) ? null : value;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean resolveBoolean(ConfigurableEnvironment environment, String key) {
        try {
            return Boolean.TRUE.equals(environment.getProperty(key, Boolean.class, Boolean.FALSE));
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
