from fastapi import APIRouter, HTTPException
from wellness_wave_backend.models import DailyMetrics
from wellness_wave_backend.database import store_daily_metrics

router = APIRouter()

@router.post("/daily-data")
async def submit_daily_data(metrics: DailyMetrics):
    success = store_daily_metrics(metrics.dict())
    if success:
        return {
            "status": "success",
            "message": "Daily metrics stored securely.",
            "data_id": f"{metrics.user_id}_{metrics.date}"
        }
    else:
        # If database is not configured, we still return success for testing integration
        # but log a warning (simulated here)
        return {
            "status": "partial_success",
            "message": "Daily metrics received but not stored (Database not configured).",
            "data_id": f"{metrics.user_id}_{metrics.date}"
        }
