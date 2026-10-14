package project_manager.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import project_manager.domain.*;
import java.util.Set;

/** Explicit set replacement inside the caller's write transaction; no cascades. */
@Component
public class ArchitecturePassportSetWriter {
    private final EntityManager em;
    public ArchitecturePassportSetWriter(EntityManager em) { this.em = em; }

    public void dataClasses(ArchitecturePassportEntity passport, Set<String> codes) {
        em.createQuery("delete from ArchitectureDataClassEntity d where d.id.projectId=:id")
            .setParameter("id", passport.getProjectId()).executeUpdate();
        for (String code : codes) {
            var entry = new ArchitectureDataClassEntity();
            entry.setId(new ArchitectureDataClassId(passport.getProjectId(), code)); entry.setPassport(passport);
            em.persist(entry);
        }
    }
    public void environments(ArchitecturePassportEntity passport, Set<String> codes) {
        em.createQuery("delete from ArchitectureEnvironmentEntity e where e.id.projectId=:id")
            .setParameter("id", passport.getProjectId()).executeUpdate();
        for (String code : codes) {
            var entry = new ArchitectureEnvironmentEntity();
            entry.setId(new ArchitectureEnvironmentId(passport.getProjectId(), code)); entry.setPassport(passport);
            em.persist(entry);
        }
    }
}
