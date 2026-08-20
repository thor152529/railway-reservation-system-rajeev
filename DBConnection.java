import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    /*
     * These values MUST be supplied as environment variables.
     *
     * Vercel:
     *   Project -> Settings -> Environment Variables
     *
     * Do NOT put the real Railway password in this source file.
     */
    private static final String URL = System.getenv("DB_URL");
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    public static Connection getConnection() throws SQLException {

        if (URL == null || URL.isBlank()) {
            throw new SQLException("DB_URL environment variable is not configured.");
        }

        if (USER == null || USER.isBlank()) {
            throw new SQLException("DB_USER environment variable is not configured.");
        }

        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException("DB_PASSWORD environment variable is not configured.");
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                "MySQL JDBC driver not found. Check lib/mysql-connector-j.jar.",
                e
            );
        }

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
