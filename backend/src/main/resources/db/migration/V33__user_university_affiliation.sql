-- Main integration V33. Additive change; preserve existing users with NULL affiliation.
ALTER TABLE users
    ADD COLUMN university_id VARCHAR(64),
    ADD COLUMN university_name VARCHAR(150),
    ADD COLUMN department_id VARCHAR(64),
    ADD COLUMN department_name VARCHAR(150),
    ADD COLUMN affiliation_source VARCHAR(20),
    ADD COLUMN affiliation_catalog_year INTEGER;

ALTER TABLE users ADD CONSTRAINT ck_users_affiliation CHECK (
    (affiliation_source IS NULL AND university_id IS NULL AND university_name IS NULL
        AND department_id IS NULL AND department_name IS NULL AND affiliation_catalog_year IS NULL)
    OR
    (affiliation_source IS NOT NULL AND university_name IS NOT NULL AND length(trim(university_name))>0
        AND department_name IS NOT NULL AND length(trim(department_name))>0
        AND ((affiliation_source='CATALOG' AND university_id IS NOT NULL AND department_id IS NOT NULL AND affiliation_catalog_year IS NOT NULL)
          OR (affiliation_source='SELF_REPORTED' AND university_id IS NULL AND department_id IS NULL AND affiliation_catalog_year IS NULL)))
);
