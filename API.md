# WeedRec API reference

Base URL: `http://localhost:8080`. JSON everywhere. Interactive version: Swagger UI at `/swagger-ui.html`.

**Authentication:** header `Authorization: Bearer <token>`. Tokens come from `/api/auth/register` and
`/api/auth/login` and are valid for 24 hours.
🔓 public, 🔒 token required, 🛡 role required.

**Error format** (all endpoints):
```json
{ "timestamp": "2026-11-03T15:00:00Z", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "details": { "email": "must be a well-formed email address" } }
```
Codes: `400` invalid input, `401` missing or invalid token, `403` insufficient role, `404` not found,
`409` email already registered.

---

## 1. Authentication

### POST /api/auth/register 🔓
`role` is `FARMER` (default) or `AGRONOMIST`.
```json
{
  "email": "new.farmer@mail.kz",
  "password": "secret123",
  "fullName": "Yerlan Kassymov",
  "farmName": "Kolos Farm",
  "region": "Kostanay Region",
  "experienceYears": 5,
  "phone": "+7 701 1234567",
  "role": "FARMER"
}
```
Response `201`:
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "user": { "id": "6727...", "email": "new.farmer@mail.kz", "role": "FARMER",
            "profile": { "fullName": "Yerlan Kassymov", "farmName": "Kolos Farm", "...": "..." },
            "createdAt": "2026-11-03T15:00:00Z" }
}
```

### POST /api/auth/login 🔓
```json
{ "email": "farmer@weedrec.kz", "password": "password123" }
```
Response `200`, same shape as registration. Wrong credentials → `401`.

---

## 2. Profile and history

### GET /api/users/me 🔒
Current user with profile.

### PUT /api/users/me/profile 🔒
```json
{ "fullName": "Askhat Nurlanov", "farmName": "Bereke Farm", "region": "Akmola Region",
  "experienceYears": 9, "phone": "+7 701 0000000" }
```

### GET /api/users/me/history 🔒
Interaction history, newest first.

| Parameter | Description |
|---|---|
| `type` | optional: `VIEW`, `SAVE`, `APPLY`, `RATING` |
| `page`, `size` | paging (default 0 and 20, max size 100) |

```json
{ "content": [ { "id": "...", "userId": "...", "treatmentId": "...", "type": "RATING",
                 "rating": 4, "effectiveness": 82, "timestamp": "2026-10-01T09:00:00Z" } ],
  "page": 0, "size": 20, "totalElements": 37, "totalPages": 2 }
```

---

## 3. Treatment catalog and search

### GET /api/treatments 🔓
All parameters are optional and combined with AND.

| Parameter | Example | Description |
|---|---|---|
| `q` | `wild oat` | full-text search over name and description |
| `name` | `fenox` | case-insensitive substring of the name |
| `category` | `CHEMICAL` | `CHEMICAL`, `MECHANICAL`, `BIOLOGICAL`, `AGROTECHNICAL` |
| `weed` | `avena_fatua` | targets this weed species (id from `/api/weeds`) |
| `crop` | `wheat` | allowed on this crop |
| `minPrice`, `maxPrice` | `2000`, `6000` | price, KZT per hectare |
| `minRating` | `4` | minimum average rating |
| `sort` | `price_asc` | `relevance` (with `q`), `price_asc`, `price_desc`, `rating`, `popular`, `name` |
| `page`, `size` | `0`, `20` | paging |

Examples:
```
GET /api/treatments?weed=avena_fatua&crop=wheat&sort=rating
GET /api/treatments?q=thistle
GET /api/treatments?category=MECHANICAL&maxPrice=2000
```
Item:
```json
{
  "id": "6727...",
  "name": "Agrogard Fenox EC",
  "description": "Herbicide based on fenoxaprop-P-ethyl ...",
  "category": "CHEMICAL",
  "activeIngredient": "fenoxaprop-P-ethyl",
  "targetWeeds": ["avena_fatua", "setaria_viridis", "echinochloa_crus_galli"],
  "allowedCrops": ["wheat", "barley"],
  "dosage": { "value": 0.8, "unit": "L/ha" },
  "pricePerHa": 6240.0,
  "stats": { "avgRating": 4.21, "ratingsCount": 14, "applications": 22, "views": 61 },
  "score": 1.5
}
```
`score` is the relevance, present only for searches with `q`.

### GET /api/treatments/{id} 🔓
A single treatment. With a token, a `VIEW` interaction is recorded automatically.

### POST /api/treatments 🛡 ADMIN, AGRONOMIST
```json
{
  "name": "Niva-Pro Clopyr WG",
  "description": "Against creeping thistle and sow-thistle in rapeseed",
  "category": "CHEMICAL",
  "activeIngredient": "clopyralid",
  "targetWeeds": ["cirsium_arvense", "sonchus_arvensis"],
  "allowedCrops": ["rapeseed", "wheat"],
  "dosage": { "value": 0.3, "unit": "L/ha" },
  "pricePerHa": 5300
}
```
Response `201`. Unknown weed id → `400`.

### PUT /api/treatments/{id} 🛡 ADMIN, AGRONOMIST
Same body as POST. `stats` is not changed: it is derived from interactions.

### DELETE /api/treatments/{id} 🛡 ADMIN
Deletes the treatment and its interactions. Response `204`.

---

## 4. Interactions

### POST /api/interactions 🔒
```json
{ "treatmentId": "6727...", "type": "RATING", "fieldId": "6727...", "detectionId": "6727...",
  "rating": 5, "effectiveness": 90 }
