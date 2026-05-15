from __future__ import annotations

import json
import math
import os
import subprocess
import sys
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

    _shared_lock = threading.Lock()
    _shared_cache: Dict[tuple[str, str], tuple[object | None, List[str], str, Optional[str]]] = {}

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._loaded = False
        self._model = None
        self._feature_columns: List[str] = []
        self._model_version = "shadow_v0"
        self._error: Optional[str] = None

    def _smoke_load_model(self, model_path: str) -> Optional[str]:
        safe_load = os.getenv("PCOSINA_ML_SAFE_MODEL_LOAD", "true").strip().lower()
        if safe_load in ("0", "false", "no", "off"):
            return None
        code = (
            "import sys\n"
            "import lightgbm as lgb\n"
            "booster = lgb.Booster(model_file=sys.argv[1])\n"
            "print(len(booster.feature_name()))\n"
        )
        try:
            proc = subprocess.run(
                [sys.executable, "-c", code, model_path],
                check=False,
                capture_output=True,
                text=True,
                timeout=float(os.getenv("PCOSINA_ML_SAFE_MODEL_LOAD_TIMEOUT_SECONDS", "8")),
            )
        except Exception as exc:
            return f"model smoke load failed: {exc}"
        if proc.returncode != 0:
            detail = (proc.stderr or proc.stdout or "").strip().splitlines()
            suffix = f": {detail[-1][:180]}" if detail else ""
            return f"model smoke load failed with exit code {proc.returncode}{suffix}"
        return None

    def _load_once(self) -> None:
        if self._loaded:
            return
        with self._lock:
            if self._loaded:
                return
            model_path = os.getenv("PCOSINA_ML_MODEL_PATH", "").strip()
            metrics_path = os.getenv("PCOSINA_ML_METRICS_PATH", "").strip()
            cache_key = (model_path, metrics_path)
            with self.__class__._shared_lock:
                cached = self.__class__._shared_cache.get(cache_key)
            if cached is None:
                booster = None
                feature_cols: List[str] = []
                model_version = "shadow_v0"
                error: Optional[str] = None
                if not model_path:
                    error = "PCOSINA_ML_MODEL_PATH not set"
                else:
                    try:
                        import lightgbm as lgb
                    except Exception as exc:
                        error = f"lightgbm unavailable: {exc}"
                    else:
                        smoke_error = self._smoke_load_model(model_path)
                        if smoke_error:
                            error = smoke_error
                        else:
                            try:
                                booster = lgb.Booster(model_file=model_path)
                            except Exception as exc:
                                error = f"failed to load model: {exc}"
                            else:
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
                cached = (booster, feature_cols, model_version, error)
                with self.__class__._shared_lock:
                    self.__class__._shared_cache[cache_key] = cached
            booster, feature_cols, model_version, error = cached
            self._model = booster
            self._feature_columns = list(feature_cols)
            self._model_version = model_version
            self._error = error
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
            try:
                pred = self._model.predict([vector], num_threads=1)
            except TypeError:
                pred = self._model.predict([vector])
            if pred is None or len(pred) == 0:
                return None
            value = float(pred[0])
            if not math.isfinite(value):
                return None
            return value
        except Exception:
            return None

    def score_many(self, features_list: List[Dict[str, float]]) -> List[Optional[float]]:
        self._load_once()
        if self._model is None or not self._feature_columns:
            return [None for _ in features_list]
        if not features_list:
            return []
        matrix = [
            [float(features.get(col, 0.0)) for col in self._feature_columns]
            for features in features_list
        ]
        try:
            try:
                preds = self._model.predict(matrix, num_threads=1)
            except TypeError:
                preds = self._model.predict(matrix)
            if preds is None:
                return [None for _ in features_list]
            out: List[Optional[float]] = []
            for pred in preds:
                try:
                    value = float(pred)
                except Exception:
                    out.append(None)
                    continue
                out.append(value if math.isfinite(value) else None)
            return out
        except Exception:
            return [None for _ in features_list]


_RANKER = Stage1MLRanker()


def get_stage1_ranker() -> Stage1MLRanker:
    return _RANKER
