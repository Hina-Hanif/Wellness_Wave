# REST API Specification

**Base URL:** `http://localhost:8000`

---

## 1. POST /daily-data
Submit raw usage metrics and user mood.

**Request Payload:**
```json
{
  "user_id": "uuid-string",
  "date": "2024-03-24",
  "screen_time": 320,
  "unlock_count": 45,
  "social_time": 120,
  "productivity_time": 60,
  "night_usage": 45,
  "session_count": 12,
  "scrolling_speed_avg": 5.4,
  "usage_consistency_shift": 1.2,
  "mood_score": 7
}
```

**Success Response (201):**
```json
{
  "status": "success",
  "message": "Data stored",
  "predicted_score": 7.2
}
```

---

## 2. GET /prediction
Get the latest stress prediction for a user.

**Parameters:** `user_id` (string)

**Success Response (200):**
```json
{
  "user_id": "uuid",
  "stress_category": "Medium",
  "feedback": "You've been scrolling a lot. Take a walk!",
  "confidence": 0.85
}
```

---

## 3. GET /health
System health check.
