-- Ensure pmis user has full access to ydsz-pmis database and public schema
GRANT ALL ON DATABASE "ydsz-pmis" TO pmis;
GRANT USAGE, CREATE ON SCHEMA public TO pmis;
GRANT ALL ON SCHEMA public TO pmis;
-- Make pmis owner of public schema
ALTER SCHEMA public OWNER TO pmis;
-- Grant on all existing objects
GRANT ALL ON ALL TABLES IN SCHEMA public TO pmis;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO pmis;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO pmis;
-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES FOR ROLE pmis IN SCHEMA public GRANT ALL ON TABLES TO pmis;
ALTER DEFAULT PRIVILEGES FOR ROLE pmis IN SCHEMA public GRANT ALL ON SEQUENCES TO pmis;
ALTER DEFAULT PRIVILEGES FOR ROLE pmis IN SCHEMA public GRANT ALL ON FUNCTIONS TO pmis;
-- Also for postgres (in case objects are created by postgres user)
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON TABLES TO pmis;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON SEQUENCES TO pmis;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON FUNCTIONS TO pmis;
