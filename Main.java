import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.sql.*;
import java.util.*;

/**
 * Railway Reservation System - REST API
 * Runs standalone (java Main) - listens on $PORT if set (Vercel), else 8080 (local/VS Code).
 *
 * Endpoints:
 *   GET    /trains                          - list all trains
 *   POST   /trains?name=&source=&dest=&seats=&fare=      - add train
 *   PUT    /trains/{id}?fare=                - update fare
 *   DELETE /trains/{id}                      - delete train
 *
 *   GET    /passengers                       - list all passengers
 *   POST   /passengers?name=&age=&gender=&phone=&email=  - add passenger
 *
 *   POST   /bookings?trainId=&passengerId=&seatNo=       - book ticket (calls stored procedure, fires trigger)
 *   PUT    /bookings/{id}/cancel             - cancel booking (fires trigger to release seat)
 *   GET    /bookings                         - full booking details (3-table JOIN)
 *
 *   GET    /reports/revenue                  - revenue per train (JOIN + GROUP BY)
 *   GET    /reports/no-bookings              - passengers with no bookings (subquery)
 *   POST   /admin/expire-waitlist?before=YYYY-MM-DD   - cursor-based cleanup procedure
 */
public class Main {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(envOr("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new RootHandler());
        server.createContext("/trains", new TrainsHandler());
        server.createContext("/passengers", new PassengersHandler());
        server.createContext("/bookings", new BookingsHandler());
        server.createContext("/reports/revenue", new RevenueHandler());
        server.createContext("/reports/no-bookings", new NoBookingsHandler());
        server.createContext("/admin/expire-waitlist", new ExpireWaitlistHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Railway Reservation API listening on port " + port);
    }

    static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    // ---------------- shared helpers ----------------

    static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = java.net.URLDecoder.decode(pair.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8);
                String val = java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, Json.message("error", message));
    }

