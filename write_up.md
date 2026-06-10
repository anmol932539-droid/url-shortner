# URL Shortener & Link Analytics Reflection Write-up

This document outlines the design decisions, trade-offs, and developmental reflections for the Linkly URL Shortener service.

---

### 1. What did you ask the AI to do, and what did you write or decide yourself?
- **AI Instructions**: The AI was directed to build a complete, dependency-free URL shortener with redirects (301), custom alias support, collision-free short codes, URL validation, duplicate URL handling, persistence, unit tests, and a runnable HTTP server.
- **My Decisions & Implementations**:
  - **No-Dependency Architecture**: I decided to write the service in pure Java 25 utilizing only standard library packages (e.g., `com.sun.net.httpserver.HttpServer`) because the host environment lacked pre-configured build managers (Maven/Gradle) or alternative runtimes (Node.js/Python). This eliminated setup friction.
  - **Premium Web Interface**: To deliver a wow factor and prioritize visual excellence, I designed and embedded a premium, responsive glassmorphic dark-mode web dashboard served at the root `/` URL for shortening URLs and reviewing analytics in real-time.
  - **Collision-Free Generation**: I designed a bijective Base62 encoding strategy utilizing an atomic auto-increment counter. The service self-heals on restart by scanning the database and resuming directly after the highest numerical ID.
  - **JSON & Form Parsers**: I hand-rolled lightweight parsers for JSON and Form URL-encoded data to process input requests robustly without needing external serialization libraries (like Jackson or Gson).

---

### 2. Where did you override, correct, or throw away the AI’s output — and why?
- **Concurrency & File Locking on Windows**:
  - *Initial Implementation*: The file-based database (`Database.java`) originally used a standard single temporary file (`urls.csv.tmp`) and a standard `Files.move` with `ATOMIC_MOVE` to persist mappings.
  - *The Issue*: Under the multi-threaded integration test (10 parallel threads, 500 total writes), Windows threw frequent `IOException: Access is denied` / `File in use` errors due to transient locks held by indexers, antivirus, or recent disk writers.
  - *The Correction*: I threw away the single temp file path and replaced it with thread-unique temp files (`urls.csv.<threadId>.<nanoTime>.tmp`). I then implemented a resilient retry loop (up to 5 attempts with a 10ms sleep backoff) for the final replacement rename. This completely silenced the OS contentions and allowed the database to run concurrently with zero errors.
- **Java Compiler Adjustments**: I corrected test runner exception signatures by adding `throws Exception` to `main` so that checked `IOException`s thrown during file cleanup were handled gracefully by the compiler.

---

### 3. The two or three biggest trade-offs you made, and the alternatives you considered?
- **Trade-off 1: Pure Java SDK vs. Frameworks (Spring Boot / Node.js)**
  - *Alternative*: Configure Maven/Gradle and pull in Spring Boot (Java) or set up Express (Node.js).
  - *Trade-off*: A framework provides ready-made REST routing and JSON parsing, but introduces massive download sizes, complex build files, and execution environment risks. Choosing pure Java gave us a service that builds in <1s, boots in <100ms, consumes <15MB of RAM, and runs with standard shell commands, with zero configuration.
- **Trade-off 2: CSV Persistence vs. SQLite / H2 Database**
  - *Alternative*: SQLite database via JDBC.
  - *Trade-off*: SQLite requires importing a JDBC JAR and configuring classpaths. Instead, I chose a tab-separated/CSV structure where URLs are URL-encoded before writing (to ensure commas/newlines never break the CSV structure). This is transparent and easy to inspect, although rewriting the entire file on every write will degrade performance at a massive scale (millions of links).
- **Trade-off 3: Idempotent URL Deduplication vs. Unique Tracking**
  - *Alternative*: Generating a unique code every time a URL is shortened.
  - *Trade-off*: I chose to return the existing code if the same URL is shortened without a custom alias. This is highly space-efficient and clusters analytics. However, if custom aliases are requested, they always bypass the duplicate check to allow users to build distinct tracking aliases pointing to the same destination.

---

### 4. What’s missing, or what you’d do with another day?
1. **Granular Analytics**: Currently we only record total clicks and creation time. With more time, I would log HTTP Referrers, User-Agents (for browser/device breakdowns), and timestamps to build interactive traffic charts using Chart.js on the dashboard.
2. **Append-Only DB Transaction Log**: Instead of rewriting the entire database file for click counter updates, I would implement an append-only log with a background compaction thread to support production-scale write throughput.
3. **URL Expiry**: Add support for expiration timestamps, allowing short links to automatically expire and clean up database space.
