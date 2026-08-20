# Railway Reservation System — Java + MySQL

A Java-based Railway Reservation System using **MySQL** as the database and **JDBC (MySQL Connector/J)** for database connectivity.

The project can run locally in VS Code and can also be deployed to **Vercel** using the included `Dockerfile.vercel`. For the Vercel deployment, the MySQL database must be hosted online; this setup uses **Railway MySQL**.

## Project Structure

```text
Railway Reservation App/
├── src/
│   ├── Main.java
│   ├── DBConnection.java
│   └── Json.java
├── sql/
│   └── schema.sql
├── lib/
│   └── mysql-connector-j.jar
├── Dockerfile.vercel
└── README.md
```

## Requirements

### For local development

- **JDK 17 or newer**
- **MySQL Server 8.4 LTS**
- **MySQL Workbench**
- **MySQL Connector/J**
- **VS Code** with Java support

### For deployment

- **GitHub account**
- **Vercel account**
- **Railway account** with a MySQL database
- The included `Dockerfile.vercel`

---

# Part A — Run Locally in VS Code

## 1. Add MySQL Connector/J

Download the **Platform Independent ZIP Archive** of MySQL Connector/J.

Extract the ZIP file and locate the JAR file, for example:

```text
mysql-connector-j-9.7.0.jar
```

Rename it exactly to:

```text
mysql-connector-j.jar
```

Place it in:

```text
lib/
└── mysql-connector-j.jar
```

This JAR is required for Java/JDBC to communicate with MySQL.

> Note: The Vercel Docker build downloads its own copy of MySQL Connector/J 9.7.0 automatically, so the JAR in `lib/` is primarily for local development.

## 2. Set Up the Local MySQL Database

Start **MySQL Server** and open **MySQL Workbench**.

Create the database:

```sql
CREATE DATABASE railway_reservation;
```

Then open and execute:

```text
sql/schema.sql
```

in MySQL Workbench.

The SQL file creates the tables and other database objects required by the project.

## 3. Configure the Local Database Connection

The application uses these environment variables:

```text
DB_URL
DB_USER
DB_PASSWORD
```

