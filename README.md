# SafeRoute AI

SafeRoute AI is a beginner-friendly Java full-stack mini project that recommends safer travel routes for women by combining route distance, risk-aware factors, and a demonstration machine learning model using Weka Random Forest.

## Project overview

- Backend: Spring Boot 3.x, Java 17
- Frontend: HTML, CSS, JavaScript, Bootstrap
- ML: Weka Random Forest
- Database: MySQL (demo uses H2 by default for easy execution)
- Map: Leaflet + OpenStreetMap tiles
- Build tool: Maven

## Features

- Route recommendation with demo route alternatives
- Risk prediction model for Low Risk / Moderate Risk / High Risk
- Safety score calculation from 0 to 100
- Risk-zone warnings from stored demo data
- Nearby safe location display
- SOS demo button and emergency contact management
- REST API endpoints for route logic and data access

## Important note

This project is designed as an educational demonstration. The machine learning model is trained on synthetic data in the `src/main/resources/ml` folder. It does not claim to use real crime data or verified live location information.

## Local setup

### 1. Prerequisites

- Java 17+
- Maven 3.9+
- MySQL 8 (optional for production mode)
- VS Code terminal or any Java IDE

### 2. Run with default demo database

```bash
cd saferoute
mvn spring-boot:run
```

The app uses H2 in-memory storage by default. After the server starts, open:

- http://localhost:8080/

### 3. Run with MySQL

Update the environment variables or `application.properties` for MySQL and set:

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/saferoute
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQLDialect
mvn spring-boot:run
```

## Main APIs

### Route risk prediction

POST `/api/risk/predict`

Request body:

```json
{
  "sourceLocation": "Campus Gate",
  "destinationLocation": "City Library",
  "timeOfTravel": 21,
  "lightingCondition": 2,
  "crowdDensity": 4,
  "reportedIncidentCount": 2,
  "roadIsolationLevel": 2,
  "distanceToPoliceStation": 1.5,
  "distanceToHospital": 2.1,
  "nearbyPublicPlaces": 6
}
```

Example response:

```json
{
  "riskLevel": "Low Risk",
  "safetyScore": 82.0,
  "explanation": "This result comes from a demonstration Random Forest model trained on synthetic safety data.",
  "riskFactors": [
    "Low lighting conditions reduce visibility and increase risk.",
    "The route appears reasonably balanced in terms of visibility and support access."
  ]
}
```

### Route recommendation

POST `/api/routes/recommend`

```json
{
  "source": "Campus Gate",
  "destination": "City Library",
  "travelTime": "NIGHT",
  "userId": 1
}
```

### Safety zones

GET `/api/safety-zones`

### Nearby safe locations

GET `/api/safe-locations?latitude=12.9716&longitude=77.5946`

### Emergency contacts

GET `/api/emergency-contacts?userId=1`
POST `/api/emergency-contacts`
PUT `/api/emergency-contacts/{id}`
DELETE `/api/emergency-contacts/{id}`

## Project structure

```text
src/
  main/
    java/com/saferoute/ai/
      config/
      controller/
      dto/
      model/
      repository/
      service/
      SafeRouteAiApplication.java
    resources/
      ml/
      sql/
      static/
      application.properties
  test/
    java/com/saferoute/ai/
```

## Testing

```bash
mvn test
```

## Troubleshooting

### Java or Maven not found

Check installation:

```bash
java -version
mvn -version
```

### Port 8080 already in use

Change the port in `application.properties`:

```properties
server.port=9090
```

### Weka model fails to load

Ensure the CSV file exists at:

`src/main/resources/ml/safety_risk_training.csv`

### MySQL connection errors

Verify MySQL is running and that the connection properties match your local database credentials.

## Demo and demonstration guidance

- Use the default app to show route recommendation.
- Show the risk-zone map markers and nearby safe locations.
- Demonstrate the SOS button and emergency contact save action.
- Highlight that all model and location data are sample/demo values for a classroom project.

## Future improvement ideas

- Replace demo route geometry with OSRM routing results
- Add real user authentication and password hashing
- Use actual safety datasets for model evaluation
- Store map metadata for more realistic route geometry
