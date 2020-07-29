package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ResourceBundleModule extends BaseLifecycleComponentModule implements ExposedKeyModule<ResourceBundle> {
    private static final Logger logger = LoggerFactory.getLogger(ResourceBundleModule.class);

    public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("i18n.Resources", new ResourceBundle.Control() {
        @Override
        public List<Locale> getCandidateLocales(String baseName, Locale locale) {
            var candidateLocales = super.getCandidateLocales(baseName, locale);
            logger.info("Candidate locales: {}", candidateLocales);
            return candidateLocales;
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            // coarse support for CHS/CHT
            checkNotNull(baseName);
            if ("zh".equals(locale.getLanguage())) {
                if ("CHS".equals(locale.getVariant().toUpperCase()) || "CHS".equals(locale.getCountry().toUpperCase())) {
                    return new Locale("zh", "HANS");
                }
                if ("CHT".equals(locale.getVariant().toUpperCase()) || "CHT".equals(locale.getCountry().toUpperCase())) {
                    return new Locale("zh", "HANT");
                }
            }
            return super.getFallbackLocale(baseName, locale);
        }
    });

    static {
        logger.info("Using resource bundle locale {}", RESOURCE_BUNDLE.getLocale());
    }

    @Override
    protected void configure() {
        bind(getExposedKey()).toInstance(RESOURCE_BUNDLE);
        expose(getExposedKey());
    }
}