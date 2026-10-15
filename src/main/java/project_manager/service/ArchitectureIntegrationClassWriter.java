package project_manager.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import project_manager.domain.ArchitectureIntegrationEntity;
import project_manager.domain.IntegrationDataClassEntity;
import project_manager.domain.IntegrationDataClassId;
import java.util.Set;

/** Caller holds the integration lock (or owns the new row) and a write transaction. */
@Component
public class ArchitectureIntegrationClassWriter {
    private final EntityManager em;
    public ArchitectureIntegrationClassWriter(EntityManager em) { this.em = em; }

    public void replace(ArchitectureIntegrationEntity integration, Set<String> codes) {
        em.createQuery("delete from IntegrationDataClassEntity d where d.id.integrationId=:id")
            .setParameter("id", integration.getId()).executeUpdate();
        for (String code : codes) {
            var row = new IntegrationDataClassEntity();
            row.setId(new IntegrationDataClassId(integration.getId(), code)); row.setIntegration(integration);
            em.persist(row);
        }
    }
}
