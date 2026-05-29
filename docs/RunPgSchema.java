import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RunPgSchema {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_NAME = "hify_knowledge";
    private static final String USER = "postgres";
    private static final String PASSWORD = "123456";
    private static final Path SCHEMA = Path.of("hify-boot/src/main/resources/schema-pg.sql");

    public static void main(String[] args) throws Exception {
        ensureDatabase();
        String targetUrl = "jdbc:postgresql://localhost:5432/" + DB_NAME;
        try (Connection conn = DriverManager.getConnection(targetUrl, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            System.out.println("OK: CREATE EXTENSION vector");

            for (String sql : parseSqlFile(SCHEMA)) {
                stmt.execute(sql);
                System.out.println("OK: " + summarize(sql));
            }
        }
        System.out.println("Schema initialized successfully on " + DB_NAME);
    }

    private static void ensureDatabase() throws Exception {
        try (Connection conn = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + DB_NAME + "'");
            if (!rs.next()) {
                stmt.execute("CREATE DATABASE " + DB_NAME);
                System.out.println("OK: CREATE DATABASE " + DB_NAME);
            } else {
                System.out.println("SKIP: database " + DB_NAME + " already exists");
            }
        }
    }

    private static List<String> parseSqlFile(Path file) throws Exception {
        String content = Files.readString(file);
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(current.toString().trim());
                current.setLength(0);
            }
        }
        return statements;
    }

    private static String summarize(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 77) + "...";
    }
}
