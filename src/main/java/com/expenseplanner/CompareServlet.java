package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * GET /api/compare?month=&year=
 * Compares planned budget vs actual expenses for the month.
 */

@WebServlet("/compare")
public class CompareServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        try (Connection conn = DBConnection.getConnection()) {

            // Get latest budget
            PreparedStatement bps = conn.prepareStatement(
                "SELECT bd.category, bd.planned_amount " +
                "FROM budget b JOIN budget_details bd ON b.budget_id = bd.budget_id " +
                "WHERE b.user_id = ? AND b.month = ? AND b.year = ? " +
                "  AND b.version_no = (SELECT MAX(version_no) FROM budget " +
                "                      WHERE user_id = ? AND month = ? AND year = ?)");
            bps.setInt(1, uid); bps.setInt(2, month); bps.setInt(3, year);
            bps.setInt(4, uid); bps.setInt(5, month); bps.setInt(6, year);
            ResultSet brs = bps.executeQuery();

            Map<String, Double> planned = new LinkedHashMap<>();
            while (brs.next()) {
                planned.put(brs.getString(1), brs.getDouble(2));
            }

            // Get actual expenses per category
            PreparedStatement eps = conn.prepareStatement(
                "SELECT category, SUM(amount) FROM expenses " +
                "WHERE user_id = ? " +
                "  AND EXTRACT(MONTH FROM date) = ? " +
                "  AND EXTRACT(YEAR  FROM date) = ? " +
                "GROUP BY category");
            eps.setInt(1, uid); eps.setInt(2, month); eps.setInt(3, year);
            ResultSet ers = eps.executeQuery();

            Map<String, Double> actual = new HashMap<>();
            while (ers.next()) {
                actual.put(ers.getString(1), ers.getDouble(2));
            }

            // Merge categories
            Set<String> categories = new TreeSet<>();
            categories.addAll(planned.keySet());
            categories.addAll(actual.keySet());

            double totalPlanned = 0, totalActual = 0;
            StringBuilder compSb = new StringBuilder("[");
            boolean first = true;
            for (String cat : categories) {
                double p    = planned.getOrDefault(cat, 0.0);
                double a    = actual.getOrDefault(cat, 0.0);
                double diff = p - a;
                totalPlanned += p;
                totalActual  += a;

                if (!first) compSb.append(",");
                first = false;
                compSb.append("{\"category\":\"").append(AuthUtil.escapeJson(cat)).append("\"")
                      .append(",\"planned\":").append(p)
                      .append(",\"actual\":").append(a)
                      .append(",\"difference\":").append(diff)
                      .append(",\"status\":\"").append(diff >= 0 ? "OK" : "EXCEEDED").append("\"}");
            }
            compSb.append("]");

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true" +
                ",\"comparison\":" + compSb +
                ",\"total_planned\":" + totalPlanned +
                ",\"total_actual\":"  + totalActual +
                ",\"total_diff\":"    + (totalPlanned - totalActual) + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