For Windows PowerShell:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/railway_reservation"
$env:DB_USER="root"
$env:DB_PASSWORD="your_mysql_password"
```

Replace `your_mysql_password` with the MySQL root password created during MySQL installation.

## 4. Compile and Run

Open the project folder in VS Code.

In the VS Code terminal:

```powershell
javac -cp "lib/mysql-connector-j.jar" -d out src/*.java
java -cp "out;lib/mysql-connector-j.jar" Main
```

The application should start on:

```text
http://localhost:8080
```

You should see:

```text
Railway Reservation API listening on port 8080
```

## 5. Test the Local Application

Examples:

```text
GET  http://localhost:8080/trains
GET  http://localhost:8080/passengers
GET  http://localhost:8080/bookings
GET  http://localhost:8080/reports/revenue
```

Example POST requests:

```text
POST /passengers?name=Amit&age=28&gender=Male&phone=9998887771&email=a@x.com

POST /bookings?trainId=1&passengerId=1&seatNo=12
```

---

# Part B — Deploy to Vercel Using Railway MySQL

## Deployment Architecture

Local:

```text
VS Code
   ↓
Java Application
   ↓
JDBC
   ↓
MySQL Server on your PC
```

Deployed:

```text
User
 ↓
Vercel
 ↓
Docker Container
 ↓
Java Application
 ↓
JDBC
 ↓
Railway MySQL
```

**Important:** Vercel does not use the MySQL Server installed on your PC. The deployed application needs an online MySQL database.

## 1. Create a Railway MySQL Database

Go to Railway:

```text
https://railway.com/
```

Create a new project and add a **MySQL** database.

After the MySQL service is created, obtain its connection details:

```text
MYSQLHOST
MYSQLPORT
MYSQLUSER
MYSQLPASSWORD
MYSQLDATABASE
```

Keep these values private.

## 2. Create the Database Schema on Railway MySQL

Use MySQL Workbench to connect to your Railway MySQL database.

Then execute:

```text
sql/schema.sql
```

Make sure the tables, constraints, views, triggers, procedures and other required database objects are created successfully.

## 3. Configure `DBConnection.java`

The application must read the database connection from environment variables rather than hard-coded local MySQL credentials.

For the deployed application, use:

```text
DB_URL
DB_USER
DB_PASSWORD
```

The local values can remain:

```text
DB_URL=jdbc:mysql://localhost:3306/railway_reservation
DB_USER=root
DB_PASSWORD=your_local_mysql_password
```

The Vercel values will be different.

## 4. Upload the Project to GitHub

Push the project to a GitHub repository.

Do **not** upload:

- MySQL passwords
- Railway passwords
- Vercel secrets
- Other private credentials

The project should contain:

```text
src/
sql/
Dockerfile.vercel
README.md
```

The `lib/mysql-connector-j.jar` file can remain for local development. It is not required by the Vercel Docker build because the Dockerfile downloads the JDBC driver automatically.

## 5. How `Dockerfile.vercel` Works

The included `Dockerfile.vercel`:

- Uses **Eclipse Temurin JDK 17**
- Copies the Java source files
- Downloads **MySQL Connector/J 9.7.0**
- Compiles the Java source files
- Runs the application in the container
- Uses port **80**

The important deployment flow is:

```text
Dockerfile.vercel
      ↓
JDK 17
      ↓
Download Connector/J 9.7.0
      ↓
Compile Java files
      ↓
Run Main.java
      ↓
Vercel
```

You do **not** need to manually put the Connector/J JAR into the Docker image.

## 6. Import the GitHub Repository into Vercel

Go to:

```text
https://vercel.com/
```

Then:

```text
Add New → Project → Import Git Repository
```

Select your Railway Reservation System repository.

Vercel will use the included:

```text
Dockerfile.vercel
```

to build and run the application.

## 7. Add Vercel Environment Variables

In your Vercel project:

```text
Project → Settings → Environment Variables
```

Add:

### `DB_URL`

Use your Railway MySQL host and port:

```text
jdbc:mysql://YOUR_RAILWAY_HOST:YOUR_RAILWAY_PORT/YOUR_DATABASE
```

For example:

```text
jdbc:mysql://your-host:12345/railway_reservation
```

Use the actual values provided by Railway.

### `DB_USER`

```text
YOUR_RAILWAY_MYSQL_USER
```

### `DB_PASSWORD`

```text
YOUR_RAILWAY_MYSQL_PASSWORD
```

Add the variables for the **Production** environment.

Do not put these credentials in GitHub or directly in the Java source code.

## 8. Deploy

You can deploy using the Vercel dashboard or the Vercel CLI.

Install the CLI:

```powershell
npm install -g vercel
```

Log in:

```powershell
vercel login
```

From the project directory:

```powershell
vercel
```

For production:

```powershell
vercel --prod
```

## 9. Test the Deployed API

After deployment, Vercel will provide a URL similar to:

```text
https://your-project.vercel.app
```

Test an endpoint such as:

```text
https://your-project.vercel.app/trains
```

Other available endpoints include:

```text
GET    /trains
POST   /trains
PUT    /trains/{id}
DELETE /trains/{id}

GET    /passengers
POST   /passengers

POST   /bookings
PUT    /bookings/{id}/cancel
GET    /bookings

GET    /reports/revenue
GET    /reports/no-bookings

POST   /admin/expire-waitlist
```

---

# Database Concepts Used

The project demonstrates:

- Primary keys and foreign keys
- Constraints
- Joins
- Subqueries
- Views
- Stored procedures
- Cursors
- Triggers
- `GROUP BY`
- Aggregate functions
- JDBC connectivity

The SQL implementation is contained in:

```text
sql/schema.sql
```

# Technology Stack

- **Programming Language:** Java
- **JDK:** 17
- **Database:** MySQL
- **Local Database:** MySQL Server 8.4 LTS
- **Cloud Database:** Railway MySQL
- **Database Connectivity:** JDBC
- **JDBC Driver:** MySQL Connector/J 9.7.0
- **IDE:** VS Code
- **Database Tool:** MySQL Workbench
- **Deployment:** Vercel
- **Container:** Docker
