# 🌊 Wellness Wave - AI Mental Wellness Backend

Wellness Wave is an intelligent FastAPI backend that powers the Wellness Wave Android application. It processes smartphone usage patterns (screen time, app switches, typing patterns, etc.) using an advanced Machine Learning ensemble model to predict user stress, burnout risk, and anxiety levels in real-time.

## 🚀 Features

- **Real-time ML Predictions:** Loads pre-trained models via `joblib` to securely process incoming smartphone usage data and classify mental wellness metrics.
- **Firebase Integration:** Securely stores and retrieves user daily metrics and historical data via Firebase Firestore.
- **Modular API Architecture:** Clean separation of concerns with dedicated routers for predictions, database access, and health checks.
- **Secure & Fast:** Built on top of FastAPI, ensuring high performance, automatic documentation (Swagger UI), and safe data parsing using Pydantic.

---

## 📂 Project Structure

```text
wellness_wave_backend/
├── api/
│   └── prediction.py         # Advanced ML endpoint logic handling data transformation & prediction
├── database.py               # Firebase Firestore initialization and data retrieval logic
├── generate_and_train.py     # Script used to test, generate mock data, and train the ML models
├── main.py                   # FastAPI entry point, application configuration, and base routers
├── models.py                 # Pydantic data schemas for incoming request validation
├── requirements.txt          # Python dependencies (FastAPI, scikit-learn, pandas, etc.)
├── Procfile                  # Deployment configuration profile (e.g., for Heroku/Render)
├── render.yaml               # Infrastructure-as-code configuration for Render deployment
├── stress_model.pkl          # Serialized scikit-learn ensemble model
├── preprocessor.pkl          # Serialized data scaler/transformer
├── label_encoder.pkl         # Serialized categorical label encoder
└── model_meta.json           # JSON definition of the ML feature pipeline
```

---

## 🛠️ Installation & Setup

### 1. Prerequisites
Ensure you have **Python 3.9+** installed on your system.
You will also need a Firebase `serviceAccountKey.json` from your Firebase Console for database access.

### 2. Clone and Setup Environment
Navigate into your backend directory and install the required dependencies:

```bash
cd wellness_wave_backend
pip install -r requirements.txt
```

### 3. Setup Firebase
Place your `serviceAccountKey.json` directly into the root `wellness_wave_backend/` directory. The application will automatically detect it and authenticate with Firestore. If the file is missing, the backend will safely fallback to a "mock mode" so you can still test it locally.

---

## 🏃‍♂️ Running the Server

Start the local development server using `uvicorn`:

```bash
python -m uvicorn main:app --reload
```

The API will be available at: **http://127.0.0.1:8000**

---

## 📖 API Endpoints

FastAPI automatically generates an interactive documentation page for you.
Once the server is running, visit: **[http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)** to test the endpoints visually!

### 🟢 `GET /health`
- **Description:** Basic health check to confirm the server is running.
- **Response:** `{"status": "active", "message": "Wellness Wave API is running smoothly."}`

### 📊 `POST /daily-data`
- **Description:** Submit daily smartphone usage metrics from the Android app to Firestore.
- **Body:** Standard `UsageMetrics` JSON.
- **Response:** Success confirmation or Firebase error.

### 🧠 `GET /prediction`
- **Description:** The core ML pipeline endpoint. Fetches the latest database metrics for the user, transforms them using `preprocessor.pkl`, and passes them to `stress_model.pkl`.
- **Query Params:** `?user_id=...`
- **Response:**
  ```json
  {
    "user_id": "test_user",
    "stress_level": "Medium",
    "confidence_score": 0.85,
    "real_time_feedback": "Prediction generated successfully."
  }
  ```

### 📈 `GET /history`
- **Description:** Retrieves historical stress & screen time data for rendering charts on the Android dashboard.

---

## 🧠 Machine Learning Pipeline

The machine learning models were developed using `scikit-learn` and `joblib`, integrated directly into the `api/prediction.py` router:
1. **Feature Extraction:** Data fetched from Firebase is mapped strictly to the exact features array saved in `model_meta.json`.
2. **Preprocessing:** Missing values are imputed and standardized exactly as they were during training via `preprocessor.pkl`.
3. **Prediction:** The finalized feature array is fed into `stress_model.pkl` yielding a classification and a confidence score.
4. **Decoding:** The raw numerical output is turned back into a human-readable string (e.g., "High", "Low") via `label_encoder.pkl`.

---

## 🚀 Deployment

The backend is fully prepared for cloud deployment out-of-the box:
- **Render:** Uses `render.yaml` for zero-config deployments. Just connect your GitHub repo to Render.
- **Heroku:** Uses the default `Procfile`.
*Make sure to inject your Firebase credentials via Environment Variables securely when deploying to production!*
