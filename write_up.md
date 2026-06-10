# URL Shortener & Link Analytics Reflection Write-up (Spring Boot Migration)

This document outlines the design decisions, trade-offs, and reflections for the Linkly URL Shortener service, migrated to Spring Boot.

---

### 1. What did you ask the AI to do, and what did you write or decide yourself?
- **AI Instructions**: The AI was directed to migrate the previous raw Java implementation to Spring Boot for cleaner HTTP routing, REST controller structuring, and standardized dependency management.
- **My Decisions & Implementations**:
  - **Spring Boot Bootstrapping**: I decided to download a Maven wrapper project structure using Spring Initializr (`start.spring.io`) programmatically to introduce dependencies like `spring-boot-starter-web` and `spring-boot-starter-test` without requiring local Maven installations.
  - **Controller Restructuring**: I divided the web layer into `UrlController` (an API `@RestController` for `/shorten` and `/analytics/{code}` with native Jackson mapping) and `RedirectController` (a `@Controller` for capturing dynamic `/{code}` path variables to handle 301 redirects and return custom 404 HTML).
  - **Static Asset Routing**: I placed our premium glassmorphic dashboard UI at `src/main/resources/static/index.html` to leverage Spring Boot's automatic static file index resolution.
  - **Spring Service Integration**: Registered `Database` as `@Repository` and `UrlShortenerService` as `@Service` with property-injected configurations (`@Value`).

---

### 2. Where did you override, correct, or throw away the AI’s output — and why?
- **Java Compiler Release Restriction**:
  - *The Issue*: The default Initializr script set the Java version target to 17 in `pom.xml`. However, compiling our database persistence layer failed because it references `Thread.threadId()`, which was introduced in Java 19.
  - *The Correction*: I overrode the Java compiler version in `pom.xml` by changing the property `<java.version>17</java.version>` to `<java.version>21</java.version>`. Since the system runs on the Java 25 JDK, targeting Java 21 is fully compatible and allowed the compilation of modern thread methods.
- **PowerShell Argument Parsing**: I corrected Maven test commands by removing unescaped `-D` logger flags that PowerShell parsed incorrectly, which previously caused Maven to search for missing lifecycle phases.

---

### 3. The two or three biggest trade-offs you made, and the alternatives you considered?
- **Trade-off 1: Spring Boot vs. Raw Java `HttpServer`**
  - *Alternative*: Continue using Java's built-in `HttpServer` class.
  - *Trade-off*: While raw Java was lightweight (booting in under 100ms with zero overhead), request body parsing and routing were messy and error-prone. Migrating to Spring Boot provides robust routing, security integration, clean JSON mapping (Jackson), and proper separation of concerns, at the cost of a slightly larger footprint and 20s build compile time.
- **Trade-off 2: File Database vs. JPA with SQLite/H2**
  - *Alternative*: Spring Data JPA with an in-memory H2 or SQLite database.
  - *Trade-off*: Although JPA is standard for enterprise systems, it requires configuring database drivers, schema initializers, and datasource profiles. By keeping our custom, thread-safe, lock-guaranteed CSV Database, we maintained a highly visible, portable datastore that works instantly out of the box with zero external configuration.
- **Trade-off 3: Serving Static UI via Resources vs. Thymeleaf Views**
  - *Alternative*: Set up Thymeleaf view resolvers.
  - *Trade-off*: Placing the dashboard directly in `static/` is simple and fast, avoiding server-side templating engines while still fully integrating with the REST API.

---

### 4. What’s missing, or what you’d do with another day?
1. **Database Compaction & Append-Only Logs**: As the system grows, rewriting the CSV database file on every write becomes a bottleneck. I would write a compaction thread or transition to a relational database like PostgreSQL.
2. **Enhanced Dashboard Metrics**: Track User-Agent, Referrer headers, and click timelines to display rich interactive charts using Chart.js on the dashboard.
3. **Scheduled Cleanup**: Implement Spring's `@Scheduled` annotation to run daily cron jobs to purge expired short codes from the CSV database automatically.
