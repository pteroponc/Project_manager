-- PM-002B1 manual H2 migration.
-- Run only after a verified backup and only against the reviewed PM-002A schema.
-- This file is not loaded by Spring and must never be configured as an init script.

SELECT 1 / CASE WHEN
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS')
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS') = 16
    AND NOT EXISTS (
        SELECT 1
        FROM (VALUES
            ('ID', 'CHARACTER VARYING', 'NO', 255),
            ('NAME', 'CHARACTER VARYING', 'NO', 255),
            ('OWNER', 'CHARACTER VARYING', 'NO', 255),
            ('STATUS', 'CHARACTER VARYING', 'NO', 255),
            ('HEALTH', 'CHARACTER VARYING', 'NO', 255),
            ('DELIVERY_MODEL', 'CHARACTER VARYING', 'YES', 255),
            ('BUDGET', 'INTEGER', 'NO', NULL),
            ('PROGRESS', 'INTEGER', 'NO', NULL),
            ('QUARTER', 'CHARACTER VARYING', 'NO', 255),
            ('DEADLINE', 'CHARACTER VARYING', 'NO', 255),
            ('MILESTONE', 'CHARACTER VARYING', 'NO', 1000),
            ('RISK', 'CHARACTER VARYING', 'NO', 10000),
            ('DEPENDENCY', 'CHARACTER VARYING', 'NO', 10000),
            ('KPI_NAME', 'CHARACTER VARYING', 'NO', 1000),
            ('KPI_TARGET', 'CHARACTER VARYING', 'NO', 1000),
            ('SUMMARY', 'CHARACTER VARYING', 'NO', 10000)
        ) expected(column_name, data_type, is_nullable, character_maximum_length)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema = 'PUBLIC'
         AND actual.table_name = 'PROJECTS'
         AND actual.column_name = expected.column_name
        WHERE actual.column_name IS NULL
           OR actual.data_type <> expected.data_type
           OR actual.is_nullable <> expected.is_nullable
           OR COALESCE(actual.character_maximum_length, -1)
              <> COALESCE(expected.character_maximum_length, -1)
    )
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS'
           AND constraint_type = 'PRIMARY KEY') = 1
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage usage
         JOIN information_schema.table_constraints constraint_info
           ON constraint_info.constraint_catalog = usage.constraint_catalog
          AND constraint_info.constraint_schema = usage.constraint_schema
          AND constraint_info.constraint_name = usage.constraint_name
         WHERE constraint_info.table_schema = 'PUBLIC'
           AND constraint_info.table_name = 'PROJECTS'
           AND constraint_info.constraint_type = 'PRIMARY KEY'
           AND usage.column_name = 'ID' AND usage.ordinal_position = 1) = 1
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage usage
         JOIN information_schema.table_constraints constraint_info
           ON constraint_info.constraint_catalog = usage.constraint_catalog
          AND constraint_info.constraint_schema = usage.constraint_schema
          AND constraint_info.constraint_name = usage.constraint_name
         WHERE constraint_info.table_schema = 'PUBLIC'
           AND constraint_info.table_name = 'PROJECTS'
           AND constraint_info.constraint_type = 'PRIMARY KEY') = 1
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS'
                      AND column_name IN ('START_DATE', 'VERSION'))
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECT_MILESTONES')
    THEN 1 ELSE 0
END AS PM002B1_PRECONDITION_FAILED_EXPECTED_PM002A_SCHEMA;

ALTER TABLE projects ADD COLUMN start_date DATE;
ALTER TABLE projects ALTER COLUMN progress DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN budget DROP NOT NULL;
ALTER TABLE projects ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

CREATE TABLE project_milestones (
    id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    planned_date DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    completed_date DATE,
    position INTEGER NOT NULL,
    CONSTRAINT pk_project_milestones PRIMARY KEY (id),
    CONSTRAINT fk_project_milestones_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE NO ACTION
);

