package com.hitstdio.fhir.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatabaseConfig {
	
   private DatabaseConfig() {
		    throw new IllegalStateException("Utility class");
	}
   
	private static final Logger ourLog = LoggerFactory.getLogger(DatabaseConfig.class);
	
    private static Properties properties = new Properties();

	private static ClassLoader safeGetClassLoader(Class<?> clazz) {
		ClassLoader classLoader = clazz.getClassLoader();
		if (classLoader == null) {
			return getDefaultClassLoader();
		}
		return classLoader;
	}

	private static ClassLoader getDefaultClassLoader() {
		return ClassLoader.getSystemClassLoader();
	}

	static {
		try {
			ClassLoader classLoader = safeGetClassLoader(DatabaseConfig.class);
			try (InputStream input = classLoader.getResourceAsStream("config.properties")) {
				if (input != null) {
					properties.load(input);
				}
			}
		} catch (IOException e) {
			ourLog.error("DatabaseConfig error");
		}
	}

    public static String getFhirId() {
        return properties.getProperty("fhir.id");
    }

    public static String getFhirBaseUrl() {
        return properties.getProperty("fhir.baseUrl");
    }

    public static String getFhirName() {
        return properties.getProperty("fhir.name");
    }    
}

