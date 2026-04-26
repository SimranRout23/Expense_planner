package com.expenseplanner;

import com.expenseplanner.db.DBConnection;
import com.expenseplanner.util.AuthUtil;
import com.google.gson.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * POST /api/import
 */
@WebServlet("/import")
public class ImportServlet extends HttpServlet {

    // Auto-categorization keyword map
    private static final Map<String, String> MERCHANT_MAP = new LinkedHashMap<>();
    static {
        // Food
        MERCHANT_MAP.put("swiggy",        "Food");
        MERCHANT_MAP.put("zomato",        "Food");
        MERCHANT_MAP.put("domino",        "Food");
        MERCHANT_MAP.put("pizza",         "Food");
        MERCHANT_MAP.put("mcdonald",      "Food");
        MERCHANT_MAP.put("kfc",           "Food");
        MERCHANT_MAP.put("restaurant",    "Food");
        MERCHANT_MAP.put("cafe",          "Food");
        MERCHANT_MAP.put("canteen",       "Food");

        // Transport
        MERCHANT_MAP.put("uber",          "Transport");
        MERCHANT_MAP.put("ola",           "Transport");
        MERCHANT_MAP.put("rapido",        "Transport");
        MERCHANT_MAP.put("metro",         "Transport");
        MERCHANT_MAP.put("irctc",         "Transport");
        MERCHANT_MAP.put("bus",           "Transport");
        MERCHANT_MAP.put("petrol",        "Transport");
        MERCHANT_MAP.put("fuel",          "Transport");

        // Shopping
        MERCHANT_MAP.put("amazon",        "Shopping");
        MERCHANT_MAP.put("flipkart",      "Shopping");
        MERCHANT_MAP.put("myntra",        "Shopping");
        MERCHANT_MAP.put("meesho",        "Shopping");
        MERCHANT_MAP.put("ajio",          "Shopping");
        MERCHANT_MAP.put("nykaa",         "Shopping");
        MERCHANT_MAP.put("store",         "Shopping");
        MERCHANT_MAP.put("mart",          "Shopping");

        // Entertainment
        MERCHANT_MAP.put("netflix",       "Entertainment");
        MERCHANT_MAP.put("hotstar",       "Entertainment");
        MERCHANT_MAP.put("prime",         "Entertainment");
        MERCHANT_MAP.put("spotify",       "Entertainment");
        MERCHANT_MAP.put("youtube",       "Entertainment");
        MERCHANT_MAP.put("pvr",           "Entertainment");
        MERCHANT_MAP.put("inox",          "Entertainment");
        MERCHANT_MAP.put("cinema",        "Entertainment");

        // Utilities
        MERCHANT_MAP.put("electricity",   "Utilities");
        MERCHANT_MAP.put("jio",           "Utilities");
        MERCHANT_MAP.put("airtel",        "Utilities");
        MERCHANT_MAP.put("bsnl",          "Utilities");
        MERCHANT_MAP.put("vi ",           "Utilities");
        MERCHANT_MAP.put("water",         "Utilities");
        MERCHANT_MAP.put("gas",           "Utilities");
        MERCHANT_MAP.put("recharge",      "Utilities");

        // Health
        MERCHANT_MAP.put("pharmacy",      "Health");
        MERCHANT_MAP.put("apollo",        "Health");
        MERCHANT_MAP.put("hospital",      "Health");
        MERCHANT_MAP.put("clinic",        "Health");
        MERCHANT_MAP.put("doctor",        "Health");
        MERCHANT_MAP.put("medic",         "Health");

        // Education
        MERCHANT_MAP.put("udemy",         "Education");
        MERCHANT_MAP.put("coursera",      "Education");
        MERCHANT_MAP.put("book",          "Education");
        MERCHANT_MAP.put("college",       "Education");
        MERCHANT_MAP.put("fee",           "Education");
        MERCHANT_MAP.put("tuition",       "Education");

        // Rent
        MERCHANT_MAP.put("rent",          "Rent");
        MERCHANT_MAP.put("landlord",      "Rent");
        MERCHANT_MAP.put("pg ",           "Rent");

        // Travel
        MERCHANT_MAP.put("hotel",         "Travel");
        MERCHANT_MAP.put("oyo",           "Travel");
        MERCHANT_MAP.put("makemytrip",    "Travel");
        MERCHANT_MAP.put("goibibo",       "Travel");
        MERCHANT_MAP.put("airport",       "Travel");
    }