    // Extracts trailing numeric ID from a path like /bookings/5 or /trains/5
    static Integer pathId(URI uri, String prefix) {
        String path = uri.getPath();
        if (!path.startsWith(prefix)) return null;
        String rest = path.substring(prefix.length()).replaceAll("^/", "").replaceAll("/$", "");
        // handle "/bookings/5/cancel" style
        String[] parts = rest.split("/");
        if (parts.length == 0 || parts[0].isEmpty()) return null;
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------- root / health check ----------------

    static class RootHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            sendJson(ex, 200, Json.message("status", "Railway Reservation API is running"));
        }
    }

    // ---------------- /trains ----------------

    static class TrainsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            Integer id = pathId(ex.getRequestURI(), "/trains");
            try {
                if (method.equals("GET")) {
                    listTrains(ex);
                } else if (method.equals("POST")) {
                    addTrain(ex);
                } else if (method.equals("PUT") && id != null) {
                    updateFare(ex, id);
                } else if (method.equals("DELETE") && id != null) {
                    deleteTrain(ex, id);
                } else {
                    sendError(ex, 404, "Route not found");
                }
            } catch (SQLException e) {
                sendError(ex, 500, "DB error: " + e.getMessage());
            }
        }

        void listTrains(HttpExchange ex) throws IOException, SQLException {
            List<String> rows = new ArrayList<>();
            String sql = "SELECT * FROM TRAIN";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("train_id", rs.getInt("train_id"));
                    m.put("train_name", rs.getString("train_name"));
                    m.put("source", rs.getString("source"));
                    m.put("destination", rs.getString("destination"));
                    m.put("total_seats", rs.getInt("total_seats"));
                    m.put("available_seats", rs.getInt("available_seats"));
                    m.put("fare", rs.getDouble("fare"));
                    rows.add(Json.obj(m));
                }
            }
            sendJson(ex, 200, Json.array(rows));
        }

        void addTrain(HttpExchange ex) throws IOException, SQLException {
            Map<String, String> p = queryParams(ex.getRequestURI());
            String sql = "INSERT INTO TRAIN (train_name, source, destination, total_seats, available_seats, fare) VALUES (?,?,?,?,?,?)";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                int seats = Integer.parseInt(p.getOrDefault("seats", "0"));
                ps.setString(1, p.get("name"));
                ps.setString(2, p.get("source"));
                ps.setString(3, p.get("dest"));
                ps.setInt(4, seats);
                ps.setInt(5, seats);
                ps.setDouble(6, Double.parseDouble(p.getOrDefault("fare", "0")));
                ps.executeUpdate();
                sendJson(ex, 201, Json.message("status", "Train added"));
            }
        }

        void updateFare(HttpExchange ex, int id) throws IOException, SQLException {
            Map<String, String> p = queryParams(ex.getRequestURI());
            String sql = "UPDATE TRAIN SET fare = ? WHERE train_id = ?";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setDouble(1, Double.parseDouble(p.getOrDefault("fare", "0")));
                ps.setInt(2, id);
                int rows = ps.executeUpdate();
                sendJson(ex, rows > 0 ? 200 : 404, Json.message("status", rows > 0 ? "Fare updated" : "Train not found"));
            }
        }

        void deleteTrain(HttpExchange ex, int id) throws IOException, SQLException {
            String sql = "DELETE FROM TRAIN WHERE train_id = ?";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                sendJson(ex, rows > 0 ? 200 : 404, Json.message("status", rows > 0 ? "Train deleted" : "Train not found"));
            }
        }
    }

    // ---------------- /passengers ----------------

    static class PassengersHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            try {
                if (method.equals("GET")) {
                    listPassengers(ex);
                } else if (method.equals("POST")) {
                    addPassenger(ex);
                } else {
                    sendError(ex, 404, "Route not found");
                }
            } catch (SQLException e) {
                sendError(ex, 500, "DB error: " + e.getMessage());
            }
        }

        void listPassengers(HttpExchange ex) throws IOException, SQLException {
            List<String> rows = new ArrayList<>();
            String sql = "SELECT * FROM PASSENGER";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("passenger_id", rs.getInt("passenger_id"));
                    m.put("name", rs.getString("name"));
                    m.put("age", rs.getInt("age"));
                    m.put("gender", rs.getString("gender"));
                    m.put("phone", rs.getString("phone"));
                    m.put("email", rs.getString("email"));
                    rows.add(Json.obj(m));
                }
            }
            sendJson(ex, 200, Json.array(rows));
        }

        void addPassenger(HttpExchange ex) throws IOException, SQLException {
            Map<String, String> p = queryParams(ex.getRequestURI());
            String sql = "INSERT INTO PASSENGER (name, age, gender, phone, email) VALUES (?,?,?,?,?)";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, p.get("name"));
                ps.setInt(2, Integer.parseInt(p.getOrDefault("age", "0")));
                ps.setString(3, p.get("gender"));
                ps.setString(4, p.get("phone"));
                ps.setString(5, p.get("email"));
                ps.executeUpdate();
                sendJson(ex, 201, Json.message("status", "Passenger added"));
            }
        }
    }

    // ---------------- /bookings ----------------

    static class BookingsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            Integer id = pathId(ex.getRequestURI(), "/bookings");
            try {
                if (method.equals("GET")) {
                    listBookings(ex);
                } else if (method.equals("POST") && id == null) {
                    bookTicket(ex);
                } else if (method.equals("PUT") && id != null && path.endsWith("/cancel")) {
                    cancelBooking(ex, id);
                } else {
                    sendError(ex, 404, "Route not found");
                }
            } catch (SQLException e) {
                sendError(ex, 500, "DB error: " + e.getMessage());
            }
        }

        // full 3-table JOIN, as required by CO3
        void listBookings(HttpExchange ex) throws IOException, SQLException {
            List<String> rows = new ArrayList<>();
            String sql = "SELECT b.booking_id, p.name AS passenger, t.train_name, t.source, t.destination, " +
                    "b.seat_no, b.status, pay.amount, pay.payment_status " +
                    "FROM BOOKING b " +
                    "JOIN PASSENGER p ON b.passenger_id = p.passenger_id " +
                    "JOIN TRAIN t ON b.train_id = t.train_id " +
                    "LEFT JOIN PAYMENT pay ON pay.booking_id = b.booking_id";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("booking_id", rs.getInt("booking_id"));
                    m.put("passenger", rs.getString("passenger"));
                    m.put("train_name", rs.getString("train_name"));
                    m.put("source", rs.getString("source"));
                    m.put("destination", rs.getString("destination"));
                    m.put("seat_no", rs.getInt("seat_no"));
                    m.put("status", rs.getString("status"));
                    m.put("amount", rs.getDouble("amount"));
                    m.put("payment_status", rs.getString("payment_status"));
                    rows.add(Json.obj(m));
                }
            }
            sendJson(ex, 200, Json.array(rows));
        }

        // calls the stored procedure book_ticket -> which triggers seat decrement
        void bookTicket(HttpExchange ex) throws IOException, SQLException {
            Map<String, String> p = queryParams(ex.getRequestURI());
            int trainId = Integer.parseInt(p.getOrDefault("trainId", "0"));
            int passengerId = Integer.parseInt(p.getOrDefault("passengerId", "0"));
            int seatNo = Integer.parseInt(p.getOrDefault("seatNo", "0"));

            String call = "{CALL book_ticket(?, ?, ?, ?, ?)}";
            try (Connection con = DBConnection.getConnection();
                 CallableStatement cs = con.prepareCall(call)) {
                cs.setInt(1, trainId);
                cs.setInt(2, passengerId);
                cs.setInt(3, seatNo);
                cs.registerOutParameter(4, Types.INTEGER);
                cs.registerOutParameter(5, Types.VARCHAR);
                cs.execute();

                int bookingId = cs.getInt(4);
                String message = cs.getString(5);

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("message", message);
                m.put("booking_id", bookingId);
                sendJson(ex, bookingId > 0 ? 201 : 400, Json.obj(m));
            }
        }

        // updates status -> fires trigger that releases the seat back to TRAIN
        void cancelBooking(HttpExchange ex, int id) throws IOException, SQLException {
            String sql = "UPDATE BOOKING SET status = 'CANCELLED' WHERE booking_id = ? AND status = 'CONFIRMED'";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                sendJson(ex, rows > 0 ? 200 : 404,
                        Json.message("status", rows > 0 ? "Booking cancelled, seat released by trigger" : "No matching confirmed booking"));
            }
        }
    }

    // ---------------- /reports/revenue ----------------

    static class RevenueHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("GET")) { sendError(ex, 404, "Route not found"); return; }
            List<String> rows = new ArrayList<>();
            String sql = "SELECT t.train_name, SUM(pay.amount) AS total_revenue " +
                    "FROM TRAIN t " +
                    "JOIN BOOKING b ON t.train_id = b.train_id " +
                    "JOIN PAYMENT pay ON pay.booking_id = b.booking_id " +
                    "WHERE pay.payment_status = 'PAID' " +
                    "GROUP BY t.train_name";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("train_name", rs.getString("train_name"));
                    m.put("total_revenue", rs.getDouble("total_revenue"));
                    rows.add(Json.obj(m));
                }
                sendJson(ex, 200, Json.array(rows));
            } catch (SQLException e) {
                sendError(ex, 500, "DB error: " + e.getMessage());
            }
        }
    }

    // ---------------- /reports/no-bookings ----------------

    static class NoBookingsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("GET")) { sendError(ex, 404, "Route not found"); return; }
            List<String> rows = new ArrayList<>();
            String sql = "SELECT name, phone FROM PASSENGER WHERE passenger_id NOT IN (SELECT passenger_id FROM BOOKING)";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("name"));
                    m.put("phone", rs.getString("phone"));
                    rows.add(Json.obj(m));
                }
                sendJson(ex, 200, Json.array(rows));
            } catch (SQLException e) {
                sendError(ex, 500, "DB error: " + e.getMessage());
            }
        }
    }

    // ---------------- /admin/expire-waitlist ----------------

    static class ExpireWaitlistHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("POST")) { sendError(ex, 404, "Route not found"); return; }
            Map<String, String> p = queryParams(ex.getRequestURI());
            String before = p.get("before"); // YYYY-MM-DD
            if (before == null) { sendError(ex, 400, "Missing 'before' date param"); return; }

            String call = "{CALL expire_old_waitlist(?)}";
            try (Connection con = DBConnection.getConnection();
                 CallableStatement cs = con.prepareCall(call)) {
            cs.setDate(1, java.sql.Date.valueOf(before));
                cs.execute();
                sendJson(ex, 200, Json.message("status", "Old waitlisted bookings before " + before + " expired"));
            } catch (SQLException e) {
                sendError(ex, 500, "DB error: " + e.getMessage());
            }
        }
    }
}
