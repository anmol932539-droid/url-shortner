# Setup, Run, and Test Guide

This guide provides instructions on how to install, build, run, and test the URL Shortener application on any machine (Windows, macOS, or Linux).

---

## 1. Prerequisites

You must have **Java Development Kit (JDK) 21** or higher installed on your computer.

### Installing a JDK
* **Windows**: Download and run the installer for **Eclipse Temurin JDK 21** (or newer) from [adoptium.net](https://adoptium.net/).
* **macOS**: Install via Homebrew:
  ```bash
  brew install openjdk@21
  ```
* **Linux (Debian/Ubuntu)**:
  ```bash
  sudo apt update
  sudo apt install openjdk-21-jdk
  ```

### Setting `JAVA_HOME`
Before running the commands below, make sure the `JAVA_HOME` environment variable is set to your JDK installation directory.

* **Windows (PowerShell)**:
  ```powershell
  # Replace with your actual JDK install path if different
  $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot"
  ```
* **Windows (CMD)**:
  ```cmd
  set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot
  ```
* **macOS / Linux (Bash/Zsh)**:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  ```

---

## 2. Build and Run Instructions

No pre-installed Maven is required. The project includes a self-contained Maven Wrapper (`mvnw` for Linux/macOS and `mvnw.cmd` for Windows).

Navigate to the project's root folder in your terminal and execute the commands matching your operating system:

### Compile the Project
* **Windows**:
  ```powershell
  .\mvnw.cmd clean compile
  ```
* **macOS / Linux**:
  ```bash
  chmod +x mvnw
  ./mvnw clean compile
  ```

### Run the Application
Start the Spring Boot server (running on port `8080` by default):
* **Windows**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
* **macOS / Linux**:
  ```bash
  ./mvnw spring-boot:run
  ```

Once started, open your web browser and go to:
👉 **`http://localhost:8080/`**

---

## 3. Running Automated Tests

Run the full suite of unit and integration tests (including the concurrent write load tests):

* **Windows**:
  ```powershell
  .\mvnw.cmd clean test
  ```
* **macOS / Linux**:
  ```bash
  ./mvnw clean test
  ```
