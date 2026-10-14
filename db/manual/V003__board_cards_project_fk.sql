-- PM-003B1a: manual H2 migration for board_cards.project_id.
-- Apply only with writes stopped, after a verified backup and a rehearsal on a restored copy.
-- This script is not a Spring resource or a startup migration. H2 DDL is not a safe rollback boundary.

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='PUBLIC' AND table_name='PROJECTS') = 18
    AND NOT EXISTS (
        SELECT 1 FROM (VALUES
            ('ID','CHARACTER VARYING','NO',255),
            ('NAME','CHARACTER VARYING','NO',255),
            ('OWNER','CHARACTER VARYING','NO',255),
            ('STATUS','CHARACTER VARYING','NO',255),
            ('HEALTH','CHARACTER VARYING','NO',255),
            ('DELIVERY_MODEL','CHARACTER VARYING','YES',255),
            ('BUDGET','INTEGER','YES',NULL),
            ('PROGRESS','INTEGER','YES',NULL),
            ('QUARTER','CHARACTER VARYING','NO',255),
            ('DEADLINE','CHARACTER VARYING','NO',255),
            ('MILESTONE','CHARACTER VARYING','NO',1000),
            ('RISK','CHARACTER VARYING','NO',10000),
            ('DEPENDENCY','CHARACTER VARYING','NO',10000),
            ('KPI_NAME','CHARACTER VARYING','NO',1000),
            ('KPI_TARGET','CHARACTER VARYING','NO',1000),
            ('SUMMARY','CHARACTER VARYING','NO',10000),
            ('START_DATE','DATE','YES',NULL),
            ('VERSION','BIGINT','NO',NULL)
        ) expected(column_name,data_type,is_nullable,character_maximum_length)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema='PUBLIC' AND actual.table_name='PROJECTS'
         AND actual.column_name=expected.column_name
        WHERE actual.column_name IS NULL OR actual.data_type<>expected.data_type
           OR actual.is_nullable<>expected.is_nullable
           OR COALESCE(actual.character_maximum_length,-1)
              <> COALESCE(expected.character_maximum_length,-1)
    )
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 12
    AND NOT EXISTS (
        SELECT 1 FROM (VALUES
            ('ID','CHARACTER VARYING','NO',255),
            ('PROJECT_ID','CHARACTER VARYING','NO',255),
            ('COLUMN_KEY','CHARACTER VARYING','NO',255),
            ('POSITION','INTEGER','NO',NULL),
            ('TITLE','CHARACTER VARYING','NO',255),
            ('DESCRIPTION','CHARACTER VARYING','NO',10000),
            ('OWNER','CHARACTER VARYING','NO',255),
            ('DUE_DATE','CHARACTER VARYING','NO',255),
            ('PRIORITY','CHARACTER VARYING','YES',255),
            ('LABELS','CHARACTER VARYING','YES',255),
            ('ESTIMATE','INTEGER','YES',NULL),
            ('BLOCKED','BOOLEAN','YES',NULL)
        ) expected(column_name,data_type,is_nullable,character_maximum_length)
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema='PUBLIC' AND actual.table_name='BOARD_CARDS'
         AND actual.column_name=expected.column_name
        WHERE actual.column_name IS NULL OR actual.data_type<>expected.data_type
           OR (expected.column_name<>'PROJECT_ID' AND actual.is_nullable<>expected.is_nullable)
           OR COALESCE(actual.character_maximum_length,-1)
              <> COALESCE(expected.character_maximum_length,-1)
    )
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
           AND column_name='PROJECT_ID' AND is_nullable IN ('YES','NO')) = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c
         JOIN information_schema.key_column_usage k
           ON k.constraint_catalog=c.constraint_catalog
          AND k.constraint_schema=c.constraint_schema
          AND k.constraint_name=c.constraint_name
         WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECTS'
           AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c
         JOIN information_schema.key_column_usage k
           ON k.constraint_catalog=c.constraint_catalog
          AND k.constraint_schema=c.constraint_schema
          AND k.constraint_name=c.constraint_name
         WHERE c.table_schema='PUBLIC' AND c.table_name='PROJECTS'
           AND c.constraint_type='PRIMARY KEY') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c
         JOIN information_schema.key_column_usage k
           ON k.constraint_catalog=c.constraint_catalog
          AND k.constraint_schema=c.constraint_schema
          AND k.constraint_name=c.constraint_name
         WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS'
           AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints c
         JOIN information_schema.key_column_usage k
           ON k.constraint_catalog=c.constraint_catalog
          AND k.constraint_schema=c.constraint_schema
          AND k.constraint_name=c.constraint_name
         WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS'
           AND c.constraint_type='PRIMARY KEY') = 1
    AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
                      AND constraint_type='FOREIGN KEY')
    AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_schema='PUBLIC'
                      AND constraint_name='FK_BOARD_CARDS_PROJECT')
    AND NOT EXISTS (SELECT 1 FROM information_schema.indexes
                    WHERE index_schema='PUBLIC' AND index_name='IDX_BOARD_CARDS_PROJECT')
    AND NOT EXISTS (SELECT 1 FROM information_schema.index_columns
                    WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
                      AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND NOT EXISTS (SELECT 1 FROM board_cards WHERE project_id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM board_cards b LEFT JOIN projects p ON p.id=b.project_id
                    WHERE p.id IS NULL)
    THEN 1 ELSE 0
END AS PM003B1A_PRECONDITION_FAILED_EXPECTED_PM002C_SCHEMA_OR_VALID_CARDS;

-- The index is the first DDL and doubles as the partial-application marker. A stopped
-- run after this point is rejected by the precondition; restore a verified backup.
CREATE INDEX idx_board_cards_project ON board_cards (project_id);

-- EXECUTE IMMEDIATE accepts a non-query statement. SET changes only this session; it is
-- selected when the original column already has NOT NULL, so no redundant ALTER runs.
EXECUTE IMMEDIATE (SELECT CASE WHEN is_nullable='YES'
    THEN 'ALTER TABLE board_cards ALTER COLUMN project_id SET NOT NULL'
    ELSE 'SET @PM003B1A_NOOP 1' END
    FROM information_schema.columns
    WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS' AND column_name='PROJECT_ID');

ALTER TABLE board_cards ADD CONSTRAINT fk_board_cards_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE NO ACTION;

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
       AND column_name='PROJECT_ID' AND is_nullable='NO') = 1
    AND (SELECT COUNT(*) FROM information_schema.index_columns
         WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
           AND index_name='IDX_BOARD_CARDS_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.index_columns
                WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
                  AND index_name='IDX_BOARD_CARDS_PROJECT'
                  AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
           AND constraint_type='FOREIGN KEY') = 1
    AND EXISTS (
        SELECT 1 FROM information_schema.table_constraints fk
        JOIN information_schema.key_column_usage fk_col
          ON fk_col.constraint_catalog=fk.constraint_catalog
         AND fk_col.constraint_schema=fk.constraint_schema
         AND fk_col.constraint_name=fk.constraint_name
        JOIN information_schema.referential_constraints ref
          ON ref.constraint_catalog=fk.constraint_catalog
         AND ref.constraint_schema=fk.constraint_schema
         AND ref.constraint_name=fk.constraint_name
        JOIN information_schema.table_constraints pk
          ON pk.constraint_catalog=ref.unique_constraint_catalog
         AND pk.constraint_schema=ref.unique_constraint_schema
         AND pk.constraint_name=ref.unique_constraint_name
        WHERE fk.table_schema='PUBLIC' AND fk.table_name='BOARD_CARDS'
          AND fk.constraint_name='FK_BOARD_CARDS_PROJECT'
          AND fk_col.column_name='PROJECT_ID' AND fk_col.ordinal_position=1
          AND pk.table_schema='PUBLIC' AND pk.table_name='PROJECTS'
          AND pk.constraint_type='PRIMARY KEY'
          AND ref.delete_rule IN ('NO ACTION','RESTRICT')
    )
    THEN 1 ELSE 0
END AS PM003B1A_POSTCONDITION_FAILED;
