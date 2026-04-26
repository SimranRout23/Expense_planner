package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.sql.SQLException;

/**
 * BudgetServlet handles:
 *   POST   /api/budget              → create budget
 *   GET    /api/budget/latest       → get latest budget version
 *   GET    /api/budget/history      → get all versions
 *   GET    /api/budget/all-months   → list distinct months
 */
@WebServlet("/budget/*")
public class BudgetServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        createBudget(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo(); // e.g. "/latest", "/history", "/all-months"
        if (pathInfo == null) pathInfo = "";
        
        try {
		
	        switch (pathInfo) {
	            case "/latest":      getLatestBudget(req, resp);  break;
	            case "/history":     getBudgetHistory(req, resp); break;
	            case "/all-months":  getAllMonths(req, resp);      break;
	            default:
	                AuthUtil.writeError(resp, 404, "Unknown budget endpoint.");
	        }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── POST /api/budget ─────────────────────────────────────────────
    private void createBudget(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        Reader reader = req.getReader();
        JsonObject body;
        try {
            body = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            AuthUtil.writeError(resp, 400, "Invalid JSON."); return;
        }

        int month = body.get("month").getAsInt();
        int year  = body.get("year").getAsInt();
        JsonObject items = body.getAsJsonObject("items");

        try (Connection conn = DBConnection.getConnection()) {

            // Determine next version number
            PreparedStatement vps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM budget " +
                "WHERE user_id = ? AND month = ? AND year = ?");
            vps.setInt(1, uid); vps.setInt(2, month); vps.setInt(3, year);
            ResultSet vrs = vps.executeQuery();
            vrs.next();
            int versionNo = vrs.getInt(1);

            // Insert budget header
            PreparedStatement bps = conn.prepareStatement(
                "INSERT INTO budget (user_id, month, year, version_no) VALUES (?, ?, ?, ?) RETURNING budget_id");
            bps.setInt(1, uid); bps.setInt(2, month); bps.setInt(3, year); bps.setInt(4, versionNo);
            ResultSet brs = bps.executeQuery();
            brs.next();
            int budgetId = brs.getInt(1);

            // Insert budget detail rows
            PreparedStatement dps = conn.prepareStatement(
                "INSERT INTO budget_details (budget_id, category, planned_amount) VALUES (?, ?, ?)");
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                dps.setInt(1, budgetId);
                dps.setString(2, entry.getKey());
                dps.setDouble(3, entry.getValue().getAsDouble());
                dps.addBatch();
            }
            dps.executeBatch();

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true,\"budget_id\":" + budgetId +
                ",\"version_no\":" + versionNo + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }

    // ── GET /api/budget/latest ───────────────────────────────────────
    private void getLatestBudget(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "SELECT b.budget_id, b.month, b.year, b.version_no, b.created_at," +
                "       bd.category, bd.planned_amount " +
                "FROM budget b JOIN budget_details bd ON b.budget_id = bd.budget_id " +
                "WHERE b.user_id = ? AND b.month = ? AND b.year = ? " +
                "  AND b.version_no = (SELECT MAX(version_no) FROM budget " +
                "                      WHERE user_id = ? AND month = ? AND year = ?) " +
                "ORDER BY bd.category");
            ps.setInt(1, uid); ps.setInt(2, month); ps.setInt(3, year);
            ps.setInt(4, uid); ps.setInt(5, month); ps.setInt(6, year);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                AuthUtil.writeError(resp, 404, "No budget found."); return;
            }

            int    budgetId  = rs.getInt(1);
            int    m         = rs.getInt(2);
            int    y         = rs.getInt(3);
            int    versionNo = rs.getInt(4);
            String createdAt = rs.getString(5);

            StringBuilder items = new StringBuilder("{");
            boolean first = true;
            do {
                if (!first) items.append(",");
                items.append("\"").append(AuthUtil.escapeJson(rs.getString(6))).append("\":")
                     .append(rs.getDouble(7));
                first = false;
            } while (rs.next());
            items.append("}");

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true,\"budget\":{" +
                "\"budget_id\":" + budgetId +
                ",\"month\":"    + m +
                ",\"year\":"     + y +
                ",\"version_no\":" + versionNo +
                ",\"created_at\":\"" + AuthUtil.escapeJson(createdAt) + "\"" +
                ",\"items\":"    + items +
                "}}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }

    // ── GET /api/budget/history ──────────────────────────────────────
    private void getBudgetHistory(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "SELECT b.budget_id, b.version_no, b.created_at, bd.category, bd.planned_amount " +
                "FROM budget b JOIN budget_details bd ON b.budget_id = bd.budget_id " +
                "WHERE b.user_id = ? AND b.month = ? AND b.year = ? " +
                "ORDER BY b.version_no, bd.category");
            ps.setInt(1, uid); ps.setInt(2, month); ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();

            // Group by version
            LinkedHashMap<Integer, Map<String, Object>> versions = new LinkedHashMap<>();
            while (rs.next()) {
                int    vno       = rs.getInt(2);
                String createdAt = rs.getString(3);
                String category  = rs.getString(4);
                double amount    = rs.getDouble(5);
                int budgetId = rs.getInt(1);

                versions.computeIfAbsent(vno, k -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("budget_id",  budgetId);
                    v.put("version_no", vno);
                    v.put("created_at", createdAt);
                    v.put("items",      new LinkedHashMap<String, Double>());
                    return v;
                });
                @SuppressWarnings("unchecked")
                Map<String, Double> itemsMap = (Map<String, Double>) versions.get(vno).get("items");
                itemsMap.put(category, amount);
            }

            // Build JSON manually
            StringBuilder sb = new StringBuilder("{\"success\":true,\"versions\":[");
            boolean firstV = true;
            for (Map<String, Object> v : versions.values()) {
                if (!firstV) sb.append(",");
                firstV = false;
                sb.append("{\"budget_id\":").append(v.get("budget_id"))
                  .append(",\"version_no\":").append(v.get("version_no"))
                  .append(",\"created_at\":\"").append(AuthUtil.escapeJson((String) v.get("created_at"))).append("\"")
                  .append(",\"items\":{");
                @SuppressWarnings("unchecked")
                Map<String, Double> items = (Map<String, Double>) v.get("items");
                boolean firstI = true;
                for (Map.Entry<String, Double> e : items.entrySet()) {
                    if (!firstI) sb.append(",");
                    firstI = false;
                    sb.append("\"").append(AuthUtil.escapeJson(e.getKey())).append("\":").append(e.getValue());
                }
                sb.append("}}");
            }
            sb.append("]}");

            AuthUtil.writeJson(resp, 200, sb.toString());

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }

    // ── GET /api/budget/all-months ───────────────────────────────────
    private void getAllMonths(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT month, year FROM budget WHERE user_id = ? " +
                "ORDER BY year DESC, month DESC");
            ps.setInt(1, uid);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("{\"success\":true,\"months\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"month\":").append(rs.getInt(1))
                  .append(",\"year\":").append(rs.getInt(2)).append("}");
            }
            sb.append("]}");

            AuthUtil.writeJson(resp, 200, sb.toString());

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
