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
 * GET /api/expenses?month=&year=
 * Returns all expenses for the given month/year for the logged-in user.
 */
@WebServlet("/expenses")
public class ExpensesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "SELECT expense_id, category, amount, date, note FROM expenses " +
                "WHERE user_id = ? " +
                "  AND EXTRACT(MONTH FROM date) = ? " +
                "  AND EXTRACT(YEAR  FROM date) = ? " +
                "ORDER BY date DESC");
            ps.setInt(1, uid);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("{\"success\":true,\"expenses\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                String note = rs.getString(5);
                sb.append("{\"expense_id\":").append(rs.getInt(1))
                  .append(",\"category\":\"").append(AuthUtil.escapeJson(rs.getString(2))).append("\"")
                  .append(",\"amount\":").append(rs.getDouble(3))
                  .append(",\"date\":\"").append(rs.getString(4)).append("\"")
                  .append(",\"note\":\"").append(note != null ? AuthUtil.escapeJson(note) : "").append("\"")
                  .append("}");
            }
            sb.append("]}");

            AuthUtil.writeJson(resp, 200, sb.toString());

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
