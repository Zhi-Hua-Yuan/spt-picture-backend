-- Search projection coordination belongs to the Search database.  This migration
-- removes the transitional copy from Core while remaining safe for fresh databases.
DROP TABLE IF EXISTS search_index_state;
