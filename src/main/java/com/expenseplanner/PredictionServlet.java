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
import java.util.*;

/**
 * GET /api/prediction
 * Predicts next month's spending using the average of the last 3 months.
 * Also returns a yearly projection ("if you continue like this...").
 */
@WebServlet("/prediction")
public class PredictionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        try (Connection conn = DBConnection.getConnection()) {

            // Monthly totals per category over last 3 completed months
            PreparedStatement ps = conn.prepareStatement(
                "SELECT category, " +
                "       EXTRACT(MONTH FROM date) AS mon, " +
                "       EXTRACT(YEAR  FROM date) AS yr,  " +
                "       SUM(amount) AS monthly_total " +
                "FROM expenses " +
                "WHERE user_id = ? " +
                "  AND date >= date_trunc('month', NOW()) - INTERVAL '3 months' " +
                "  AND date <  date_trunc('month', NOW()) " +
                "GROUP BY category, mon, yr");
            ps.setInt(1, uid);
            ResultSet rs = ps.executeQuery();

            // Group: category -> list of monthly totals
            Map<String, List<Double>> catData = new LinkedHashMap<>();
            while (rs.next()) {
                String cat = rs.getString("category");
                double amt = rs.getDouble("monthly_total");
                catData.computeIfAbsent(cat, k -> new ArrayList<>()).add(amt);
            }

            // Also get monthly totals (for trend)
            PreparedStatement tps = conn.prepareStatement(
                "SELECT EXTRACT(MONTH FROM date) AS mon, " +
                "       EXTRACT(YEAR  FROM date) AS yr,  " +
                "       SUM(amount) AS total " +
                "FROM expenses " +
                "WHERE user_id = ? " +
                "  AND date >= date_trunc('month', NOW()) - INTERVAL '6 months' " +
                "GROUP BY mon, yr ORDER BY yr, mon");
            tps.setInt(1, uid);
            ResultSet trs = tps.executeQuery();

            StringBuilder trendJson = new StringBuilder("[");
            boolean firstT = true;
            String[] mNames = {"","Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            while (trs.next()) {
                if (!firstT) trendJson.append(",");
                firstT = false;
                int m = trs.getInt("mon");
                int y = trs.getInt("yr");
                trendJson.append("{\"label\":\"").append(mNames[m]).append(" ").append(y)
                    .append("\",\"total\":").append(trs.getDouble("total")).append("}");
            }
            trendJson.append("]");

            // Predict per category (simple average of available months)
            double predictedTotal = 0;
            StringBuilder catJson = new StringBuilder("[");
            boolean firstC = true;
            for (Map.Entry<String, List<Double>> e : catData.entrySet()) {
                double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
                predictedTotal += avg;
                if (!firstC) catJson.append(",");
                firstC = false;
                catJson.append("{\"category\":\"").append(AuthUtil.escapeJson(e.getKey()))
                    .append("\",\"predicted\":").append(String.format("%.2f", avg)).append("}");
            }
            catJson.append("]");

            // Yearly projection
            double yearlyProjection = predictedTotal * 12;

            // Months of data used
            PreparedStatement mps = conn.prepareStatement(
                "SELECT COUNT(DISTINCT (EXTRACT(YEAR FROM date)*100 + EXTRACT(MONTH FROM date))) " +
                "FROM expenses WHERE user_id=? " +
                "  AND date >= date_trunc('month',NOW()) - INTERVAL '3 months' " +
                "  AND date <  date_trunc('month',NOW())");
            mps.setInt(1, uid);
            ResultSet mrs = mps.executeQuery();
            int monthsUsed = mrs.next() ? mrs.getInt(1) : 0;

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true" +
                ",\"months_used\":"       + monthsUsed +
                ",\"predicted_total\":"   + String.format("%.2f", predictedTotal) +
                ",\"yearly_projection\":" + String.format("%.2f", yearlyProjection) +
                ",\"categories\":"        + catJson +
                ",\"trend\":"             + trendJson + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
