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

/**
 * GET /api/dashboard?month=&year=
 * Returns summary stats for the dashboard page.
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        try (Connection conn = DBConnection.getConnection()) {

            // Total planned budget (latest version)
            PreparedStatement bps = conn.prepareStatement(
                "SELECT COALESCE(SUM(bd.planned_amount), 0) " +
                "FROM budget b JOIN budget_details bd ON b.budget_id = bd.budget_id " +
                "WHERE b.user_id = ? AND b.month = ? AND b.year = ? " +
                "  AND b.version_no = (SELECT MAX(version_no) FROM budget " +
                "                      WHERE user_id = ? AND month = ? AND year = ?)");
            bps.setInt(1, uid); bps.setInt(2, month); bps.setInt(3, year);
            bps.setInt(4, uid); bps.setInt(5, month); bps.setInt(6, year);
            ResultSet brs = bps.executeQuery();
            brs.next();
            double totalBudget = brs.getDouble(1);

            // Total expenses
            PreparedStatement eps = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses " +
                "WHERE user_id = ? " +
                "  AND EXTRACT(MONTH FROM date) = ? " +
                "  AND EXTRACT(YEAR  FROM date) = ?");
            eps.setInt(1, uid); eps.setInt(2, month); eps.setInt(3, year);
            ResultSet ers = eps.executeQuery();
            ers.next();
            double totalExpenses = ers.getDouble(1);

            // Expense count
            PreparedStatement cps = conn.prepareStatement(
                "SELECT COUNT(*) FROM expenses " +
                "WHERE user_id = ? " +
                "  AND EXTRACT(MONTH FROM date) = ? " +
                "  AND EXTRACT(YEAR  FROM date) = ?");
            cps.setInt(1, uid); cps.setInt(2, month); cps.setInt(3, year);
            ResultSet crs = cps.executeQuery();
            crs.next();
            int expenseCount = crs.getInt(1);

            // Budget version count
            PreparedStatement vps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version_no), 0) FROM budget " +
                "WHERE user_id = ? AND month = ? AND year = ?");
            vps.setInt(1, uid); vps.setInt(2, month); vps.setInt(3, year);
            ResultSet vrs = vps.executeQuery();
            vrs.next();
            int versionCount = vrs.getInt(1);

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true" +
                ",\"total_budget\":"   + totalBudget +
                ",\"total_expenses\":" + totalExpenses +
                ",\"remaining\":"      + (totalBudget - totalExpenses) +
                ",\"expense_count\":"  + expenseCount +
                ",\"version_count\":"  + versionCount + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
