package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;
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

/**
 * ExpenseServlet handles:
 *   POST   /api/expense          → add expense
 *   DELETE /api/expense/{id}     → delete expense by ID
 */
@WebServlet("/expense")
public class ExpenseServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        Reader reader = req.getReader();
        JsonObject body;
        try {
            body = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            AuthUtil.writeError(resp, 400, "Invalid JSON."); return;
        }

        String category = body.has("category") ? body.get("category").getAsString().trim() : "";
        double amount   = body.has("amount")   ? body.get("amount").getAsDouble()          : 0;
        String date     = body.has("date")     ? body.get("date").getAsString()             : "";
        String note     = body.has("note")     ? body.get("note").getAsString()             : "";

        if (category.isEmpty() || date.isEmpty() || amount <= 0) {
            AuthUtil.writeError(resp, 400, "Invalid data."); return;
        }

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO expenses (user_id, category, amount, date, note) " +
                "VALUES (?, ?, ?, ?::date, ?) RETURNING expense_id");
            ps.setInt(1, uid);
            ps.setString(2, category);
            ps.setDouble(3, amount);
            ps.setDate(4, java.sql.Date.valueOf(date)); 
            ps.setString(5, note);
            ResultSet rs = ps.executeQuery();
            rs.next();
            int eid = rs.getInt(1);

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true,\"expense_id\":" + eid + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        // Path: /api/expense/{id}  → pathInfo = "/{id}"
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            AuthUtil.writeError(resp, 400, "Missing expense ID."); return;
        }

        int expenseId;
        try {
            expenseId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            AuthUtil.writeError(resp, 400, "Invalid expense ID."); return;
        }

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM expenses WHERE expense_id = ? AND user_id = ?");
            ps.setInt(1, expenseId);
            ps.setInt(2, uid);
            ps.executeUpdate();

            AuthUtil.writeJson(resp, 200, "{\"success\":true}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
