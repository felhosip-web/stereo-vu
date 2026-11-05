import math
import numpy as np

# ITU-R BS.1770 compliant 4x oversampling polyphase FIR filter (12 taps per sub-phase)
# Phases 1, 2, 3 reconstruct inter-sample points at 1/4, 2/4, 3/4 sample offsets.
PHASE_1 = np.array([
    -0.00539, 0.01912, -0.04873, 0.11107, -0.27092, 0.88788,
     0.38075, -0.12932, 0.05778, -0.02476, 0.00905, -0.00216
], dtype=np.float64)

PHASE_2 = np.array([
    -0.00941, 0.03362, -0.08805, 0.22238, -0.63662, 0.63662,
    -0.22238, 0.08805, -0.03362, 0.00941, -0.00201, 0.00042
], dtype=np.float64)

PHASE_3 = np.array([
    -0.00216, 0.00905, -0.02476, 0.05778, -0.12932, 0.38075,
     0.88788, -0.27092, 0.11107, -0.04873, 0.01912, -0.00539
], dtype=np.float64)

TAPS = 12

def get_migrated_mode(settings) -> int:
    """
    Safely and idempotently migrates persisted ballistics modes for QSettings.
    In v1: 0=Digital Peak, 1=Analog VU, 2=PPM, 3=Custom.
    In v2: 0=Digital Peak, 1=Analog VU, 2=PPM, 3=True Peak, 4=Classic VU, 5=BBC PPM, 6=Nordic / DIN, 7=Custom.
    If mode was 3 and not migrated, it is migrated to 7 (Custom).
    """
    is_migrated = settings.value("ballistics_v2_migrated", False, type=bool)
    raw_mode = settings.value("mode", 0, type=int)

    if not is_migrated:
        migrated_mode = 7 if raw_mode == 3 else max(0, min(7, raw_mode))
        settings.setValue("mode", migrated_mode)
        settings.setValue("ballistics_v2_migrated", True)
        return migrated_mode

    return max(0, min(7, raw_mode))

class TruePeakDetector:
    """
    4x oversampling True Peak detector based on polyphase FIR interpolation.
    Reconstructs inter-sample signal peaks and maintains history across buffer boundaries.
    """
    def __init__(self):
        self.delay_l = np.zeros(TAPS, dtype=np.float64)
        self.delay_r = np.zeros(TAPS, dtype=np.float64)
        self.head = 0

    def reset(self):
        """Reset internal filter delay lines."""
        self.delay_l.fill(0.0)
        self.delay_r.fill(0.0)
        self.head = 0

    def process_sample(self, s_l: float, s_r: float):
        """
        Process a single stereo sample and return the true peak for (left, right).
        """
        self.delay_l[self.head] = s_l
        self.delay_r[self.head] = s_r

        max_l = abs(s_l)
        max_r = abs(s_r)

        # Build ordered history slice: newest to oldest tap
        # k=0 is most recent sample (head), k=11 is oldest
        idx = (self.head - np.arange(TAPS)) % TAPS
        hist_l = self.delay_l[idx]
        hist_r = self.delay_r[idx]

        p1_l = abs(np.dot(hist_l, PHASE_1))
        p2_l = abs(np.dot(hist_l, PHASE_2))
        p3_l = abs(np.dot(hist_l, PHASE_3))

        p1_r = abs(np.dot(hist_r, PHASE_1))
        p2_r = abs(np.dot(hist_r, PHASE_2))
        p3_r = abs(np.dot(hist_r, PHASE_3))

        max_l = max(max_l, p1_l, p2_l, p3_l)
        max_r = max(max_r, p1_r, p2_r, p3_r)

        self.head = (self.head + 1) % TAPS
        return max_l, max_r

    def process_buffer(self, samples_l: np.ndarray, samples_r: np.ndarray):
        """
        Process arrays of samples for left and right channels, preserving history.
        Returns (true_peak_l, true_peak_r).
        """
        n = len(samples_l)
        if n == 0:
            return 0.0, 0.0

        peak_l = float(np.max(np.abs(samples_l)))
        peak_r = float(np.max(np.abs(samples_r)))

        for i in range(n):
            s_peak_l, s_peak_r = self.process_sample(float(samples_l[i]), float(samples_r[i]))
            if s_peak_l > peak_l:
                peak_l = s_peak_l
            if s_peak_r > peak_r:
                peak_r = s_peak_r

        return peak_l, peak_r
