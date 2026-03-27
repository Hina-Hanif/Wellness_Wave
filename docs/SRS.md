# Software Requirements Specification (SRS)

## 1. Technical Requirements

### 1.1 Behavioral Monitoring (High Granularity)
- **Typing Statistics:** Use an Accessibility Service to monitor typing rhythm (characters per minute, backspace frequency) without capturing character values.
- **Scroll Analysis:** Calculate scroll speed and directional shifts (pixel delta / time).
- **App Switching:** Log time spent in the foreground for each app and the transition count between apps.
- **Notifications:** Measure the time between a notification arrival and user action (click/dismiss).

### 1.2 Multi-State Classification
- **Target States:** Stress, Anxiety, Burnout, Phone Addiction.
- **Model:** Scikit-learn Random Forest Classifier.
- **Inference:** Server-side prediction based on daily aggregated behavioral vectors.

## 2. Functional Requirements
- **FR-1:** Accessibility Permission request for typing and scrolling data.
- **FR-2:** Background data aggregation (1-hour windows).
- **FR-3:** Daily mental health self-assessment form.
- **FR-4:** API endpoint for multi-state JSON payload submission.
- **FR-5:** Dashboard showing state-specific alerts and advice.

## 3. Non-Functional Requirements
- **Privacy:** Absolute zero-content policy. No keylogging of text, only metadata of interaction.
- **Battery:** Use WorkManager for batch syncing to minimize radio usage.
- **Latency:** Inference response within 500ms.
