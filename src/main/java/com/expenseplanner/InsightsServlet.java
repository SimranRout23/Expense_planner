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
 * GET /api/insights?month=&year=
 * Analyses spending vs budget and returns smart suggestions.
 */
@WebServlet("/insights")
public class InsightsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        try (Connection conn = DBConnection.getConnection()) {

            // 1. Get latest budget per category
            Map<String, Double> budgetMap = new LinkedHashMap<>();
            PreparedStatement bps = conn.prepareStatement(
                "SELECT bd.category, bd.planned_amount " +
                "FROM budget b JOIN budget_details bd ON b.budget_id = bd.budget_id " +
                "WHERE b.user_id=? AND b.month=? AND b.year=? " +
                "  AND b.version_no=(SELECT MAX(version_no) FROM budget " +
                "                    WHERE user_id=? AND month=? AND year=?)");
            bps.setInt(1,uid); bps.setInt(2,month); bps.setInt(3,year);
            bps.setInt(4,uid); bps.setInt(5,month); bps.setInt(6,year);
            ResultSet brs = bps.executeQuery();
            while (brs.next()) budgetMap.put(brs.getString(1), brs.getDouble(2));

            // 2. Get actual spending per category
            Map<String, Double> actualMap = new LinkedHashMap<>();
            double totalActual = 0;
            PreparedStatement eps = conn.prepareStatement(
                "SELECT category, SUM(amount) AS total FROM expenses " +
                "WHERE user_id=? AND EXTRACT(MONTH FROM date)=? AND EXTRACT(YEAR FROM date)=? " +
                "GROUP BY category ORDER BY total DESC");
            eps.setInt(1,uid); eps.setInt(2,month); eps.setInt(3,year);
            ResultSet ers = eps.executeQuery();
            while (ers.next()) {
                actualMap.put(ers.getString(1), ers.getDouble(2));
                totalActual += ers.getDouble(2);
            }

            // 3. Total budget
            double totalBudget = budgetMap.values().stream().mapToDouble(Double::doubleValue).sum();

            // 4. Build insights list
            List<String> insights = new ArrayList<>();

            // Overall budget usage
            if (totalBudget > 0) {
                double pct = (totalActual / totalBudget) * 100;
                if (pct >= 100)
                    insights.add("{\"type\":\"danger\",\"icon\":\"🔴\",\"msg\":\"You have exceeded your total budget! Spent " +
                            String.format("%.1f", pct) + "% of planned amount.\"}");
                else if (pct >= 80)
                    insights.add("{\"type\":\"warning\",\"icon\":\"🟡\",\"msg\":\"You've used " +
                            String.format("%.1f", pct) + "% of your budget. Slow down spending!\"}");
                else if (pct <= 40 && totalActual > 0)
                    insights.add("{\"type\":\"success\",\"icon\":\"🟢\",\"msg\":\"Great discipline! You've only used " +
                            String.format("%.1f", pct) + "% of your budget so far.\"}");
            }

            // Per-category overspend alerts
            for (Map.Entry<String, Double> e : actualMap.entrySet()) {
                String cat = e.getKey();
                double actual  = e.getValue();
                double planned = budgetMap.getOrDefault(cat, 0.0);
                if (planned > 0 && actual > planned) {
                    double over = actual - planned;
                    insights.add("{\"type\":\"warning\",\"icon\":\"⚠️\",\"msg\":\"Overspent on " + cat +
                            " by ₹" + String.format("%.2f", over) + " (budget: ₹" +
                            String.format("%.2f", planned) + ", spent: ₹" +
                            String.format("%.2f", actual) + ").\"}");
                }
            }

            // Top spending category
            if (!actualMap.isEmpty()) {
                Map.Entry<String, Double> top = actualMap.entrySet().iterator().next();
                insights.add("{\"type\":\"info\",\"icon\":\"📊\",\"msg\":\"Your biggest expense this month is " +
                        top.getKey() + " at ₹" + String.format("%.2f", top.getValue()) + ".\"}");
            }

            // Categories with 0 actual but budget set (savings)
            for (Map.Entry<String, Double> e : budgetMap.entrySet()) {
                if (e.getValue() > 0 && !actualMap.containsKey(e.getKey())) {
                    insights.add("{\"type\":\"success\",\"icon\":\"💚\",\"msg\":\"You spent ₹0 on " +
                            e.getKey() + " this month. Great saving!\"}");
                }
            }

            // Savings tip
            if (totalBudget > 0 && totalActual < totalBudget) {
                double saved = totalBudget - totalActual;
                insights.add("{\"type\":\"success\",\"icon\":\"💰\",\"msg\":\"You have ₹" +
                        String.format("%.2f", saved) + " remaining. Consider moving it to savings.\"}");
            }

            // No budget set
            if (totalBudget == 0 && totalActual > 0)
                insights.add("{\"type\":\"info\",\"icon\":\"📋\",\"msg\":\"You have no budget set for this month. Set a budget to track your progress!\"}");

            // Category breakdown JSON
            StringBuilder catBreakdown = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<String, Double> e : actualMap.entrySet()) {
                if (!first) catBreakdown.append(",");
                first = false;
                double pct = totalActual > 0 ? (e.getValue() / totalActual) * 100 : 0;
                catBreakdown.append("{\"category\":\"").append(AuthUtil.escapeJson(e.getKey()))
                    .append("\",\"amount\":").append(e.getValue())
                    .append(",\"pct\":").append(String.format("%.1f", pct)).append("}");
            }
            catBreakdown.append("]");

            StringBuilder insightsJson = new StringBuilder("[");
            for (int i = 0; i < insights.size(); i++) {
                if (i > 0) insightsJson.append(",");
                insightsJson.append(insights.get(i));
            }
            insightsJson.append("]");

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true" +
                ",\"total_budget\":"  + totalBudget +
                ",\"total_actual\":"  + totalActual +
                ",\"savings\":"       + (totalBudget - totalActual) +
                ",\"insights\":"      + insightsJson +
                ",\"breakdown\":"     + catBreakdown + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
