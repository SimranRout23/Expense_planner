package com.expenseplanner.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AuthUtil.java
 * Shared utilities: session management, password hashing, JSON responses.
 */
public class AuthUtil {

    /** Return the logged-in user_id from the session, or -1 if not logged in. */
    public static int getCurrentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return -1;
        Object uid = session.getAttribute("user_id");
        return (uid instanceof Integer) ? (Integer) uid : -1;
    }

    /** Return the logged-in user's name, or null if not logged in. */
    public static String getCurrentUserName(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object name = session.getAttribute("name");
        return (name instanceof String) ? (String) name : null;
    }

    /** SHA-256 hash of the password (matches Python's hashlib.sha256). */
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Write a JSON string as the HTTP response with Content-Type application/json. */
    public static void writeJson(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        out.print(json);
        out.flush();
    }

    /** Convenience: write a simple error JSON. */
    public static void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status,
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    /** Minimal JSON string escaping. */
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