```
| Field | Required | Description |
|---|---|---|
| `treatmentId` | yes | the treatment |
| `type` | yes | `VIEW`, `SAVE`, `APPLY`, `RATING` |
| `rating` | for `RATING` | 1–5 |
| `effectiveness` | no | % of weeds killed, 0–100 |
| `fieldId`, `detectionId` | no | context: which field and which detection |

Side effects: `VIEW` and `APPLY` increment `stats.views` / `stats.applications`; `RATING` recomputes
`stats.avgRating`. Rating the same treatment again replaces the previous rating.

---

## 5. Fields

| Method | URL | Description |
|---|---|---|
| GET | `/api/fields` 🔒 | my fields |
| POST | `/api/fields` 🔒 | add a field |
| DELETE | `/api/fields/{id}` 🔒 | delete my field |
| GET | `/api/fields/{id}/detections?limit=10` 🔒 | latest detections on a field |

```json
{ "name": "Field #4", "crop": "wheat", "areaHa": 150, "soilType": "chernozem",
  "longitude": 71.43, "latitude": 51.13 }
```

---

## 6. CV model detections

### POST /api/detections 🔒
Stores a detection result (called by the CV model service).
```json
{
  "fieldId": "6727...",
  "imageUrl": "https://storage.example/img_0412.jpg",
  "modelVersion": "yolov8-weeds-v1.2",
  "weeds": [
    { "weedId": "avena_fatua", "confidence": 0.91, "density": 14.5 },
    { "weedId": "cirsium_arvense", "confidence": 0.78, "density": 3.2 }
  ]
}
```

### GET /api/detections/{id}/matching-treatments 🔒
Treatments that target at least one detected weed and are allowed for the field's crop, sorted by rating.
A baseline lookup over the multikey indexes; the recommendation algorithm comes in the Final Project.

---

## 7. Weed species

### GET /api/weeds 🔓
```json
[ { "id": "avena_fatua", "name": "Wild oat", "latinName": "Avena fatua",
    "type": "monocot", "lifecycle": "annual" } ]
```
Ids match the CV model classes.

---

## Demo walkthrough

1. `POST /api/auth/register`: register a new farmer.
2. `POST /api/auth/login` as `farmer@weedrec.kz`, then Authorize in Swagger.
3. `GET /api/users/me`, then `PUT /api/users/me/profile`.
4. `GET /api/treatments?q=wild oat`: full-text search.
5. `GET /api/treatments?weed=cirsium_arvense&crop=rapeseed&sort=rating`: attribute search.
6. `GET /api/fields` → `GET /api/fields/{id}/detections` → `GET /api/detections/{id}/matching-treatments`.
7. `POST /api/interactions` with `RATING` → `GET /api/treatments/{id}` (`stats` changed).
8. `GET /api/users/me/history`.
9. Log in as `agronomist@weedrec.kz` → `POST`, `PUT /api/treatments`; the same POST as `farmer` → `403`.
10. In mongo-express or Compass: collections, documents, indexes (`getIndexes`, `explain`).