CREATE INDEX idx_project_milestones_project_order
    ON project_milestones (project_id, position, id);

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS') = 18
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS'
                  AND column_name = 'BUDGET' AND data_type = 'INTEGER' AND is_nullable = 'YES')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS'
                  AND column_name = 'PROGRESS' AND data_type = 'INTEGER' AND is_nullable = 'YES')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS'
                  AND column_name = 'START_DATE' AND data_type = 'DATE' AND is_nullable = 'YES')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECTS'
                  AND column_name = 'VERSION' AND data_type = 'BIGINT' AND is_nullable = 'NO'
                  AND column_default = '0')
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECT_MILESTONES') = 7
    AND NOT EXISTS (
        SELECT 1
        FROM (VALUES
            ('ID', 'CHARACTER VARYING', 'NO', 255),
            ('PROJECT_ID', 'CHARACTER VARYING', 'NO', 255),
            ('NAME', 'CHARACTER VARYING', 'NO', 255),
            ('PLANNED_DATE', 'DATE', 'NO', NULL),
            ('COMPLETED', 'BOOLEAN', 'NO', NULL),
            ('COMPLETED_DATE', 'DATE', 'YES', NULL),
            ('POSITION', 'INTEGER', 'NO', NULL)
        ) expected(column_name, data_type, is_nullable, character_maximum_length)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema = 'PUBLIC'
         AND actual.table_name = 'PROJECT_MILESTONES'
         AND actual.column_name = expected.column_name
        WHERE actual.column_name IS NULL
           OR actual.data_type <> expected.data_type
           OR actual.is_nullable <> expected.is_nullable
           OR COALESCE(actual.character_maximum_length, -1)
              <> COALESCE(expected.character_maximum_length, -1)
    )
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage usage
         JOIN information_schema.table_constraints constraint_info
           ON constraint_info.constraint_catalog = usage.constraint_catalog
          AND constraint_info.constraint_schema = usage.constraint_schema
          AND constraint_info.constraint_name = usage.constraint_name
         WHERE constraint_info.table_schema = 'PUBLIC'
           AND constraint_info.table_name = 'PROJECT_MILESTONES'
           AND constraint_info.constraint_type = 'PRIMARY KEY'
           AND usage.column_name = 'ID' AND usage.ordinal_position = 1) = 1
    AND EXISTS (
        SELECT 1
        FROM information_schema.referential_constraints reference_info
        JOIN information_schema.table_constraints foreign_key
          ON foreign_key.constraint_catalog = reference_info.constraint_catalog
         AND foreign_key.constraint_schema = reference_info.constraint_schema
         AND foreign_key.constraint_name = reference_info.constraint_name
        JOIN information_schema.table_constraints referenced_key
          ON referenced_key.constraint_catalog = reference_info.unique_constraint_catalog
         AND referenced_key.constraint_schema = reference_info.unique_constraint_schema
         AND referenced_key.constraint_name = reference_info.unique_constraint_name
        WHERE foreign_key.table_schema = 'PUBLIC'
          AND foreign_key.table_name = 'PROJECT_MILESTONES'
          AND foreign_key.constraint_name = 'FK_PROJECT_MILESTONES_PROJECT'
          AND referenced_key.table_schema = 'PUBLIC'
          AND referenced_key.table_name = 'PROJECTS'
          AND reference_info.delete_rule IN ('NO ACTION', 'RESTRICT')
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.key_column_usage
        WHERE constraint_schema = 'PUBLIC'
          AND constraint_name = 'FK_PROJECT_MILESTONES_PROJECT'
          AND table_name = 'PROJECT_MILESTONES'
          AND column_name = 'PROJECT_ID' AND ordinal_position = 1
    )
    AND (SELECT COUNT(*) FROM information_schema.index_columns
         WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECT_MILESTONES'
           AND index_name = 'IDX_PROJECT_MILESTONES_PROJECT_ORDER') = 3
    AND EXISTS (SELECT 1 FROM information_schema.index_columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECT_MILESTONES'
                  AND index_name = 'IDX_PROJECT_MILESTONES_PROJECT_ORDER'
                  AND column_name = 'PROJECT_ID' AND ordinal_position = 1)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECT_MILESTONES'
                  AND index_name = 'IDX_PROJECT_MILESTONES_PROJECT_ORDER'
                  AND column_name = 'POSITION' AND ordinal_position = 2)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns
                WHERE table_schema = 'PUBLIC' AND table_name = 'PROJECT_MILESTONES'
                  AND index_name = 'IDX_PROJECT_MILESTONES_PROJECT_ORDER'
                  AND column_name = 'ID' AND ordinal_position = 3)
    THEN 1 ELSE 0
END AS PM002B1_POSTCONDITION_FAILED;
