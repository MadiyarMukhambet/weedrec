# WeedRec — weed-control recommendation platform (backend)

Assignment #6: NoSQL data layer and backend on MongoDB.

**Domain** (related to the planned dissertation on computer vision for weed detection):

| Concept | In the platform |
|---|---|
| Users | farmers and agronomists (`users`) and their fields (`fields`) |
| Items | weed-control products and methods (`treatments`) |
| Interactions | view, save, apply, rate (`interactions`) |
| CV model context | weed detections on field images (`detections`), weed species (`weeds`) |

## Stack

- Java 17+, Spring Boot 3.3, Spring Data MongoDB
- MongoDB 7 (Docker or a local install)
- Spring Security + JWT (jjwt), BCrypt password hashes
- springdoc-openapi (Swagger UI)

## Project structure

```
src/main/java/kz/weedrec/
├── model/        MongoDB documents and indexes
├── repository/   Spring Data repositories
├── service/      registration, search, interactions, statistics
├── controller/   REST API
├── security/     JWT filter and access rules
├── dto/          request and response bodies
├── exception/    uniform error format
├── seed/         demo data generator
└── config/       Swagger
```

## Running

### 1. Requirements
- JDK 17 or newer
- Maven 3.9+ (or the bundled `./mvnw`, or IntelliJ IDEA)
- Docker Desktop for MongoDB, or a local MongoDB 7

### 2. Start MongoDB
```bash
docker compose up -d
```
This starts MongoDB on `localhost:27018` and mongo-express on http://localhost:8081.
Host port 27018 avoids a clash with a local MongoDB service on 27017.

### 3. Start the application
```bash
./mvnw spring-boot:run
```
or run `WeedRecApplication` from IntelliJ IDEA.

On the first start against an empty database the app creates the indexes and seeds demo data. The log shows:
```
Done: weeds 12, treatments 40, users 42, fields 72, detections 234, interactions 1751
Farm styles (hidden): {CHEMICAL_INTENSIVE=12, ORGANIC=7, BUDGET=12, BALANCED=5}
```
The generator is deterministic (seed 42), so the default settings always produce these numbers.

**How the demo data works.** Every treatment has a hidden true quality. Every farmer has a hidden farm style
(chemical-intensive, organic, budget, balanced) with individual noise: an affinity for each treatment category
and a price sensitivity. Utility of a treatment for a farmer = quality + category affinity − price penalty.
Utility drives views, saves, which treatment is applied and the rating, so different farmers rate the same
treatments differently. `effectiveness` is objective and depends on quality only. The hidden parameters are
never stored in the database or exposed by the API.

### 4. API documentation
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Endpoint reference: [API.md](API.md)

In Swagger, call `POST /api/auth/login`, copy the `token`, click **Authorize** and paste it.

### Demo accounts (password `password123`)

| Email | Role | Can |
|---|---|---|
| `admin@weedrec.kz` | ADMIN | everything, including deleting treatments |
| `agronomist@weedrec.kz` | AGRONOMIST | create and update treatments |
| `farmer@weedrec.kz` | FARMER | fields, detections, interactions, profile |

Plus farmers `farmer1…farmer35@weedrec.kz` and agronomists `agro1…agro4@weedrec.kz`.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27018/weedrec` | connection string |
| `JWT_SECRET` | demo key | JWT signing secret (at least 32 characters) |
| `SEED_ENABLED` | `true` | seed demo data into an empty database |
| `SEED_FARMERS` | `35` | number of farmers (each gets 1–3 fields) |
| `SEED_AGRONOMISTS` | `4` | agronomists in addition to the demo accounts |

To re-seed, drop the database (`docker compose down -v`, or `db.dropDatabase()` in mongosh) and restart the app.

## Data model

```
users ──< fields ──< detections >── weeds
  │                                   │
  └──< interactions >── treatments >──┘ (targetWeeds)
```

### Indexes

| Collection | Index | Query it serves |
|---|---|---|
| users | `{email: 1}` unique | login, duplicate check on registration |
| fields | `{userId: 1}` | a user's fields |
| fields | `{location: "2dsphere"}` | nearby farms |
| treatments | text `{name: 3, description: 1}`, English | full-text search |
| treatments | `{category: 1, pricePerHa: 1}` | filter by category, sort by price |
| treatments | `{targetWeeds: 1}`, `{allowedCrops: 1}` (multikey) | treatments for weed X on crop Y |
| detections | `{fieldId: 1, detectedAt: -1}` | latest detections on a field |
| interactions | `{userId: 1, timestamp: -1}` | user history |
| interactions | `{treatmentId: 1, type: 1}` | per-treatment stats and average rating |
| interactions | `{userId: 1, treatmentId: 1}` | user × treatment matrix for recommendations |

Check in mongosh:
```js
use weedrec
db.treatments.getIndexes()
db.treatments.find({targetWeeds: "avena_fatua", allowedCrops: "wheat"}).explain("executionStats")
// winningPlan: IXSCAN on targetWeeds
```

There is deliberately no compound `{targetWeeds: 1, allowedCrops: 1}` index: both fields are arrays, and
MongoDB cannot build a compound index over two arrays (error 171, `cannot index parallel arrays`).
The planner picks the more selective single-field index and checks the other condition on the document.

### Design decisions

- **`interactions` is its own collection**, not an array in `users`: it grows without bound (16 MB document
  limit) and recommendations query it across users.
- **Detected weeds are embedded in `detections`**: few per image, read together, never changed.
- **The profile is embedded in `users`**: 1:1 and always read together.
- **`stats` is denormalized into `treatments`**: every catalog response and sort needs the average rating;
  it is updated on write (`$inc` and an aggregation), so reads stay cheap.
- **`targetWeeds` is an array of ids** with a multikey index instead of a link table: many-to-many without joins.

### NoSQL options compared

| Database (model) | Strengths for WeedRec | Weaknesses for WeedRec | Decision |
|---|---|---|---|
| **MongoDB** (document) | embedded profile and detections; multikey, text and 2dsphere indexes; aggregation for stats and the user × item matrix | no joins; "similar users" traversals cost more than in a graph DB | primary store |
| Redis (key-value) | microsecond reads; sorted sets for top lists and counters | no multi-attribute filters or full-text without modules; in-memory | cache for computed recommendations (Final Project) |
| Neo4j (graph) | natural user–treatment–weed–crop model; fast traversals for CF | weaker at catalog search with sorting and paging; an event log bloats the graph | alternative if the algorithm becomes graph-based |
| Cassandra (wide column) | very fast event writes at scale | one table per query; no ad-hoc filters or full-text | unnecessary at this scale |

The deciding factor is catalog search with any combination of filters (category, weed, crop, price,
rating, text): only MongoDB offers that out of the box.
