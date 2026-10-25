package project_manager.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitecturePortfolioQuery;
import project_manager.web.dto.ArchitecturePortfolioSummaryQuery;
import project_manager.web.dto.ArchitectureReadDtos.*;
import java.util.ArrayList;
import java.util.List;

/** Fixed scalar queries; never traverses lazy entities. Card/legacy IN lists are bounded to 500 IDs;
 * the D1 summary uses filtered subqueries with a constant number of SELECTs. */
@Repository
public class ArchitectureReadRepository {
    public static final int BATCH_SIZE = 500;
    private static final String SUMMARY_FILTERS = "(:quarter='all' or lower(p.quarter)=:quarter) "
        + "and (:status='all' or lower(p.status)=:status) "
        + "and (:query='' or locate(:query,lower(p.name))>0 or locate(:query,lower(p.summary))>0)";
    private final EntityManager em;
    public ArchitectureReadRepository(EntityManager em) { this.em = em; }

    public ProjectInfo project(String id) {
        return em.createQuery(projectSelect() + " where p.id=:id", ProjectInfo.class).setParameter("id", id)
            .getResultList().stream().findFirst().orElseThrow(ProjectMutationException::projectNotFound);
    }
    /** Caller has verified/locked the existing passport; scalar counts never load or modify children. */
    public Counts dependenciesOfExistingPassport(String id) {
        return new Counts(1,
            count("ArchitectureComponentEntity", "passport.projectId", id),
            count("ArchitectureIntegrationEntity", "passport.projectId", id),
            count("ArchitectureCheckEntity", "passport.projectId", id),
            count("SecurityRiskEntity", "passport.projectId", id),
            count("ArchitectureDataClassEntity", "id.projectId", id),
            count("ArchitectureEnvironmentEntity", "id.projectId", id));
    }
    private long count(String entity, String owner, String id) {
        // Both query fragments are internal constants, never request input.
        return em.createQuery("select count(r) from " + entity + " r where r." + owner + "=:id", Long.class)
            .setParameter("id", id).getSingleResult();
    }
    /** Only project deletion dependencies: no full aggregate or unused membership collections. */
    public Counts projectDeletionCounts(String id) {
        return new Counts(count("ArchitecturePassportEntity","projectId",id),
            count("ArchitectureComponentEntity","passport.projectId",id),
            count("ArchitectureIntegrationEntity","passport.projectId",id),
            count("ArchitectureCheckEntity","passport.projectId",id),
            count("SecurityRiskEntity","passport.projectId",id),0,0);
    }
    public List<String> quarters() {
        return em.createQuery("select distinct lower(trim(p.quarter)) from ProjectEntity p", String.class).getResultList();
    }
    public List<ProjectInfo> projects(ArchitecturePortfolioQuery q) {
        return em.createQuery(projectSelect() + " where (:quarter='all' or lower(trim(p.quarter))=:quarter) "
            + "and (:status='all' or lower(trim(p.status))=:status) "
            + "and (:query='' or locate(:query,lower(p.name))>0 or locate(:query,lower(p.owner))>0)", ProjectInfo.class)
            .setParameter("quarter", q.quarter()).setParameter("status", q.projectStatus())
            .setParameter("query", q.query()).getResultList();
    }
    public List<PortfolioSummaryProject> summaryProjects(ArchitecturePortfolioSummaryQuery q) {
        // Same literal substring search (name OR summary) and exact case-insensitive filters as the registry.
        return em.createQuery("select new " + PortfolioSummaryProject.class.getName()
            + "(p.id,p.name,p.owner,p.quarter,p.status,p.health,p.deadline) from ProjectEntity p where "
            + SUMMARY_FILTERS, PortfolioSummaryProject.class)
            .setParameter("quarter", q.quarter()).setParameter("status", q.status())
            .setParameter("query", q.query()).getResultList();
    }
    /** Six narrow scalar result sets; no detail text unused by the readiness formula is selected. */
    public SummaryBatch loadSummary(ArchitecturePortfolioSummaryQuery q) {
        return new SummaryBatch(
            summaryRows(SummaryPassport.class, "p.projectId,p.criticality,p.stage,p.solutionType,p.architect,p.solutionSummary,"
                + "p.availabilityRequirement,p.recoveryRequirement,p.lastReviewDate", "ArchitecturePassportEntity p", "p.projectId", q),
            summaryRows(Membership.class,"d.id.projectId,d.id.classCode","ArchitectureDataClassEntity d","d.id.projectId",q),
            summaryRows(Membership.class,"e.id.projectId,e.id.environmentCode","ArchitectureEnvironmentEntity e","e.id.projectId",q),
            summaryRows(SummaryIntegration.class,"i.passport.projectId,i.id,i.criticality,i.readiness",
                "ArchitectureIntegrationEntity i","i.passport.projectId",q),
            summaryRows(SummaryCheck.class,"c.passport.projectId,c.id,c.checkType,c.required,c.status,"
                + "c.plannedDate,c.actualDate,c.comment","ArchitectureCheckEntity c","c.passport.projectId",q),
            summaryRows(SummaryRisk.class,"r.passport.projectId,r.id,r.probability,r.impact,r.status,r.acceptanceJustification",
                "SecurityRiskEntity r","r.passport.projectId",q));
    }
    /** One scalar GROUP BY, never per-project COUNTs or component text loading. */
    public List<ComponentCount> summaryComponentCounts(ArchitecturePortfolioSummaryQuery q) {
        return em.createQuery("select new " + ComponentCount.class.getName()
            + "(c.passport.projectId,count(c)) from ArchitectureComponentEntity c "
            + "where " + summaryOwners("c.passport.projectId") + " group by c.passport.projectId", ComponentCount.class)
            .setParameter("quarter",q.quarter()).setParameter("status",q.status()).setParameter("query",q.query()).getResultList();
    }
    private <T> List<T> summaryRows(Class<T> type, String fields, String from, String owner, ArchitecturePortfolioSummaryQuery q) {
        return em.createQuery("select new " + type.getName() + "(" + fields + ") from " + from + " where " + summaryOwners(owner),type)
            .setParameter("quarter",q.quarter()).setParameter("status",q.status()).setParameter("query",q.query()).getResultList();
    }
    private static String summaryOwners(String owner) {
        // Inner alias deliberately differs from outer passport alias p; only trusted query fragments enter here.
        return owner + " in (select p_summary.id from ProjectEntity p_summary where "
            + SUMMARY_FILTERS.replace("p.","p_summary.") + ")";
    }
    private static String projectSelect() {
        return "select new " + ProjectInfo.class.getName() + "(p.id,p.name,p.owner,p.quarter,p.status,p.version) from ProjectEntity p";
    }

