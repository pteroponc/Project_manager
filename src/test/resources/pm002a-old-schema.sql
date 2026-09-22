CREATE TABLE projects (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    health VARCHAR(255) NOT NULL,
    delivery_model VARCHAR(255),
    budget INTEGER NOT NULL,
    progress INTEGER NOT NULL,
    quarter VARCHAR(255) NOT NULL,
    deadline VARCHAR(255) NOT NULL,
    milestone VARCHAR(1000) NOT NULL,
    risk VARCHAR(10000) NOT NULL,
    dependency VARCHAR(10000) NOT NULL,
    kpi_name VARCHAR(1000) NOT NULL,
    kpi_target VARCHAR(1000) NOT NULL,
    summary VARCHAR(10000) NOT NULL
);

CREATE TABLE board_cards (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    column_key VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    due_date VARCHAR(255) NOT NULL,
    priority VARCHAR(255),
    labels VARCHAR(255),
    estimate INTEGER,
    blocked BOOLEAN
);

CREATE TABLE release_trains (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    cadence VARCHAR(255) NOT NULL,
    quarter VARCHAR(255) NOT NULL,
    planned_release_date VARCHAR(255) NOT NULL,
    code_freeze_date VARCHAR(255) NOT NULL,
    qa_freeze_date VARCHAR(255) NOT NULL,
    go_live_date VARCHAR(255) NOT NULL,
    capacity_points INTEGER NOT NULL,
    committed_points INTEGER NOT NULL,
    readiness INTEGER NOT NULL,
    blocked_items INTEGER NOT NULL,
    scope VARCHAR(10000) NOT NULL,
    risk VARCHAR(10000) NOT NULL,
    decision VARCHAR(10000) NOT NULL
);
