package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V007__add_journal_search_trigram_index extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String database = context.getConnection().getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equals(database)) {
            return;
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            statement.execute("""
                CREATE INDEX idx_journal_entry_search_text_trgm
                ON journal_entry USING GIN (search_text gin_trgm_ops)
                """);
        }
    }
}
