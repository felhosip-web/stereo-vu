import math
import numpy as np

# 4x oversampling polyphase FIR reconstruction filter (12 taps per sub-phase, 48 taps total)
# Designed via windowed sinc interpolation with unity DC gain per sub-phase.
# Tap k=0 corresponds to the newest sample, and k=11 to the oldest sample in history.
# Phase 0 is the original sample (delay of 5 samples).
# Phase 1 reconstructs at offset +0.25 (peak at tap 5).
# Phase 2 reconstructs at offset +0.50 (symmetric peak between tap 5 and tap 6).
# Phase 3 reconstructs at offset +0.75 (peak at tap 6).
PHASE_1 = np.array([
    -0.00040574,  0.00452106, -0.01807915,  0.05255905, -0.14447287,  0.88354769,
     0.29451590, -0.10319490,  0.04300286, -0.01566860,  0.00404516, -0.00037046
], dtype=np.float64)

PHASE_2 = np.array([
    -0.00054430,  0.00600078, -0.02359308,  0.06647851, -0.16919841,  0.62085649,
     0.62085649, -0.16919841,  0.06647851, -0.02359308,  0.00600078, -0.00054430
], dtype=np.float64)

PHASE_3 = np.array([
    -0.00037046,  0.00404516, -0.01566860,  0.04300286, -0.10319490,  0.29451590,
     0.88354769, -0.14447287,  0.05255905, -0.01807915,  0.00452106, -0.00040574
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
        # Precomputed circular index lookup table of shape (12, 12)
        # Avoids per-sample np.arange() allocations in process_sample()
        self._idx_table = np.array([[(head - k) % TAPS for k in range(TAPS)] for head in range(TAPS)], dtype=np.intp)
        # 12x3 matrix combining all 3 reconstruction phases for single-pass dot product
        self._phases = np.column_stack([PHASE_1, PHASE_2, PHASE_3])

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

        # Retrieve ordered history (newest to oldest) via precomputed index
        idx = self._idx_table[self.head]
        hist_l = self.delay_l[idx]
        hist_r = self.delay_r[idx]

        p_l = np.abs(np.dot(hist_l, self._phases))
        p_r = np.abs(np.dot(hist_r, self._phases))

        max_l = max(abs(s_l), float(p_l[0]), float(p_l[1]), float(p_l[2]))
        max_r = max(abs(s_r), float(p_r[0]), float(p_r[1]), float(p_r[2]))

        self.head = (self.head + 1) % TAPS
        return max_l, max_r

    def process_buffer(self, samples_l: np.ndarray, samples_r: np.ndarray):
        """
        Process arrays of samples for left and right channels, preserving history.
        Vectorized via np.convolve across chunks for high performance in real-time callbacks.
        Returns (true_peak_l, true_peak_r).
        """
        n = len(samples_l)
        if n == 0:
            return 0.0, 0.0

        # Extract last TAPS - 1 samples in oldest-to-newest order for prepending
        hist_idx = [(self.head - (TAPS - 1) + k) % TAPS for k in range(TAPS - 1)]
        hist_l = self.delay_l[hist_idx]
        hist_r = self.delay_r[hist_idx]

        full_l = np.concatenate([hist_l, samples_l])
        full_r = np.concatenate([hist_r, samples_r])

        # Maintain exact internal delay buffer state continuity
        if n >= TAPS:
            self.delay_l[:] = full_l[-TAPS:]
            self.delay_r[:] = full_r[-TAPS:]
            self.head = 0
        else:
            for i in range(n):
                self.delay_l[self.head] = samples_l[i]
                self.delay_r[self.head] = samples_r[i]
                self.head = (self.head + 1) % TAPS

        peak_l = float(np.max(np.abs(samples_l)))
        peak_r = float(np.max(np.abs(samples_r)))

        for phase in (PHASE_1, PHASE_2, PHASE_3):
            c_l = np.abs(np.convolve(full_l, phase, mode='valid'))
            c_r = np.abs(np.convolve(full_r, phase, mode='valid'))
            m_l = float(np.max(c_l))
            m_r = float(np.max(c_r))
            if m_l > peak_l:
                peak_l = m_l
            if m_r > peak_r:
                peak_r = m_r

        return peak_l, peak_r
