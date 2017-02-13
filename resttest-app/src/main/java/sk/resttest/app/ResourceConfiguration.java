package sk.resttest.app;

import org.glassfish.jersey.server.ResourceConfig;

public class ResourceConfiguration extends ResourceConfig {
    public ResourceConfiguration() {
        packages("sk.resttest.app");
        register(TestResource.class);
        register(ExitResource.class);
    }
}
