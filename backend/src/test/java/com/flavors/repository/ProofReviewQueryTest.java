package com.flavors.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.flavors.entity.Campaign;
import com.flavors.entity.CampaignDeliverable;
import com.flavors.entity.ProofReview;
import com.flavors.entity.ProofSubmission;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/** Checks HQL translation against entity metadata without a database; schema correctness remains an IT concern. */
class ProofReviewQueryTest {

    @Test
    void historyAndOverdueQueriesTranslateForPostgresWithoutOpeningADatabase() throws Exception {
        String overdue = CampaignRepository.class.getMethod("findOverdueProofCampaignIds", Instant.class, Pageable.class)
                .getAnnotation(Query.class).value();
        String history = ProofReviewRepository.class.getMethod("findByCampaignId", Long.class)
                .getAnnotation(Query.class).value();
        String ownedLock = CampaignRepository.class.getMethod("findByIdAndBrandIdForUpdate", Long.class, Long.class)
                .getAnnotation(Query.class).value();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try (SessionFactory factory = new MetadataSources(registry)
                .addAnnotatedClass(Campaign.class)
                // Campaign maps its deliverables (EEC-40), so Hibernate needs that entity to build the model.
                .addAnnotatedClass(CampaignDeliverable.class)
                .addAnnotatedClass(ProofSubmission.class)
                .addAnnotatedClass(ProofReview.class)
                .buildMetadata().buildSessionFactory();
             var session = factory.openSession()) {
            assertThatCode(() -> session.createQuery(overdue, Long.class).setParameter("now", Instant.now()))
                    .doesNotThrowAnyException();
            assertThatCode(() -> session.createQuery(history, ProofReview.class).setParameter("campaignId", 42L))
                    .doesNotThrowAnyException();
            assertThatCode(() -> session.createQuery(ownedLock, Campaign.class)
                    .setParameter("id", 42L).setParameter("brandId", 9L)).doesNotThrowAnyException();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
