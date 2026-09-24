package com.nikita_ovramenko.sping_all_purpose_server;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import com.nikita_ovramenko.sping_all_purpose_server.app_user.model.AppUser;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.model.EmailVerification;
import com.nikita_ovramenko.sping_all_purpose_server.client.model.Client;
import com.nikita_ovramenko.sping_all_purpose_server.job.model.Job;
import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.model.JobLineItem;
import com.nikita_ovramenko.sping_all_purpose_server.location.model.Location;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization;
import com.nikita_ovramenko.sping_all_purpose_server.organizationserviceoffering.model.OrganizationServiceOffering;
import com.nikita_ovramenko.sping_all_purpose_server.quote.model.Quote;
import com.nikita_ovramenko.sping_all_purpose_server.quotelineitem.model.QuoteLineItem;
import com.nikita_ovramenko.sping_all_purpose_server.review.model.Review;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.model.ServiceOffering;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.model.UserOrganization;

/**
 * Development utility: writes the DDL Hibernate expects for the current mappings,
 * without needing a database.
 *
 * <p>Used to author the Flyway baseline so that ddl-auto=validate agrees with it.
 * Not a test -- run it by hand when the entity mappings change:
 * {@code java -cp <test-classpath> com...SchemaExporter <output.sql>}
 */
public final class SchemaExporter {

    private SchemaExporter() {
    }

    public static void main(String[] args) {
        String target = args.length > 0 ? args[0] : "hibernate-schema.sql";

        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // No database is available at generation time; without this Hibernate tries to
        // read JDBC metadata to pick defaults.
        settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", target);
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        settings.put("hibernate.format_sql", "true");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(AppUser.class)
                    .addAnnotatedClass(EmailVerification.class)
                    .addAnnotatedClass(Client.class)
                    .addAnnotatedClass(Location.class)
                    .addAnnotatedClass(Organization.class)
                    .addAnnotatedClass(ServiceOffering.class)
                    .addAnnotatedClass(OrganizationServiceOffering.class)
                    .addAnnotatedClass(UserOrganization.class)
                    .addAnnotatedClass(Quote.class)
                    .addAnnotatedClass(QuoteLineItem.class)
                    .addAnnotatedClass(Job.class)
                    .addAnnotatedClass(JobLineItem.class)
                    .addAnnotatedClass(Review.class)
                    .buildMetadata();

            // Building the factory is what triggers script generation.
            metadata.buildSessionFactory().close();

            System.out.println("Wrote schema to " + target);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
