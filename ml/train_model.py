import pandas as pd
import joblib
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score
import os

def train_model():
    # Load dataset
    dataset_path = "ml/behavioral_dataset.csv"
    if not os.path.exists(dataset_path):
        print(f"Error: Dataset not found at {dataset_path}")
        return

    df = pd.read_csv(dataset_path)
    
    # Features and Target
    X = df.drop(columns=["category"])
    y = df["category"]
    
    # Split data
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    
    # Initialize and train Random Forest
    print("Training Random Forest Classifier...")
    model = RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42)
    model.fit(X_train, y_train)
    
    # Evaluate
    y_pred = model.predict(X_test)
    accuracy = accuracy_score(y_test, y_pred)
    print(f"Model Accuracy: {accuracy:.4f}")
    print("\nClassification Report:")
    print(classification_report(y_test, y_pred))
    
    # Save the model
    model_path = "wellness_wave_backend/stress_model.pkl"
    # Ensure backend directory exists
    os.makedirs(os.path.dirname(model_path), exist_ok=True)
    
    joblib.dump(model, model_path)
    print(f"Model saved to {model_path}")
    
    # Save features list for reference during inference
    features = X.columns.tolist()
    joblib.dump(features, "wellness_wave_backend/model_features.pkl")
    print("Feature list saved to wellness_wave_backend/model_features.pkl")

if __name__ == "__main__":
    train_model()
