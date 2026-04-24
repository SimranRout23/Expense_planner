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
 * POST /api/login
 * Body: { "email": "...", "password": "..." }
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		request.getRequestDispatcher("index.html").forward(request, response);
	}

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        Reader reader = req.getReader();
        JsonObject body;
        try {
            body = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            AuthUtil.writeError(resp, 400, "Invalid JSON body.");
            return;
        }

        String email    = body.has("email")    ? body.get("email").getAsString().trim().toLowerCase() : "";
        String password = body.has("password") ? body.get("password").getAsString()                   : "";

        try (Connection conn = DBConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id, name FROM users WHERE email = ? AND password = ?");
            ps.setString(1, email);
            ps.setString(2, AuthUtil.hashPassword(password));
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                AuthUtil.writeError(resp, 401, "Invalid email or password.");
                return;
            }

            int    userId = rs.getInt(1);
            String name   = rs.getString(2);

            HttpSession session = req.getSession(true);
            session.setAttribute("user_id", userId);
            session.setAttribute("name",    name);

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true,\"message\":\"Login successful.\",\"name\":\"" +
                AuthUtil.escapeJson(name) + "\"}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}
