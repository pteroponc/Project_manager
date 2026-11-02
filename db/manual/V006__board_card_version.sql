-- PM-006.3C.3: manual H2 migration. Stop all writers, verify a backup and rehearse restore.
-- Apply only to the reviewed V005 schema. H2 DDL is not a rollback boundary.
-- A repeated or partially applied run must fail before any further DDL.

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
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
           OR actual.is_nullable<>expected.is_nullable
           OR COALESCE(actual.character_maximum_length,-1)
              <> COALESCE(expected.character_maximum_length,-1)
    )
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 2
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints c
                JOIN information_schema.key_column_usage k
                  ON k.constraint_catalog=c.constraint_catalog AND k.constraint_schema=c.constraint_schema
                 AND k.constraint_name=c.constraint_name
                WHERE c.table_schema='PUBLIC' AND c.table_name='BOARD_CARDS'
                  AND c.constraint_type='PRIMARY KEY' AND k.column_name='ID' AND k.ordinal_position=1)
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints r
                JOIN information_schema.table_constraints f
                  ON f.constraint_catalog=r.constraint_catalog AND f.constraint_schema=r.constraint_schema
                 AND f.constraint_name=r.constraint_name
                JOIN information_schema.key_column_usage fc
                  ON fc.constraint_catalog=f.constraint_catalog AND fc.constraint_schema=f.constraint_schema
                 AND fc.constraint_name=f.constraint_name
                JOIN information_schema.table_constraints p
                  ON p.constraint_catalog=r.unique_constraint_catalog
                 AND p.constraint_schema=r.unique_constraint_schema AND p.constraint_name=r.unique_constraint_name
                JOIN information_schema.key_column_usage pc
                  ON pc.constraint_catalog=p.constraint_catalog AND pc.constraint_schema=p.constraint_schema
                 AND pc.constraint_name=p.constraint_name
                WHERE f.table_schema='PUBLIC' AND f.table_name='BOARD_CARDS'
                  AND f.constraint_name='FK_BOARD_CARDS_PROJECT'
                  AND fc.column_name='PROJECT_ID' AND fc.ordinal_position=1
                  AND p.table_schema='PUBLIC' AND p.table_name='PROJECTS'
                  AND p.constraint_type='PRIMARY KEY' AND pc.column_name='ID' AND pc.ordinal_position=1
                  AND r.delete_rule IN ('NO ACTION','RESTRICT')
                  AND r.update_rule IN ('NO ACTION','RESTRICT'))
    AND (SELECT COUNT(*) FROM information_schema.index_columns
         WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS'
           AND index_name='IDX_BOARD_CARDS_PROJECT') = 1
    AND EXISTS (SELECT 1 FROM information_schema.indexes
                WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS'
                  AND index_name='IDX_BOARD_CARDS_PROJECT'
                  AND index_type_name='INDEX' AND is_generated=FALSE)
    AND EXISTS (SELECT 1 FROM information_schema.index_columns
                WHERE index_schema='PUBLIC' AND table_name='BOARD_CARDS'
                  AND index_name='IDX_BOARD_CARDS_PROJECT'
                  AND column_name='PROJECT_ID' AND ordinal_position=1)
    AND (SELECT COUNT(*) FROM information_schema.indexes
         WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 2
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema='PUBLIC' AND table_name='PROJECT_ARCHITECTURE_PASSPORTS'
                  AND column_name='INSTANCE_ID' AND is_nullable='NO')
    AND NOT EXISTS (SELECT 1 FROM board_cards b LEFT JOIN projects p ON p.id=b.project_id
                    WHERE p.id IS NULL)
    THEN 1 ELSE 0 END AS PM0063C3_PRECONDITION_FAILED_EXPECTED_V005_SCHEMA;

ALTER TABLE board_cards ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS') = 13
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
                  AND column_name='VERSION' AND data_type='BIGINT' AND is_nullable='NO')
    AND NOT EXISTS (SELECT 1 FROM board_cards WHERE version IS NULL OR version<>0)
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints
                WHERE table_schema='PUBLIC' AND table_name='BOARD_CARDS'
                  AND constraint_type='FOREIGN KEY' AND constraint_name='FK_BOARD_CARDS_PROJECT')
    THEN 1 ELSE 0 END AS PM0063C3_POSTCONDITION_FAILED;
