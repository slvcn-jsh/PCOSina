from __future__ import annotations

import json
import os
import threading
from dataclasses import dataclass
from typing import Dict, List, Optional

@dataclass
class RankerState:
    ready: bool
    model_version: str
    feature_columns: List[str]
    error: Optional[str] = None


class Stage1MLRanker:
    """Best-effort model loader for shadow/canary ranking support."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._loaded = False
        self._model = None
        self._feature_columns: List[str] = []
        self._model_version = "shadow_v0"
        self._error: Optional[str] = None

    def _load_once(self) -> None:
        if self._loaded:
            return
        with self._lock:
            if self._loaded:
                return
            model_path = os.getenv("PCOSINA_ML_MODEL_PATH", "").strip()
            metrics_path = os.getenv("PCOSINA_ML_METRICS_PATH", "").strip()
            if not model_path:
                self._error = "PCOSINA_ML_MODEL_PATH not set"
                self._loaded = True
                return
            try:
                import lightgbm as lgb
            except Exception as exc:
                self._error = f"lightgbm unavailable: {exc}"
                self._loaded = True
                return
            try:
                booster = lgb.Booster(model_file=model_path)
            except Exception as exc:
                self._error = f"failed to load model: {exc}"
                self._loaded = True
                return

            feature_cols: List[str] = []
            model_version = "lightgbm_v1_unknown"
            if metrics_path and os.path.exists(metrics_path):
                try:
                    payload = json.loads(open(metrics_path, "r", encoding="utf-8").read())
                    cols = payload.get("feature_columns") or payload.get("featureColumns") or []
                    if isinstance(cols, list):
                        feature_cols = [str(c) for c in cols if str(c).strip()]
                    model_version = str(payload.get("model_name") or payload.get("model") or model_version)
                except Exception:
                    pass
            if not feature_cols:
                try:
                    feature_cols = list(booster.feature_name())
                except Exception:
                    feature_cols = []
            self._model = booster
            self._feature_columns = feature_cols
            self._model_version = model_version
            self._error = None
            self._loaded = True

    def state(self) -> RankerState:
        self._load_once()
        return RankerState(
            ready=self._model is not None and len(self._feature_columns) > 0,
            model_version=self._model_version,
            feature_columns=list(self._feature_columns),
            error=self._error,
        )

    def score(self, features: Dict[str, float]) -> Optional[float]:
        self._load_once()
        if self._model is None or not self._feature_columns:
            return None
        vector = [float(features.get(col, 0.0)) for col in self._feature_columns]
        try:
            pred = self._model.predict([vector])
            if pred is None or len(pred) == 0:
                return None
            return float(pred[0])
        except Exception:
            return None


_RANKER = Stage1MLRanker()


def get_stage1_ranker() -> Stage1MLRanker:
    return _RANKER
