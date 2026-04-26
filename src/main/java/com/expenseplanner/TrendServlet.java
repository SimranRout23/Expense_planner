package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;

/**
 * GET /api/trend?months=6
 * Returns monthly spending totals for the last N months.
 * Used to render the multi-month trend line chart.
 */
@WebServlet("/trend")
public class TrendServlet extends HttpServlet {

    private static final String[] MONTH_NAMES =
        {"","Jan","Feb","Mar","Apr","May","Jun",
         "Jul","Aug","Sep","Oct","Nov","Dec"};

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        String mParam = req.getParameter("months");
        int months = (mParam != null) ? Integer.parseInt(mParam) : 6;
        if (months < 1 || months > 24) months = 6;

        try (Connection conn = DBConnection.getConnection()) {

            // Monthly totals
            PreparedStatement ps = conn.prepareStatement(
                "SELECT EXTRACT(MONTH FROM date) AS mon, " +
                "       EXTRACT(YEAR  FROM date) AS yr, " +
                "       SUM(amount) AS total " +
                "FROM expenses " +
                "WHERE user_id = ? " +
                "  AND date >= date_trunc('month', NOW()) - INTERVAL '" + months + " months' " +
                "GROUP BY mon, yr " +
                "ORDER BY yr ASC, mon ASC");
            ps.setInt(1, uid);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("{\"success\":true,\"trend\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                int m = rs.getInt("mon");
                int y = rs.getInt("yr");
                sb.append("{\"month\":").append(m)
                  .append(",\"year\":").append(y)
                  .append(",\"label\":\"").append(MONTH_NAMES[m]).append(" ").append(y).append("\"")
                  .append(",\"total\":").append(rs.getDouble("total")).append("}");
            }
            sb.append("]}");

            // Category-wise multi-month breakdown (for stacked bar chart)
            PreparedStatement cps = conn.prepareStatement(
                "SELECT EXTRACT(MONTH FROM date) AS mon, " +
                "       EXTRACT(YEAR  FROM date) AS yr, " +
                "       category, SUM(amount) AS total " +
                "FROM expenses " +
                "WHERE user_id = ? " +
                "  AND date >= date_trunc('month', NOW()) - INTERVAL '" + months + " months' " +
                "GROUP BY mon, yr, category " +
                "ORDER BY yr ASC, mon ASC");
            cps.setInt(1, uid);
            ResultSet crs = cps.executeQuery();

            StringBuilder csb = new StringBuilder(",\"by_category\":[");
            boolean firstC = true;
            while (crs.next()) {
                if (!firstC) csb.append(",");
                firstC = false;
                csb.append("{\"month\":").append(crs.getInt("mon"))
                   .append(",\"year\":").append(crs.getInt("yr"))
                   .append(",\"category\":\"").append(AuthUtil.escapeJson(crs.getString("category"))).append("\"")
                   .append(",\"total\":").append(crs.getDouble("total")).append("}");
            }
            csb.append("]}");

            // Splice the two JSON parts
            String result = sb.toString();
            result = result.substring(0, result.length() - 1) + csb;
            AuthUtil.writeJson(resp, 200, result);

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
