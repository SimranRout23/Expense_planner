# Expense Planner

A comprehensive web-based personal finance application for tracking expenses, managing budgets, generating insights, and making predictions. Built with Java Servlets, PostgreSQL, HTML/CSS/JavaScript.

## Features
- **Expense Tracking**: Add, view, edit, delete expenses (`/expenses`).
- **Budget Management**: Set monthly budgets with versioning (`/budget`).
- **Dashboard**: Summary stats (total budget, expenses, remaining) for selected month/year (`/dashboard`).
- **Trends & Insights**: Visualize spending patterns (`/trend`, `/insights`).
- **Predictions**: Forecast future expenses (`/prediction`).
- **Comparisons**: Compare budgets/expenses across periods (`/compare`).
- **Import/Export**: CSV data handling (`/import`, `/export`).
- **User Authentication**: Register, login, logout (`/register`, `/login`, `/logout`).
- **Responsive UI**: Modern HTML/JS frontend with charts.

## Tech Stack
- **Backend**: Java 17+ (Jakarta Servlets), PostgreSQL (driver included).
- **Frontend**: HTML, CSS (style.css), JavaScript (script.js).
- **Build**: Eclipse Dynamic Web Project.
- **Database**: PostgreSQL with schema in `src/main/java/com/expenseplanner/script/init.sql`.
- **Libs**: Gson (JSON), PostgreSQL JDBC.

## Prerequisites
- Java 17+
- Apache Tomcat 10+ (Jakarta EE 10)
- PostgreSQL 13+ with database `expenseplanner` (user/password setup required).
- Eclipse IDE (optional, for development).

## Setup
1. **Clone/Extract** project.
2. **Database**:
   - Create DB: `CREATE DATABASE expenseplanner;`
   - Run `src/main/java/com/expenseplanner/script/init.sql` to setup tables (users, expenses, budget, etc.).
   - Update DB credentials (host, port, user, pass) – consider .env for env vars.
3. **Eclipse Import**:
   - File > Import > Existing Projects into Workspace.
   - Select project root.
   - Right-click project > Properties > Java Build Path > Add External JARs: `src/main/webapp/WEB-INF/lib/*.jar`.
4. **Build**: Right-click > Run As > Maven build (if Mavenized) or compile to `build/classes/`.

## Deployment
1. **Tomcat**:
   - Copy `src/main/webapp/` to Tomcat `webapps/expenseplanner/`.
   - Or export as WAR: Right-click > Export > WAR file.
   - Deploy WAR to Tomcat webapps/.
2. **Run**: Start Tomcat, access `http://localhost:8080/expenseplanner/`.
3. **Login/Register** at `/` or `/register`.

## API Endpoints
Base: `http://localhost:8080/expenseplanner/`
- `GET /dashboard?month=1&year=2024` – Dashboard JSON.
- `POST /expenses` – Add expense (JSON/form).
- Auth required for most (JWT/session via AuthUtil).

## Development
- Frontend: Edit HTML/JS/CSS in `src/main/webapp/`.
- Backend: Servlets in `src/main/java/com/expenseplanner/`.
- Charts: Uses Chart.js or similar in script.js.

## Troubleshooting
- DBConnection errors: Check PostgreSQL connection.
- 401: Not logged in.
- 500: SQL/JSON errors – check Tomcat logs.

## License
MIT License – see [LICENSE](LICENSE).

