package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * POST /api/register
 * Body: { "name": "...", "email": "...", "password": "..." }
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Parse JSON body
        Reader reader = req.getReader();
        JsonObject body;
        try {
            body = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            AuthUtil.writeError(resp, 400, "Invalid JSON body.");
            return;
        }

        String name     = body.has("name")     ? body.get("name").getAsString().trim()           : "";
        String email    = body.has("email")    ? body.get("email").getAsString().trim().toLowerCase() : "";
        String password = body.has("password") ? body.get("password").getAsString()              : "";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            AuthUtil.writeError(resp, 400, "All fields are required.");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {

            // Check duplicate email
            PreparedStatement check = conn.prepareStatement(
                "SELECT user_id FROM users WHERE email = ?");
            check.setString(1, email);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                AuthUtil.writeError(resp, 409, "Email already registered.");
                return;
            }

            // Insert user
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO users (name, email, password) VALUES (?, ?, ?) RETURNING user_id");
            ins.setString(1, name);
            ins.setString(2, email);
            ins.setString(3, AuthUtil.hashPassword(password));
            ResultSet rs2 = ins.executeQuery();
            rs2.next();
            int userId = rs2.getInt(1);

            // Set session
            HttpSession session = req.getSession(true);
            session.setAttribute("user_id", userId);
            session.setAttribute("name",    name);

            AuthUtil.writeJson(resp, 201,
                "{\"success\":true,\"message\":\"Registered successfully.\",\"name\":\"" +
                AuthUtil.escapeJson(name) + "\"}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
