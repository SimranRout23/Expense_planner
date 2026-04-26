package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.*;
import java.sql.*;

/**
 * GET /api/export?month=&year=
 * Streams a CSV file containing all expenses for the given month.
 * The browser will prompt a download because of the Content-Disposition header.
 */

@WebServlet("/export")
public class ExportServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        int month = Integer.parseInt(req.getParameter("month"));
        int year  = Integer.parseInt(req.getParameter("year"));

        String[] mNames = {"","January","February","March","April","May","June",
                           "July","August","September","October","November","December"};
        String filename = "ExpenseReport_" + mNames[month] + "_" + year + ".csv";

        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        resp.setCharacterEncoding("UTF-8");

        try (Connection conn = DBConnection.getConnection();
             PrintWriter out = resp.getWriter()) {

            // Header row
            out.println("Date,Category,Amount (INR),Note,Source");

            // Expense rows
            PreparedStatement ps = conn.prepareStatement(
                "SELECT date, category, amount, note, source " +
                "FROM expenses " +
                "WHERE user_id = ? " +
                "  AND EXTRACT(MONTH FROM date) = ? " +
                "  AND EXTRACT(YEAR  FROM date) = ? " +
                "ORDER BY date ASC");
            ps.setInt(1, uid); ps.setInt(2, month); ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();

            double total = 0;
            int count = 0;
            while (rs.next()) {
                double amt = rs.getDouble("amount");
                total += amt;
                count++;
                String note = rs.getString("note");
                out.println(
                    rs.getString("date") + "," +
                    csvEscape(rs.getString("category")) + "," +
                    String.format("%.2f", amt) + "," +
                    csvEscape(note != null ? note : "") + "," +
                    csvEscape(rs.getString("source"))
                );
            }

            // Budget summary section
            out.println();
            out.println("--- BUDGET SUMMARY ---");

            PreparedStatement bps = conn.prepareStatement(
                "SELECT bd.category, bd.planned_amount FROM budget b " +
                "JOIN budget_details bd ON b.budget_id = bd.budget_id " +
                "WHERE b.user_id=? AND b.month=? AND b.year=? " +
                "  AND b.version_no=(SELECT MAX(version_no) FROM budget " +
                "                    WHERE user_id=? AND month=? AND year=?)");
            bps.setInt(1,uid); bps.setInt(2,month); bps.setInt(3,year);
            bps.setInt(4,uid); bps.setInt(5,month); bps.setInt(6,year);
            ResultSet brs = bps.executeQuery();

            double totalBudget = 0;
            out.println("Category,Planned Budget (INR)");
            while (brs.next()) {
                double b = brs.getDouble("planned_amount");
                totalBudget += b;
                out.println(csvEscape(brs.getString("category")) + "," + String.format("%.2f", b));
            }

            out.println();
            out.println("Total Transactions," + count);
            out.println("Total Spent,₹" + String.format("%.2f", total));
            out.println("Total Budget,₹" + String.format("%.2f", totalBudget));
            out.println("Remaining,₹" + String.format("%.2f", totalBudget - total));
            out.println("Report Month," + mNames[month] + " " + year);

        } catch (Exception e) {
            resp.setContentType("application/json");
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
