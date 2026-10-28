package baosys.manager.app.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

public final class CatalogFixture {

    private CatalogFixture() {}

    public static SystemCatalog load() {
        try {
            var sources = new YamlPropertySourceLoader().load(
                "catalog",
                new ClassPathResource("manager-catalog-test.yml")
            );
            return new Binder(ConfigurationPropertySources.from(sources))
                .bind("baosys", SystemCatalog.class)
                .get();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
