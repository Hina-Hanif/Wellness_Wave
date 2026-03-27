# System Architecture

## Expanded Data Flow
The system now monitors high-frequency behavioral traits via the Accessibility Service and Usage Stats API.

```mermaid
sequenceDiagram
    participant App as Android App
    participant Acc as Accessibility Service
    participant API as FastAPI Backend
    participant RF as Random Forest Model
    participant DB as Firestore
    
    App->>Acc: Monitor Typing Rhythm & Scroll Erraticness
    App->>App: Collect App Switches & Notification Latency
    App->>API: POST /mental-state-data
    API->>DB: Store Metrics
    API->>RF: Predict (Stress/Anxiety/Burnout/Addiction)
    RF-->>API: Predicted State + Confidence
    API-->>App: Results + Actionable Feedback
```

## ML Components
- **Input Features (Feature Vector):**
    - `typing_cps` (characters per second)
    - `backspace_rate`
    - `scroll_velocity_std_dev`
    - `notification_response_sec`
    - `app_switch_count_per_hour`
- **Output:** Multi-class label for the detected mental state.