    public Batch load(List<String> projectIds) {
        return load(projectIds, true);
    }
    public Batch load(List<String> projectIds, boolean details) {
        List<Passport> passports = new ArrayList<>(); List<Membership> data = new ArrayList<>();
        List<Membership> environments = new ArrayList<>(); List<Component> components = new ArrayList<>();
        List<IntegrationData> integrations = new ArrayList<>(); List<Membership> integrationData = new ArrayList<>();
        List<Check> checks = new ArrayList<>(); List<RiskData> risks = new ArrayList<>();
        for (int start = 0; start < projectIds.size(); start += BATCH_SIZE) {
            List<String> ids = projectIds.subList(start, Math.min(start + BATCH_SIZE, projectIds.size()));
            passports.addAll(query(Passport.class, "p.projectId,p.instanceId,p.criticality,p.stage,p.solutionType,p.architect,p.solutionSummary,"
                + "p.availabilityRequirement,p.recoveryRequirement,p.lastReviewDate,p.version,p.createdAt,p.updatedAt",
                "ArchitecturePassportEntity p", "p.projectId", ids));
            data.addAll(query(Membership.class, "d.id.projectId,d.id.classCode", "ArchitectureDataClassEntity d", "d.id.projectId", ids));
            environments.addAll(query(Membership.class, "e.id.projectId,e.id.environmentCode", "ArchitectureEnvironmentEntity e", "e.id.projectId", ids));
            if (details) components.addAll(query(Component.class, "c.id,c.passport.projectId,c.name,c.componentType,c.purpose,c.platform,"
                + "c.criticality,c.externalComponent,c.position,c.version", "ArchitectureComponentEntity c", "c.passport.projectId", ids));
            integrations.addAll(query(IntegrationData.class, "i.id,i.passport.projectId,i.name,i.sourceSystem,i.targetSystem,"
                + "i.direction,i.interactionType,i.authCategory,i.criticality,i.readiness,i.responsibleParty,i.comment,i.version",
                "ArchitectureIntegrationEntity i", "i.passport.projectId", ids));
            if (details) integrationData.addAll(query(Membership.class, "d.id.integrationId,d.id.classCode", "IntegrationDataClassEntity d",
                "d.integration.passport.projectId", ids));
            checks.addAll(query(Check.class, "c.id,c.passport.projectId,c.checkType,c.required,c.status,c.owner,"
                + "c.plannedDate,c.actualDate,c.comment,c.version", "ArchitectureCheckEntity c", "c.passport.projectId", ids));
            risks.addAll(query(RiskData.class, "r.id,r.passport.projectId,r.name,r.description,r.category,r.probability,r.impact,"
                + "r.owner,r.treatmentDueDate,r.treatmentPlan,r.status,r.closedDate,r.acceptanceJustification,r.version",
                "SecurityRiskEntity r", "r.passport.projectId", ids));
        }
        return new Batch(passports, data, environments, components, integrations, integrationData, checks, risks);
    }
    private <T> List<T> query(Class<T> type, String fields, String from, String owner, List<String> ids) {
        return em.createQuery("select new " + type.getName() + "(" + fields + ") from " + from + " where " + owner + " in :ids", type)
            .setParameter("ids", ids).getResultList();
    }
    public record Batch(List<Passport> passports, List<Membership> dataClasses, List<Membership> environments,
        List<Component> components, List<IntegrationData> integrations, List<Membership> integrationDataClasses,
        List<Check> checks, List<RiskData> risks) { }
    public record SummaryBatch(List<SummaryPassport> passports, List<Membership> dataClasses,
        List<Membership> environments, List<SummaryIntegration> integrations,
        List<SummaryCheck> checks, List<SummaryRisk> risks) { }
}
