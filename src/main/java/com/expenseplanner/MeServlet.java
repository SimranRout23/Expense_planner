package com.expenseplanner;

import com.expenseplanner.util.AuthUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;

/**
 * GET /api/me
 * Returns the current session user info.
 */
@WebServlet("/me")
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int    userId = AuthUtil.getCurrentUserId(req);
        String name   = AuthUtil.getCurrentUserName(req);

        if (userId < 0) {
            AuthUtil.writeJson(resp, 200, "{\"logged_in\":false}");
            return;
        }

        AuthUtil.writeJson(resp, 200,
            "{\"logged_in\":true,\"user_id\":" + userId +
            ",\"name\":\"" + AuthUtil.escapeJson(name) + "\"}");
    }
}
