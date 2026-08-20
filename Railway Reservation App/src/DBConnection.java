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
    private static final String URL = System.getenv("jdbc:mysql://trolley.proxy.rlwy.net:47067/railway");
    private static final String USER = System.getenv("root");
    private static final String PASSWORD = System.getenv("fGLxpkbDqNqdSUcNMpMhqmteYcwijGRs");

    public static Connection getConnection() throws SQLException {

        if (URL == null || URL.isBlank()) {
            throw new SQLException("jdbc:mysql://trolley.proxy.rlwy.net:47067/railway environment variable is not configured.");
        }

        if (USER == null || USER.isBlank()) {
            throw new SQLException("root environment variable is not configured.");
        }

        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException("fGLxpkbDqNqdSUcNMpMhqmteYcwijGRs environment variable is not configured.");
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
