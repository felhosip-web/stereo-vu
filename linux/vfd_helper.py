"""
Pure helper module for VFD (Klasszikus Cián / Videoton) display theme on Linux.
Standardizes canonical palettes, 4-subsegment geometry, progression order,
and peak mode color mapping across platforms.
"""

# Canonical VFD Palette (Videoton amplifier style)
COLOR_ACTIVE_HEX = "#00D4E8"
COLOR_INACTIVE_HEX = "#001F26"
COLOR_BACKGROUND_HEX = "#0A1215"
COLOR_BORDER_HEX = "#003840"
COLOR_GROOVE_HEX = "#040B0D"
COLOR_LABEL_HEX = "#66C0CC"
COLOR_SCALE_TICK_HEX = "#006A75"
COLOR_SCALE_TEXT_HEX = "#4DAEB8"
COLOR_WIRE_RGBA = (255, 255, 255, 45)

COLOR_ACTIVE_RGB = (0x00, 0xD4, 0xE8)
COLOR_INACTIVE_RGB = (0x00, 0x1F, 0x26)
COLOR_BACKGROUND_RGB = (0x0A, 0x12, 0x15)

# Peak color mappings
COLOR_PEAK_WHITE_RGB = (255, 255, 255)
COLOR_PEAK_WHITE_HEX = "#FFFFFF"

COLOR_PEAK_CYAN_RGB = (0x00, 0xD4, 0xE8)
COLOR_PEAK_CYAN_HEX = "#00D4E8"

COLOR_PEAK_AMBER_RGB = (255, 204, 0)
COLOR_PEAK_AMBER_HEX = "#FFCC00"

COLOR_PEAK_FIXED_CYAN_RGB = (0x00, 0xE5, 0xFF)
COLOR_PEAK_FIXED_CYAN_HEX = "#00E5FF"

SUBSEGMENTS_PER_CELL = 4


def get_peak_color_rgb(peak_mode: int):
    """
    Returns (R, G, B) tuple for the given peak mode in VFD theme.
    0: White
    1: Active Cyan (canonical #00D4E8)
    2: Amber (#FFCC00)
    3: Fixed Cyan (#00E5FF)
    """
    if peak_mode == 1:
        return COLOR_PEAK_CYAN_RGB
    elif peak_mode == 2:
        return COLOR_PEAK_AMBER_RGB
    elif peak_mode == 3:
        return COLOR_PEAK_FIXED_CYAN_RGB
    else:
        return COLOR_PEAK_WHITE_RGB


def get_peak_color_hex(peak_mode: int):
    """
    Returns hex string for the given peak mode in VFD theme.
    """
    if peak_mode == 1:
        return COLOR_PEAK_CYAN_HEX
    elif peak_mode == 2:
        return COLOR_PEAK_AMBER_HEX
    elif peak_mode == 3:
        return COLOR_PEAK_FIXED_CYAN_HEX
    else:
        return COLOR_PEAK_WHITE_HEX


def calculate_subsegment_rect(is_horizontal, cell_left, cell_top, cell_width, cell_height, sub_index, sub_gap=2.0):
    """
    Calculates (left, top, width, height) of a subsegment within a logical cell.

    Established Videoton VFD subsegment geometry:
    - Vertical meter:
      Four narrow vertical subsegments side-by-side (divide cell along X axis).
      Each subsegment occupies approximately one quarter of cell width and full cell height.
      Progression within cell: left-to-right (sub_index 0..3).
      sub_left = cell_left + sub_index * (sub_w + sub_gap)
      sub_top = cell_top
      sub_w = (cell_width - 3 * sub_gap) / 4
      sub_h = cell_height

    - Horizontal meter:
      Four thin horizontal subsegments stacked vertically (divide cell along Y axis).
      Each subsegment occupies approximately one quarter of cell height and full cell width.
      Progression within cell: bottom-to-top (sub_index 0..3, 0 is bottom, 3 is top).
      sub_top = cell_top + (3 - sub_index) * (sub_h + sub_gap)
      sub_left = cell_left
      sub_w = cell_width
      sub_h = (cell_height - 3 * sub_gap) / 4

    Guarantees non-negative width and height even at small dimensions.
    """
    clamped_sub_idx = max(0, min(SUBSEGMENTS_PER_CELL - 1, sub_index))
    if is_horizontal:
        avail_h = max(0.0, float(cell_height) - (SUBSEGMENTS_PER_CELL - 1) * float(sub_gap))
        sub_h = avail_h / float(SUBSEGMENTS_PER_CELL)
        # 0 is bottom-most, 3 is top-most
        top = float(cell_top) + (SUBSEGMENTS_PER_CELL - 1 - clamped_sub_idx) * (sub_h + float(sub_gap))
        return (float(cell_left), top, max(0.0, float(cell_width)), sub_h)
    else:
        avail_w = max(0.0, float(cell_width) - (SUBSEGMENTS_PER_CELL - 1) * float(sub_gap))
        sub_w = avail_w / float(SUBSEGMENTS_PER_CELL)
        left = float(cell_left) + clamped_sub_idx * (sub_w + float(sub_gap))
        return (left, float(cell_top), sub_w, max(0.0, float(cell_height)))


def get_global_subsegment_index(cell_index: int, sub_index: int) -> int:
    """Calculates the global subsegment index."""
    return cell_index * SUBSEGMENTS_PER_CELL + sub_index


def calculate_active_subsegments(level: float, total_cells: int) -> int:
    """Calculates number of active subsegments for given level (0.0 to 1.0)."""
    total_subsegments = total_cells * SUBSEGMENTS_PER_CELL
    clamped_lvl = max(0.0, min(1.0, float(level)))
    return int(clamped_lvl * total_subsegments)


def calculate_peak_subsegment(peak: float, total_cells: int) -> int:
    """Calculates peak subsegment index clamped to [0, total_subsegments - 1]."""
    total_subsegments = total_cells * SUBSEGMENTS_PER_CELL
    if total_subsegments <= 0:
        return 0
    return max(0, min(total_subsegments - 1, int(float(peak) * total_subsegments)))