    /** Auto-detect category from merchant/note text. */
    private static String detectCategory(String note) {
        if (note == null || note.isBlank()) return "Other";
        String low = note.toLowerCase();
        for (Map.Entry<String, String> e : MERCHANT_MAP.entrySet()) {
            if (low.contains(e.getKey())) return e.getValue();
        }
        return "Other";
    }

    //  clean bank/UPI text
    private static String normalizeNote(String note) {
        if (note == null) return "";

        String n = note.toLowerCase();

        n = n.replaceAll("upi/|neft|imps|rtgs|pos|txn|transfer|payment", "");
        n = n.replaceAll("[^a-z ]", " ");
        n = n.replaceAll("\\s+", " ").trim();

        return n;
    }

    //  extract meaningful keyword
    private static String extractKeyword(String note) {
        String cleaned = normalizeNote(note);
        String[] words = cleaned.split(" ");

        for (String w : words) {
            if (w.length() > 3) {
                return w;
            }
        }
        return cleaned;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int uid = AuthUtil.getCurrentUserId(req);
        if (uid < 0) { AuthUtil.writeError(resp, 401, "Not logged in."); return; }

        JsonObject body;
        try {
            body = JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (Exception ex) {
            AuthUtil.writeError(resp, 400, "Invalid JSON."); return;
        }

        String filename  = body.has("filename") ? body.get("filename").getAsString() : "upload.csv";
        JsonArray txns   = body.getAsJsonArray("transactions");
        if (txns == null || txns.size() == 0) {
            AuthUtil.writeError(resp, 400, "No transactions provided."); return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            PreparedStatement ips = conn.prepareStatement(
                "INSERT INTO payment_imports (user_id, filename, total_records) VALUES (?, ?, ?) RETURNING import_id");
            ips.setInt(1, uid);
            ips.setString(2, AuthUtil.escapeJson(filename));
            ips.setInt(3, txns.size());
            ResultSet irs = ips.executeQuery();
            irs.next();
            int importId = irs.getInt(1);

            PreparedStatement eps = conn.prepareStatement(
                "INSERT INTO expenses (user_id, category, amount, date, note, source) VALUES (?, ?, ?, ?, ?, 'import')");

            int inserted = 0;

            for (JsonElement el : txns) {
                JsonObject t = el.getAsJsonObject();
                double amount = t.get("amount").getAsDouble();
                if (amount <= 0) continue;

                String date     = t.get("date").getAsString();
                String note     = t.has("note") ? t.get("note").getAsString() : "";
                String category = t.has("category") ? t.get("category").getAsString() : "";

                // ✅ IMPROVED CATEGORY LOGIC (ONLY CHANGE)
                if (category == null || category.isBlank()
                        || category.equalsIgnoreCase("Other")
                        || category.equalsIgnoreCase("Online Payment")) {

                    String keyword = extractKeyword(note);
                    String detected = detectCategory(keyword);

                    if (detected.equals("Other")) {
                        detected = detectCategory(normalizeNote(note));
                    }

                    category = detected;
                }

                eps.setInt(1, uid);
                eps.setString(2, category);
                eps.setDouble(3, amount);
                eps.setDate(4, java.sql.Date.valueOf(date)); // unchanged
                eps.setString(5, note);
                eps.addBatch();
                inserted++;
            }

            eps.executeBatch();
            conn.commit();

            AuthUtil.writeJson(resp, 200,
                "{\"success\":true,\"import_id\":" + importId +
                ",\"inserted\":" + inserted + "}");

        } catch (Exception e) {
            AuthUtil.writeError(resp, 500, e.getMessage());
        }
    }
}