package com.hitstdio.fhir.server.r4;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.api.config.ThreadPoolFactoryConfig;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.binary.api.IBinaryStorageSvc;
import ca.uhn.fhir.jpa.binstore.MemoryBinaryStorageSvcImpl;
import ca.uhn.fhir.jpa.config.HapiJpaConfig;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import ca.uhn.fhir.jpa.config.util.HapiEntityManagerFactoryUtil;
import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import ca.uhn.fhir.jpa.model.dialect.HapiFhirOracleDialect;
import ca.uhn.fhir.jpa.model.dialect.HapiFhirSQLServerDialect;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig;
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig;
import ca.uhn.fhir.jpa.util.CircularQueueCaptureQueriesListener;
import ca.uhn.fhir.jpa.util.CurrentThreadCaptureQueriesListener;
import jakarta.persistence.EntityManagerFactory;
import net.ttddyy.dsproxy.listener.SingleQueryCountHolder;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.apache.commons.dbcp2.BasicDataSource;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

@Configuration
@PropertySource("classpath:config.properties")
@Import({
	JpaR4Config.class,
	HapiJpaConfig.class,
	JpaBatch2Config.class,
	Batch2JobsConfig.class,
	SubscriptionSubmitterConfig.class,
	SubscriptionProcessorConfig.class,
	SubscriptionChannelConfig.class,
	ThreadPoolFactoryConfig.class
	//SmartConfig.class,
	//JwtSecurityConfig.class
})
public class TestJpaR4Config extends TestJpaConfig {

	private static final Logger ourLog = LoggerFactory.getLogger(TestJpaR4Config.class);

	private static final int OUR_MAX_THREADS = 20;

	@Bean
	public CircularQueueCaptureQueriesListener captureQueriesListener() {
		return new CircularQueueCaptureQueriesListener();
	}

    @Value("${db.url}")
    private String dbUrl;

    @Value("${db.username}")
    private String dbUsername;

    @Value("${db.password}")
    private String dbPassword;

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean("jasyptStringEncryptor")
    public StandardPBEStringEncryptor stringEncryptor() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        String password = System.getenv("FHIRSERVER_PASSWORD");
        
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("Secret Key (FHIRSERVER_PASSWORD) is not set or empty");
        }
        
        encryptor.setPassword(password);
        
        return encryptor;
    }

    private String decrypt(String value, StandardPBEStringEncryptor encryptor) {
        if (value != null && value.startsWith("ENC(") && value.endsWith(")")) {
            return encryptor.decrypt(value.substring(4, value.length() - 1));
        }
        return value;
    }    
    
	@Bean
	public DataSource dataSource() {
		 StandardPBEStringEncryptor encryptor = stringEncryptor();
		
		BasicDataSource retVal = new BasicDataSource();

		/*retVal.setDriver(new com.microsoft.sqlserver.jdbc.SQLServerDriver());*/
		retVal.setDriver(new oracle.jdbc.OracleDriver());
		retVal.setUrl(dbUrl);	
        retVal.setUsername(decrypt(dbUsername, encryptor));
        retVal.setPassword(decrypt(dbPassword, encryptor));

		retVal.setMaxWait(Duration.ofMillis(30000));
		retVal.setMaxTotal(OUR_MAX_THREADS);

		SLF4JLogLevel logLevel = SLF4JLogLevel.INFO;
		return ProxyDataSourceBuilder.create(retVal)
				//			.logQueryBySlf4j(level)
				.logSlowQueryBySlf4j(10, TimeUnit.SECONDS, logLevel)
				.afterQuery(captureQueriesListener())
				.afterQuery(new CurrentThreadCaptureQueriesListener())
				.countQuery(singleQueryCountHolder())
				.afterMethod(captureQueriesListener())
				.build();
	}

	@Bean
	public SingleQueryCountHolder singleQueryCountHolder() {
		return new SingleQueryCountHolder();
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
			ConfigurableListableBeanFactory theConfigurableListableBeanFactory,
			FhirContext theFhirContext,
			JpaStorageSettings theStorageSettings) {

		LocalContainerEntityManagerFactoryBean retVal = HapiEntityManagerFactoryUtil.newEntityManagerFactory(
				theConfigurableListableBeanFactory, theFhirContext, theStorageSettings);
		retVal.setPersistenceUnitName("PU_HapiFhirJpaR4");
		retVal.setDataSource(dataSource());
		retVal.setJpaProperties(jpaProperties());
		return retVal;
	}

	private Properties jpaProperties() {
		Properties extraProperties = new Properties();
		extraProperties.put("hibernate.search.enabled", "false");
		extraProperties.put("hibernate.format_sql", "false");
		extraProperties.put("hibernate.show_sql", "false");
		extraProperties.put("hibernate.hbm2ddl.auto", "update");
		extraProperties.put("hibernate.dialect", HapiFhirOracleDialect.class.getName());
		/*extraProperties.put("hibernate.dialect", HapiFhirSQLServerDialect.class.getName());*/

		ourLog.info("jpaProperties: {}", extraProperties);

		return extraProperties;
	}

	@Bean
	public JpaStorageSettings storageSettings() {
		JpaStorageSettings storageSettings = new JpaStorageSettings();
		storageSettings.setResourceServerIdStrategy(JpaStorageSettings.IdStrategyEnum.UUID);
		storageSettings.setIndexMissingFields(JpaStorageSettings.IndexEnabledEnum.ENABLED);
		storageSettings.setAutoCreatePlaceholderReferenceTargets(true);
		storageSettings.setAllowInlineMatchUrlReferences(true);
		storageSettings.setDeleteEnabled(false);
		storageSettings.setMassIngestionMode(true);
		return storageSettings;
	}

	@Bean
	public PartitionSettings partitionSettings() {
		return new PartitionSettings();
	}

	@Bean
	@Primary
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		JpaTransactionManager retVal = new JpaTransactionManager();
		retVal.setEntityManagerFactory(entityManagerFactory);
		return retVal;
	}

	@Bean
	@Lazy
	public IBinaryStorageSvc binaryStorage() {
		return new MemoryBinaryStorageSvcImpl();
	}
}
