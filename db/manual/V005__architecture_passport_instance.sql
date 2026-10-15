-- PM-003B3.3b manual V005 for H2 2.3.232. Never run as startup SQL.
-- Full V004 schema precondition precedes the first DDL. Existing row versions/dates are preserved.

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='PUBLIC' AND table_name IN ('PROJECTS', 'BOARD_CARDS', 'RELEASE_TRAINS', 'PROJECT_MILESTONES')) = 53
    AND NOT EXISTS (
        SELECT 1 FROM (VALUES
            ('PROJECTS', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'OWNER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'STATUS', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'HEALTH', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'DELIVERY_MODEL', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECTS', 'BUDGET', 'INTEGER', 'YES', NULL, NULL),
            ('PROJECTS', 'PROGRESS', 'INTEGER', 'YES', NULL, NULL),
            ('PROJECTS', 'QUARTER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'DEADLINE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'MILESTONE', 'CHARACTER VARYING', 'NO', 1000, NULL),
            ('PROJECTS', 'RISK', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECTS', 'DEPENDENCY', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECTS', 'KPI_NAME', 'CHARACTER VARYING', 'NO', 1000, NULL),
            ('PROJECTS', 'KPI_TARGET', 'CHARACTER VARYING', 'NO', 1000, NULL),
            ('PROJECTS', 'SUMMARY', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECTS', 'START_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECTS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('BOARD_CARDS', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'COLUMN_KEY', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'POSITION', 'INTEGER', 'NO', NULL, NULL),
            ('BOARD_CARDS', 'TITLE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'DESCRIPTION', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('BOARD_CARDS', 'OWNER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'DUE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'PRIORITY', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('BOARD_CARDS', 'LABELS', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('BOARD_CARDS', 'ESTIMATE', 'INTEGER', 'YES', NULL, NULL),
            ('BOARD_CARDS', 'BLOCKED', 'BOOLEAN', 'YES', NULL, NULL),
            ('RELEASE_TRAINS', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'STATUS', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'CADENCE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'QUARTER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'PLANNED_RELEASE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'CODE_FREEZE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'QA_FREEZE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'GO_LIVE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'CAPACITY_POINTS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'COMMITTED_POINTS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'READINESS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'BLOCKED_ITEMS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'SCOPE', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('RELEASE_TRAINS', 'RISK', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('RELEASE_TRAINS', 'DECISION', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECT_MILESTONES', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_MILESTONES', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_MILESTONES', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_MILESTONES', 'PLANNED_DATE', 'DATE', 'NO', NULL, NULL),
            ('PROJECT_MILESTONES', 'COMPLETED', 'BOOLEAN', 'NO', NULL, NULL),
            ('PROJECT_MILESTONES', 'COMPLETED_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_MILESTONES', 'POSITION', 'INTEGER', 'NO', NULL, NULL)
        ) expected(table_name,column_name,data_type,is_nullable,max_length,column_default)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema='PUBLIC' AND actual.table_name=expected.table_name
         AND actual.column_name=expected.column_name
        WHERE actual.column_name IS NULL OR actual.data_type<>expected.data_type
           OR actual.is_nullable<>expected.is_nullable
           OR COALESCE(actual.character_maximum_length,-1)<>COALESCE(expected.max_length,-1)
           OR COALESCE(actual.column_default,'<NULL>')<>COALESCE(expected.column_default,'<NULL>')
           OR actual.is_identity<>'NO' OR actual.is_generated<>'NEVER'
    )
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECTS' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECTS' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECTS') = 1
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='PROJECTS') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 2
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_BOARD_CARDS_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='BOARD_CARDS' AND f.constraint_name='FK_BOARD_CARDS_PROJECT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS' AND index_name='IDX_BOARD_CARDS_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS' AND index_name='IDX_BOARD_CARDS_PROJECT' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS' AND index_name='IDX_BOARD_CARDS_PROJECT' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 2
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='RELEASE_TRAINS' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='RELEASE_TRAINS' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='RELEASE_TRAINS') = 1
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='RELEASE_TRAINS') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_MILESTONES' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_MILESTONES' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_MILESTONES') = 2
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_PROJECT_MILESTONES_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_MILESTONES' AND f.constraint_name='FK_PROJECT_MILESTONES_PROJECT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND column_name='POSITION' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='PROJECT_MILESTONES') = 3
    AND NOT EXISTS (SELECT 1 FROM board_cards b LEFT JOIN projects p ON p.id=b.project_id WHERE p.id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM project_milestones m LEFT JOIN projects p ON p.id=m.project_id WHERE p.id IS NULL)
    AND EXISTS (SELECT 1 FROM information_schema.indexes i JOIN information_schema.index_columns c ON c.index_schema=i.index_schema AND c.index_name=i.index_name WHERE i.table_schema='PUBLIC' AND i.table_name='PROJECT_MILESTONES' AND i.index_type_name='INDEX' AND i.is_generated=TRUE AND c.column_name='PROJECT_ID' AND c.ordinal_position=1 AND (SELECT COUNT(*) FROM information_schema.index_columns x WHERE x.index_schema=i.index_schema AND x.index_name=i.index_name)=1)
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='PUBLIC' AND table_name IN ('PROJECT_ARCHITECTURE_PASSPORTS', 'PROJECT_ARCHITECTURE_DATA_CLASSES', 'PROJECT_ARCHITECTURE_ENVIRONMENTS', 'PROJECT_ARCHITECTURE_COMPONENTS', 'PROJECT_ARCHITECTURE_INTEGRATIONS', 'PROJECT_INTEGRATION_DATA_CLASSES', 'PROJECT_ARCHITECTURE_CHECKS', 'PROJECT_SECURITY_RISKS', 'PM_ARCHITECTURE_SCHEMA_MIGRATIONS')) = 67
    AND NOT EXISTS (
        SELECT 1 FROM (VALUES
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'CRITICALITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'STAGE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'SOLUTION_TYPE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'ARCHITECT', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'SOLUTION_SUMMARY', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'AVAILABILITY_REQUIREMENT', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'RECOVERY_REQUIREMENT', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'LAST_REVIEW_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'CREATED_AT', 'TIMESTAMP WITH TIME ZONE', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'UPDATED_AT', 'TIMESTAMP WITH TIME ZONE', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_DATA_CLASSES', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_DATA_CLASSES', 'CLASS_CODE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_ENVIRONMENTS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_ENVIRONMENTS', 'ENVIRONMENT_CODE', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'COMPONENT_TYPE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'PURPOSE', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'PLATFORM', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'CRITICALITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'EXTERNAL_COMPONENT', 'BOOLEAN', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'POSITION', 'INTEGER', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'SOURCE_SYSTEM', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'TARGET_SYSTEM', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'DIRECTION', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'INTERACTION_TYPE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'AUTH_CATEGORY', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'CRITICALITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'READINESS', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'RESPONSIBLE_PARTY', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'COMMENT', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_INTEGRATION_DATA_CLASSES', 'INTEGRATION_ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_INTEGRATION_DATA_CLASSES', 'CLASS_CODE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'CHECK_TYPE', 'CHARACTER VARYING', 'NO', 48, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'REQUIRED', 'BOOLEAN', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'STATUS', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'OWNER', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'PLANNED_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'ACTUAL_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'COMMENT', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_SECURITY_RISKS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_SECURITY_RISKS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_SECURITY_RISKS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_SECURITY_RISKS', 'DESCRIPTION', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_SECURITY_RISKS', 'CATEGORY', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_SECURITY_RISKS', 'PROBABILITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_SECURITY_RISKS', 'IMPACT', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_SECURITY_RISKS', 'OWNER', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_SECURITY_RISKS', 'TREATMENT_DUE_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_SECURITY_RISKS', 'TREATMENT_PLAN', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_SECURITY_RISKS', 'STATUS', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_SECURITY_RISKS', 'CLOSED_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_SECURITY_RISKS', 'ACCEPTANCE_JUSTIFICATION', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_SECURITY_RISKS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PM_ARCHITECTURE_SCHEMA_MIGRATIONS', 'MIGRATION_VERSION', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PM_ARCHITECTURE_SCHEMA_MIGRATIONS', 'APPLIED_AT', 'TIMESTAMP WITH TIME ZONE', 'NO', NULL, NULL)
        ) expected(table_name,column_name,data_type,is_nullable,max_length,column_default)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema='PUBLIC' AND actual.table_name=expected.table_name
         AND actual.column_name=expected.column_name
        WHERE actual.column_name IS NULL OR actual.data_type<>expected.data_type
           OR actual.is_nullable<>expected.is_nullable
           OR COALESCE(actual.character_maximum_length,-1)<>COALESCE(expected.max_length,-1)
           OR COALESCE(actual.column_default,'<NULL>')<>COALESCE(expected.column_default,'<NULL>')
           OR actual.is_identity<>'NO' OR actual.is_generated<>'NEVER'
    )
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_PASSPORTS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_PASSPORTS' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_PASSPORT_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND f.constraint_name='FK_ARCH_PASSPORT_PROJECT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND column_name='CRITICALITY' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND column_name='STAGE' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND column_name='PROJECT_ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS') = 6
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_CRITICALITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CRITICALITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_STAGE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'STAGEIN''NOT_STARTED'',''IN_DESIGN'',''IN_REVIEW'',''APPROVED'',''CHANGES_REQUIRED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_SOLUTION_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'SOLUTION_TYPEIN''NEW_SOLUTION'',''EXISTING_SOLUTION_CHANGE'',''INTEGRATION'',''MIGRATION''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_DATA_CLASSES') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_DATA_CLASSES' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_DATA_CLASSES' AND k.column_name='CLASS_CODE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_DATA_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND f.constraint_name='FK_ARCH_DATA_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_DATA_CLASSES') = 3
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_DATA_CLASSES_CLASS_CODE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CLASS_CODEIN''PUBLIC'',''INTERNAL'',''CONFIDENTIAL'',''PERSONAL'',''PAYMENT'',''COMMERCIAL_SECRET''')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_ENVIRONMENTS') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_ENVIRONMENTS' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_ENVIRONMENTS' AND k.column_name='ENVIRONMENT_CODE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_ENV_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND f.constraint_name='FK_ARCH_ENV_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS') = 3
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_ENVIRONMENTS_ENVIRONMENT_CODE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'ENVIRONMENT_CODEIN''DEV'',''TEST'',''PREPROD'',''PROD'',''OTHER''')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_COMPONENTS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_COMPONENTS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_COMPONENT_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND f.constraint_name='FK_ARCH_COMPONENT_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND column_name='POSITION' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS') = 6
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_COMPONENT_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'COMPONENT_TYPEIN''FRONTEND'',''BACKEND'',''DATABASE'',''QUEUE'',''EXTERNAL_SERVICE'',''INFRASTRUCTURE''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_CRITICALITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CRITICALITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_POSITION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'POSITION>=0')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_INTEGRATIONS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_INTEGRATIONS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_INTEGRATION_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND f.constraint_name='FK_ARCH_INTEGRATION_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT') = 2
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT' AND column_name='ID' AND ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS') = 8
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_DIRECTION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'DIRECTIONIN''INBOUND'',''OUTBOUND'',''BIDIRECTIONAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_INTERACTION_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'INTERACTION_TYPEIN''API'',''EVENT'',''FILE_EXCHANGE'',''BATCH'',''OTHER''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_AUTH_CATEGORY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'AUTH_CATEGORYIN''NONE'',''SERVICE_ACCOUNT'',''OAUTH2'',''MTLS'',''CERTIFICATE'',''OTHER''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_CRITICALITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CRITICALITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_READINESS' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'READINESSIN''PLANNED'',''IN_PROGRESS'',''READY'',''BLOCKED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_INTEGRATION_DATA_CLASSES') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_INTEGRATION_DATA_CLASSES' AND k.column_name='INTEGRATION_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_INTEGRATION_DATA_CLASSES' AND k.column_name='CLASS_CODE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_INTEGRATION_DATA_INTEGRATION') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND f.constraint_name='FK_INTEGRATION_DATA_INTEGRATION'
          AND fc.column_name='INTEGRATION_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_INTEGRATION_DATA_CLASSES') = 3
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_INTEGRATION_DATA_CLASSES_CLASS_CODE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CLASS_CODEIN''PUBLIC'',''INTERNAL'',''CONFIDENTIAL'',''PERSONAL'',''PAYMENT'',''COMMERCIAL_SECRET''')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_CHECKS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_CHECKS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_CHECK_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_CHECKS' AND f.constraint_name='FK_ARCH_CHECK_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND column_name='PLANNED_DATE' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS') = 2
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS' AND column_name='STATUS' AND ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='UNIQUE' AND c.constraint_name='UQ_ARCH_CHECK_TYPE') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='UNIQUE' AND c.constraint_name='UQ_ARCH_CHECK_TYPE' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='UNIQUE' AND c.constraint_name='UQ_ARCH_CHECK_TYPE' AND k.column_name='CHECK_TYPE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS') = 6
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_CHECKS_CHECK_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CHECK_TYPEIN''ARCHITECTURE_APPROVAL'',''SECURITY_REQUIREMENTS'',''DATA_CLASSIFICATION'',''THREAT_MODEL'',''SECURITY_ASSESSMENT'',''ACCESS_CONTROL'',''LOGGING_MONITORING'',''BACKUP_RECOVERY'',''LOAD_TESTING'',''PRODUCTION_ADMISSION''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_CHECKS_STATUS' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'STATUSIN''NOT_REQUIRED'',''NOT_STARTED'',''IN_PROGRESS'',''IN_REVIEW'',''PASSED'',''CHANGES_REQUIRED'',''BLOCKED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_CHECKS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_SECURITY_RISKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_SECURITY_RISKS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_SECURITY_RISKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_SECURITY_RISKS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_SECURITY_RISK_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_SECURITY_RISKS' AND f.constraint_name='FK_SECURITY_RISK_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND column_name='STATUS' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND column_name='TREATMENT_DUE_DATE' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS') = 7
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_CATEGORY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CATEGORYIN''ACCESS'',''DATA'',''AVAILABILITY'',''INTEGRATION'',''PROCESS'',''OTHER''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_PROBABILITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'PROBABILITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_IMPACT' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'IMPACTIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_STATUS' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'STATUSIN''OPEN'',''IN_PROGRESS'',''ACCEPTED'',''MITIGATED'',''CLOSED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PM_ARCHITECTURE_SCHEMA_MIGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_PM_ARCHITECTURE_SCHEMA_MIGRATIONS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PM_ARCHITECTURE_SCHEMA_MIGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_PM_ARCHITECTURE_SCHEMA_MIGRATIONS' AND k.column_name='MIGRATION_VERSION' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM pm_architecture_schema_migrations) = 1
    AND EXISTS (SELECT 1 FROM pm_architecture_schema_migrations WHERE migration_version='V004' AND applied_at IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='UQ_ARCH_PASSPORT_INSTANCE')
    THEN 1 ELSE 0
END AS PM003B3B_V005_PRECONDITION_FAILED_EXPECTED_V004_SCHEMA;

-- ALL writers must remain stopped throughout this manual script. Stop at first error.
-- H2 DDL commits: a partial failure requires verified backup restoration, never a blind rerun.
ALTER TABLE project_architecture_passports ADD COLUMN instance_id VARCHAR(36);
UPDATE project_architecture_passports SET instance_id=CAST(RANDOM_UUID() AS VARCHAR(36));
ALTER TABLE project_architecture_passports ALTER COLUMN instance_id SET NOT NULL;
ALTER TABLE project_architecture_passports ADD CONSTRAINT uq_arch_passport_instance UNIQUE(instance_id);
INSERT INTO pm_architecture_schema_migrations(migration_version,applied_at) VALUES ('V005',CURRENT_TIMESTAMP);

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='PUBLIC' AND table_name IN ('PROJECTS', 'BOARD_CARDS', 'RELEASE_TRAINS', 'PROJECT_MILESTONES')) = 53
    AND NOT EXISTS (
        SELECT 1 FROM (VALUES
            ('PROJECTS', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'OWNER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'STATUS', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'HEALTH', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'DELIVERY_MODEL', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECTS', 'BUDGET', 'INTEGER', 'YES', NULL, NULL),
            ('PROJECTS', 'PROGRESS', 'INTEGER', 'YES', NULL, NULL),
            ('PROJECTS', 'QUARTER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'DEADLINE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECTS', 'MILESTONE', 'CHARACTER VARYING', 'NO', 1000, NULL),
            ('PROJECTS', 'RISK', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECTS', 'DEPENDENCY', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECTS', 'KPI_NAME', 'CHARACTER VARYING', 'NO', 1000, NULL),
            ('PROJECTS', 'KPI_TARGET', 'CHARACTER VARYING', 'NO', 1000, NULL),
            ('PROJECTS', 'SUMMARY', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECTS', 'START_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECTS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('BOARD_CARDS', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'COLUMN_KEY', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'POSITION', 'INTEGER', 'NO', NULL, NULL),
            ('BOARD_CARDS', 'TITLE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'DESCRIPTION', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('BOARD_CARDS', 'OWNER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'DUE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('BOARD_CARDS', 'PRIORITY', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('BOARD_CARDS', 'LABELS', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('BOARD_CARDS', 'ESTIMATE', 'INTEGER', 'YES', NULL, NULL),
            ('BOARD_CARDS', 'BLOCKED', 'BOOLEAN', 'YES', NULL, NULL),
            ('RELEASE_TRAINS', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'STATUS', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'CADENCE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'QUARTER', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'PLANNED_RELEASE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'CODE_FREEZE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'QA_FREEZE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'GO_LIVE_DATE', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('RELEASE_TRAINS', 'CAPACITY_POINTS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'COMMITTED_POINTS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'READINESS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'BLOCKED_ITEMS', 'INTEGER', 'NO', NULL, NULL),
            ('RELEASE_TRAINS', 'SCOPE', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('RELEASE_TRAINS', 'RISK', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('RELEASE_TRAINS', 'DECISION', 'CHARACTER VARYING', 'NO', 10000, NULL),
            ('PROJECT_MILESTONES', 'ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_MILESTONES', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_MILESTONES', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_MILESTONES', 'PLANNED_DATE', 'DATE', 'NO', NULL, NULL),
            ('PROJECT_MILESTONES', 'COMPLETED', 'BOOLEAN', 'NO', NULL, NULL),
            ('PROJECT_MILESTONES', 'COMPLETED_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_MILESTONES', 'POSITION', 'INTEGER', 'NO', NULL, NULL)
        ) expected(table_name,column_name,data_type,is_nullable,max_length,column_default)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema='PUBLIC' AND actual.table_name=expected.table_name
         AND actual.column_name=expected.column_name
        WHERE actual.column_name IS NULL OR actual.data_type<>expected.data_type
           OR actual.is_nullable<>expected.is_nullable
           OR COALESCE(actual.character_maximum_length,-1)<>COALESCE(expected.max_length,-1)
           OR COALESCE(actual.column_default,'<NULL>')<>COALESCE(expected.column_default,'<NULL>')
           OR actual.is_identity<>'NO' OR actual.is_generated<>'NEVER'
    )
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECTS' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECTS' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECTS') = 1
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='PROJECTS') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 2
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_BOARD_CARDS_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='BOARD_CARDS' AND f.constraint_name='FK_BOARD_CARDS_PROJECT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS' AND index_name='IDX_BOARD_CARDS_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS' AND index_name='IDX_BOARD_CARDS_PROJECT' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS' AND index_name='IDX_BOARD_CARDS_PROJECT' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 2
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='RELEASE_TRAINS' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='RELEASE_TRAINS' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='RELEASE_TRAINS') = 1
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='RELEASE_TRAINS') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_MILESTONES' AND c.constraint_type='PRIMARY KEY') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_MILESTONES' AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_MILESTONES') = 2
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_PROJECT_MILESTONES_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_MILESTONES' AND f.constraint_name='FK_PROJECT_MILESTONES_PROJECT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND column_name='POSITION' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_MILESTONES' AND index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name='PROJECT_MILESTONES') = 3
    AND NOT EXISTS (SELECT 1 FROM board_cards b LEFT JOIN projects p ON p.id=b.project_id WHERE p.id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM project_milestones m LEFT JOIN projects p ON p.id=m.project_id WHERE p.id IS NULL)
    AND EXISTS (SELECT 1 FROM information_schema.indexes i JOIN information_schema.index_columns c ON c.index_schema=i.index_schema AND c.index_name=i.index_name WHERE i.table_schema='PUBLIC' AND i.table_name='PROJECT_MILESTONES' AND i.index_type_name='INDEX' AND i.is_generated=TRUE AND c.column_name='PROJECT_ID' AND c.ordinal_position=1 AND (SELECT COUNT(*) FROM information_schema.index_columns x WHERE x.index_schema=i.index_schema AND x.index_name=i.index_name)=1)
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='PUBLIC' AND table_name IN ('PROJECT_ARCHITECTURE_PASSPORTS', 'PROJECT_ARCHITECTURE_DATA_CLASSES', 'PROJECT_ARCHITECTURE_ENVIRONMENTS', 'PROJECT_ARCHITECTURE_COMPONENTS', 'PROJECT_ARCHITECTURE_INTEGRATIONS', 'PROJECT_INTEGRATION_DATA_CLASSES', 'PROJECT_ARCHITECTURE_CHECKS', 'PROJECT_SECURITY_RISKS', 'PM_ARCHITECTURE_SCHEMA_MIGRATIONS')) = 68
    AND NOT EXISTS (
        SELECT 1 FROM (VALUES
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'INSTANCE_ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'CRITICALITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'STAGE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'SOLUTION_TYPE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'ARCHITECT', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'SOLUTION_SUMMARY', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'AVAILABILITY_REQUIREMENT', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'RECOVERY_REQUIREMENT', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'LAST_REVIEW_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'CREATED_AT', 'TIMESTAMP WITH TIME ZONE', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_PASSPORTS', 'UPDATED_AT', 'TIMESTAMP WITH TIME ZONE', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_DATA_CLASSES', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_DATA_CLASSES', 'CLASS_CODE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_ENVIRONMENTS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_ENVIRONMENTS', 'ENVIRONMENT_CODE', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'COMPONENT_TYPE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'PURPOSE', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'PLATFORM', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'CRITICALITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'EXTERNAL_COMPONENT', 'BOOLEAN', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'POSITION', 'INTEGER', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_COMPONENTS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'SOURCE_SYSTEM', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'TARGET_SYSTEM', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'DIRECTION', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'INTERACTION_TYPE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'AUTH_CATEGORY', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'CRITICALITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'READINESS', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'RESPONSIBLE_PARTY', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'COMMENT', 'CHARACTER VARYING', 'YES', 1000, NULL),
            ('PROJECT_ARCHITECTURE_INTEGRATIONS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_INTEGRATION_DATA_CLASSES', 'INTEGRATION_ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_INTEGRATION_DATA_CLASSES', 'CLASS_CODE', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'CHECK_TYPE', 'CHARACTER VARYING', 'NO', 48, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'REQUIRED', 'BOOLEAN', 'NO', NULL, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'STATUS', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'OWNER', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'PLANNED_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'ACTUAL_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'COMMENT', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_ARCHITECTURE_CHECKS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PROJECT_SECURITY_RISKS', 'ID', 'CHARACTER VARYING', 'NO', 36, NULL),
            ('PROJECT_SECURITY_RISKS', 'PROJECT_ID', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_SECURITY_RISKS', 'NAME', 'CHARACTER VARYING', 'NO', 255, NULL),
            ('PROJECT_SECURITY_RISKS', 'DESCRIPTION', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_SECURITY_RISKS', 'CATEGORY', 'CHARACTER VARYING', 'NO', 32, NULL),
            ('PROJECT_SECURITY_RISKS', 'PROBABILITY', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_SECURITY_RISKS', 'IMPACT', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_SECURITY_RISKS', 'OWNER', 'CHARACTER VARYING', 'YES', 255, NULL),
            ('PROJECT_SECURITY_RISKS', 'TREATMENT_DUE_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_SECURITY_RISKS', 'TREATMENT_PLAN', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_SECURITY_RISKS', 'STATUS', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PROJECT_SECURITY_RISKS', 'CLOSED_DATE', 'DATE', 'YES', NULL, NULL),
            ('PROJECT_SECURITY_RISKS', 'ACCEPTANCE_JUSTIFICATION', 'CHARACTER VARYING', 'YES', 2000, NULL),
            ('PROJECT_SECURITY_RISKS', 'VERSION', 'BIGINT', 'NO', NULL, '0'),
            ('PM_ARCHITECTURE_SCHEMA_MIGRATIONS', 'MIGRATION_VERSION', 'CHARACTER VARYING', 'NO', 16, NULL),
            ('PM_ARCHITECTURE_SCHEMA_MIGRATIONS', 'APPLIED_AT', 'TIMESTAMP WITH TIME ZONE', 'NO', NULL, NULL)
        ) expected(table_name,column_name,data_type,is_nullable,max_length,column_default)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema='PUBLIC' AND actual.table_name=expected.table_name
         AND actual.column_name=expected.column_name
        WHERE actual.column_name IS NULL OR actual.data_type<>expected.data_type
           OR actual.is_nullable<>expected.is_nullable
           OR COALESCE(actual.character_maximum_length,-1)<>COALESCE(expected.max_length,-1)
           OR COALESCE(actual.column_default,'<NULL>')<>COALESCE(expected.column_default,'<NULL>')
           OR actual.is_identity<>'NO' OR actual.is_generated<>'NEVER'
    )
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_PASSPORTS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_PASSPORTS' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_PASSPORT_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND f.constraint_name='FK_ARCH_PASSPORT_PROJECT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND column_name='CRITICALITY' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND column_name='STAGE' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND index_name='IDX_ARCH_PASSPORT_PORTFOLIO' AND column_name='PROJECT_ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS') = 7
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_CRITICALITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CRITICALITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_STAGE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'STAGEIN''NOT_STARTED'',''IN_DESIGN'',''IN_REVIEW'',''APPROVED'',''CHANGES_REQUIRED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_SOLUTION_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'SOLUTION_TYPEIN''NEW_SOLUTION'',''EXISTING_SOLUTION_CHANGE'',''INTEGRATION'',''MIGRATION''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_PASSPORTS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_DATA_CLASSES') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_DATA_CLASSES' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_DATA_CLASSES' AND k.column_name='CLASS_CODE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_DATA_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_DATA_CLASSES' AND f.constraint_name='FK_ARCH_DATA_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_DATA_CLASSES') = 3
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_DATA_CLASSES_CLASS_CODE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CLASS_CODEIN''PUBLIC'',''INTERNAL'',''CONFIDENTIAL'',''PERSONAL'',''PAYMENT'',''COMMERCIAL_SECRET''')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_ENVIRONMENTS') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_ENVIRONMENTS' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_ENVIRONMENTS' AND k.column_name='ENVIRONMENT_CODE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_ENV_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS' AND f.constraint_name='FK_ARCH_ENV_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_ENVIRONMENTS') = 3
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_ENVIRONMENTS_ENVIRONMENT_CODE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'ENVIRONMENT_CODEIN''DEV'',''TEST'',''PREPROD'',''PROD'',''OTHER''')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_COMPONENTS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_COMPONENTS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_COMPONENT_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND f.constraint_name='FK_ARCH_COMPONENT_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND column_name='POSITION' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS' AND index_name='IDX_ARCH_COMPONENT_ORDER' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_COMPONENTS') = 6
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_COMPONENT_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'COMPONENT_TYPEIN''FRONTEND'',''BACKEND'',''DATABASE'',''QUEUE'',''EXTERNAL_SERVICE'',''INFRASTRUCTURE''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_CRITICALITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CRITICALITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_POSITION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'POSITION>=0')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_COMPONENTS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_INTEGRATIONS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_INTEGRATIONS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_INTEGRATION_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND f.constraint_name='FK_ARCH_INTEGRATION_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT') = 2
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND index_name='IDX_ARCH_INTEGRATION_PROJECT' AND column_name='ID' AND ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_INTEGRATIONS') = 8
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_DIRECTION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'DIRECTIONIN''INBOUND'',''OUTBOUND'',''BIDIRECTIONAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_INTERACTION_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'INTERACTION_TYPEIN''API'',''EVENT'',''FILE_EXCHANGE'',''BATCH'',''OTHER''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_AUTH_CATEGORY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'AUTH_CATEGORYIN''NONE'',''SERVICE_ACCOUNT'',''OAUTH2'',''MTLS'',''CERTIFICATE'',''OTHER''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_CRITICALITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CRITICALITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_READINESS' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'READINESSIN''PLANNED'',''IN_PROGRESS'',''READY'',''BLOCKED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_INTEGRATIONS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_INTEGRATION_DATA_CLASSES') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_INTEGRATION_DATA_CLASSES' AND k.column_name='INTEGRATION_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_INTEGRATION_DATA_CLASSES' AND k.column_name='CLASS_CODE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_INTEGRATION_DATA_INTEGRATION') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_INTEGRATION_DATA_CLASSES' AND f.constraint_name='FK_INTEGRATION_DATA_INTEGRATION'
          AND fc.column_name='INTEGRATION_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_INTEGRATIONS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_INTEGRATION_DATA_CLASSES') = 3
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_INTEGRATION_DATA_CLASSES_CLASS_CODE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CLASS_CODEIN''PUBLIC'',''INTERNAL'',''CONFIDENTIAL'',''PERSONAL'',''PAYMENT'',''COMMERCIAL_SECRET''')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_CHECKS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_ARCH_CHECKS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_ARCH_CHECK_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_ARCHITECTURE_CHECKS' AND f.constraint_name='FK_ARCH_CHECK_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND column_name='PLANNED_DATE' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_DATE' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS') = 2
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS' AND index_name='IDX_ARCH_CHECK_STATUS' AND column_name='STATUS' AND ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='UNIQUE' AND c.constraint_name='UQ_ARCH_CHECK_TYPE') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='UNIQUE' AND c.constraint_name='UQ_ARCH_CHECK_TYPE' AND k.column_name='PROJECT_ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_ARCHITECTURE_CHECKS' AND c.constraint_type='UNIQUE' AND c.constraint_name='UQ_ARCH_CHECK_TYPE' AND k.column_name='CHECK_TYPE' AND k.ordinal_position=2)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_CHECKS') = 6
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_CHECKS_CHECK_TYPE' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CHECK_TYPEIN''ARCHITECTURE_APPROVAL'',''SECURITY_REQUIREMENTS'',''DATA_CLASSIFICATION'',''THREAT_MODEL'',''SECURITY_ASSESSMENT'',''ACCESS_CONTROL'',''LOGGING_MONITORING'',''BACKUP_RECOVERY'',''LOAD_TESTING'',''PRODUCTION_ADMISSION''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_CHECKS_STATUS' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'STATUSIN''NOT_REQUIRED'',''NOT_STARTED'',''IN_PROGRESS'',''IN_REVIEW'',''PASSED'',''CHANGES_REQUIRED'',''BLOCKED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_ARCH_CHECKS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_SECURITY_RISKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_SECURITY_RISKS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECT_SECURITY_RISKS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_SECURITY_RISKS' AND k.column_name='ID' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='FK_SECURITY_RISK_PASSPORT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
        JOIN information_schema.table_constraints f ON f.constraint_catalog=r.constraint_catalog
         AND f.constraint_schema=r.constraint_schema AND f.constraint_name=r.constraint_name
        JOIN information_schema.key_column_usage fc ON fc.constraint_catalog=f.constraint_catalog
         AND fc.constraint_schema=f.constraint_schema AND fc.constraint_name=f.constraint_name
        JOIN information_schema.table_constraints p ON p.constraint_catalog=r.unique_constraint_catalog
         AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
        JOIN information_schema.key_column_usage pc ON pc.constraint_catalog=p.constraint_catalog
         AND pc.constraint_schema=p.constraint_schema AND pc.constraint_name=p.constraint_name
        WHERE f.table_schema='PUBLIC' AND f.table_name='PROJECT_SECURITY_RISKS' AND f.constraint_name='FK_SECURITY_RISK_PASSPORT'
          AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
          AND p.table_schema='PUBLIC' AND p.table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND p.constraint_type='PRIMARY KEY'
          AND pc.column_name='PROJECT_ID' AND pc.ordinal_position=1
          AND r.delete_rule IN ('NO ACTION','RESTRICT') AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND column_name='STATUS' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_STATUS' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE') = 3
    AND EXISTS (SELECT 1 FROM information_schema.indexes WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND column_name='TREATMENT_DUE_DATE' AND ordinal_position=2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns WHERE index_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS' AND index_name='IDX_SECURITY_RISK_DATE' AND column_name='ID' AND ordinal_position=3)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_SECURITY_RISKS') = 7
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_CATEGORY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'CATEGORYIN''ACCESS'',''DATA'',''AVAILABILITY'',''INTEGRATION'',''PROCESS'',''OTHER''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_PROBABILITY' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'PROBABILITYIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_IMPACT' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'IMPACTIN''LOW'',''MEDIUM'',''HIGH'',''CRITICAL''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_STATUS' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'STATUSIN''OPEN'',''IN_PROGRESS'',''ACCEPTED'',''MITIGATED'',''CLOSED''')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema='PUBLIC' AND constraint_name='CK_SECURITY_RISKS_VERSION' AND REGEXP_REPLACE(REPLACE(UPPER(check_clause), '"', ''), '[ ()]', '', 'g') = 'VERSION>=CAST0ASBIGINT')
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PM_ARCHITECTURE_SCHEMA_MIGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_PM_ARCHITECTURE_SCHEMA_MIGRATIONS') = 1
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c JOIN information_schema.key_column_usage k
          ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
         AND k.constraint_name=c.constraint_name
        WHERE c.table_schema='PUBLIC' AND c.table_name='PM_ARCHITECTURE_SCHEMA_MIGRATIONS' AND c.constraint_type='PRIMARY KEY' AND c.constraint_name='PK_PM_ARCHITECTURE_SCHEMA_MIGRATIONS' AND k.column_name='MIGRATION_VERSION' AND k.ordinal_position=1)
    AND (SELECT COUNT(*) FROM pm_architecture_schema_migrations) = 2
    AND EXISTS (SELECT 1 FROM pm_architecture_schema_migrations WHERE migration_version='V004' AND applied_at IS NOT NULL)
    AND EXISTS (SELECT 1 FROM pm_architecture_schema_migrations WHERE migration_version='V005' AND applied_at IS NOT NULL)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND constraint_type='UNIQUE' AND constraint_name='UQ_ARCH_PASSPORT_INSTANCE')
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='UQ_ARCH_PASSPORT_INSTANCE')=1
    AND EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name='UQ_ARCH_PASSPORT_INSTANCE' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS' AND column_name='INSTANCE_ID' AND ordinal_position=1)
    AND NOT EXISTS (SELECT 1 FROM project_architecture_passports WHERE instance_id IS NULL OR NOT REGEXP_LIKE(instance_id,'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'))
    AND (SELECT COUNT(*) FROM project_architecture_passports)=(SELECT COUNT(DISTINCT instance_id) FROM project_architecture_passports)
    THEN 1 ELSE 0
END AS PM003B3B_V005_POSTCONDITION_FAILED_INSTANCE_SCHEMA;
