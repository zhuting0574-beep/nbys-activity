from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter
from reportlab.lib.colors import black, white
from reportlab.lib.pagesizes import A3, A4
from reportlab.lib.units import mm
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas


OUTPUT_DIR = Path(__file__).resolve().parent
REFERENCE_HUMAN_TARGET = Path("/Users/zhuting/Downloads/lasera靶纸/b3-150.png")


def marker(pdf: canvas.Canvas, x_mm: float, y_mm: float, size_mm: float, label: str) -> None:
    """Draw the proprietary black/white/black registration marker."""
    x = x_mm * mm
    y = y_mm * mm
    size = size_mm * mm

    pdf.setFillColor(black)
    pdf.rect(x - size / 2, y - size / 2, size, size, fill=1, stroke=0)
    pdf.setFillColor(white)
    pdf.rect(x - size * 0.34, y - size * 0.34, size * 0.68, size * 0.68, fill=1, stroke=0)
    pdf.setFillColor(black)
    pdf.rect(x - size * 0.15, y - size * 0.15, size * 0.30, size * 0.30, fill=1, stroke=0)

    pdf.setFont("Helvetica-Bold", max(5, size_mm * 0.32))
    pdf.drawCentredString(x, y - size * 0.72, label)


def draw_target(filename: str, page_size: tuple[float, float], marker_size_mm: float) -> None:
    width, height = page_size
    width_mm = width / mm
    height_mm = height / mm
    pdf = canvas.Canvas(str(OUTPUT_DIR / filename), pagesize=page_size, pageCompression=1)
    pdf.setTitle("NBYS Laser Precision Target")
    pdf.setAuthor("NBYS")

    margin = marker_size_mm * 0.95
    corners = (
        (margin, height_mm - margin, "TL"),
        (width_mm - margin, height_mm - margin, "TR"),
        (width_mm - margin, margin, "BR"),
        (margin, margin, "BL"),
    )
    for x, y, label in corners:
        marker(pdf, x, y, marker_size_mm, label)

    center_x = width / 2
    center_y = height / 2
    max_radius = min(width_mm * 0.39, height_mm * 0.34) * mm
    ring_count = 10

    pdf.setLineWidth(0.45)
    for score in range(1, ring_count + 1):
        radius = max_radius * (ring_count + 1 - score) / ring_count
        if score >= 8:
            pdf.setFillColor(black)
            pdf.circle(center_x, center_y, radius, fill=1, stroke=0)
        else:
            pdf.setFillColor(white)
            pdf.setStrokeColor(black)
            pdf.circle(center_x, center_y, radius, fill=0, stroke=1)

    # Restore the two inner white scoring lines over the black center.
    pdf.setStrokeColor(white)
    for score in (8, 9, 10):
        radius = max_radius * (ring_count + 1 - score) / ring_count
        pdf.circle(center_x, center_y, radius, fill=0, stroke=1)

    pdf.setFont("Helvetica-Bold", 7)
    for score in range(1, ring_count + 1):
        radius = max_radius * (ring_count + 0.5 - score) / ring_count
        pdf.setFillColor(white if score >= 8 else black)
        pdf.drawCentredString(center_x, center_y + radius - 2.3, str(score))
        pdf.drawCentredString(center_x, center_y - radius - 2.3, str(score))

    pdf.setFillColor(black)
    pdf.setFont("Helvetica-Bold", 12)
    pdf.drawCentredString(center_x, height - 12 * mm, "NBYS LASER PRECISION")
    pdf.setFont("Helvetica", 7)
    pdf.drawCentredString(center_x, 8 * mm, "CALIBRATION AREA: TL / TR / BR / BL")
    pdf.showPage()
    pdf.save()


def draw_human_target(filename: str, page_size: tuple[float, float], marker_size_mm: float) -> None:
    width, height = page_size
    width_mm = width / mm
    height_mm = height / mm
    pdf = canvas.Canvas(str(OUTPUT_DIR / filename), pagesize=page_size, pageCompression=1)
    pdf.setTitle("NBYS Human Silhouette Target")
    pdf.setAuthor("NBYS")

    margin = marker_size_mm * 0.95
    corners = (
        (margin, height_mm - margin, "TL"),
        (width_mm - margin, height_mm - margin, "TR"),
        (width_mm - margin, margin, "BR"),
        (margin, margin, "BL"),
    )
    for x, y, label in corners:
        marker(pdf, x, y, marker_size_mm, label)

    scale = min(width_mm / 210.0, height_mm / 297.0)
    cx = width / 2
    head_top = height - 55 * scale * mm
    head_bottom = head_top - 29 * scale * mm
    body_top = head_bottom - 19 * scale * mm
    body_bottom = 43 * scale * mm

    # Angular upper-body silhouette: head, shoulders, torso and lower body zones.
    pdf.setFillColor(black)
    pdf.rect(cx - 17 * scale * mm, head_bottom, 34 * scale * mm, 29 * scale * mm, fill=1, stroke=0)
    pdf.rect(cx - 10 * scale * mm, body_top, 20 * scale * mm, 20 * scale * mm, fill=1, stroke=0)
    body = pdf.beginPath()
    points = [
        (-36, body_top / mm / scale), (-74, body_top / mm / scale - 14),
        (-92, body_top / mm / scale - 31), (-92, body_bottom / mm / scale + 24),
        (-76, body_bottom / mm / scale), (-43, body_bottom / mm / scale),
        (43, body_bottom / mm / scale), (76, body_bottom / mm / scale),
        (92, body_bottom / mm / scale + 24), (92, body_top / mm / scale - 31),
        (74, body_top / mm / scale - 14), (36, body_top / mm / scale),
    ]
    body.moveTo(cx + points[0][0] * scale * mm, points[0][1] * scale * mm)
    for x, y in points[1:]:
        body.lineTo(cx + x * scale * mm, y * scale * mm)
    body.close()
    pdf.drawPath(body, fill=1, stroke=0)

    pdf.setStrokeColor(white)
    pdf.setLineWidth(max(0.7, 1.2 * scale))
    pdf.roundRect(cx - 11 * scale * mm, head_bottom + 7 * scale * mm, 22 * scale * mm, 14 * scale * mm, 5 * scale * mm, fill=0, stroke=1)
    pdf.setFont("Helvetica-Bold", max(7, 9 * scale))
    pdf.setFillColor(white)
    pdf.drawCentredString(cx, head_bottom + 12 * scale * mm, "A")

    inner = pdf.beginPath()
    inner.moveTo(cx - 37 * scale * mm, body_top - 30 * scale * mm)
    inner.lineTo(cx - 51 * scale * mm, body_top - 47 * scale * mm)
    inner.lineTo(cx - 38 * scale * mm, body_bottom + 43 * scale * mm)
    inner.lineTo(cx + 38 * scale * mm, body_bottom + 43 * scale * mm)
    inner.lineTo(cx + 51 * scale * mm, body_top - 47 * scale * mm)
    inner.lineTo(cx + 37 * scale * mm, body_top - 30 * scale * mm)
    inner.close()
    pdf.drawPath(inner, fill=0, stroke=1)
    pdf.roundRect(cx - 25 * scale * mm, body_top - 14 * scale * mm, 50 * scale * mm, 31 * scale * mm, 7 * scale * mm, fill=0, stroke=1)
    pdf.setFont("Helvetica-Bold", max(7, 9 * scale))
    pdf.drawCentredString(cx, body_top - 1 * scale * mm, "B")
    pdf.drawCentredString(cx, body_bottom + 55 * scale * mm, "A")
    pdf.drawCentredString(cx, body_bottom + 12 * scale * mm, "C")
    pdf.setFillColor(white)
    pdf.drawCentredString(cx, body_bottom + 6 * scale * mm, "D")

    pdf.setFillColor(black)
    pdf.setFont("Helvetica-Bold", 12 * scale)
    pdf.drawCentredString(cx, height - 11 * mm, "NBYS HUMAN SILHOUETTE")
    pdf.setFont("Helvetica", max(6, 7 * scale))
    pdf.drawCentredString(cx, 8 * mm, "CALIBRATION AREA: TL / TR / BR / BL")
    pdf.showPage()
    pdf.save()


def extract_reference_human_target() -> Image.Image:
    """Crop only the supplied image's left target panel; keep its original artwork."""
    source = Image.open(REFERENCE_HUMAN_TARGET).convert("RGB")
    # The supplied 1619x1241 sheet contains the target on the left and product
    # instructions on the right. Keep the complete left artwork and its labels.
    target = source.crop((42, 52, 790, 1198))
    cleanup = ImageDraw.Draw(target)
    # Remove the source sheet's right-side compliance copy and bottom caption,
    # while preserving the supplied silhouette, title and scoring zones.
    cleanup.rectangle((505, 0, target.width, 225), fill="white")
    cleanup.rectangle((0, 1127, target.width, target.height), fill="white")
    # The source is a halftone print preview. A median pass removes the dots
    # from both the black silhouette and the white background while retaining
    # the supplied outlines and zone letters.
    target = target.filter(ImageFilter.MedianFilter(size=5))
    # Flatten the halftone to a clean black/white print while retaining the
    # supplied scoring outlines and zone letters.
    return target.filter(ImageFilter.MedianFilter(size=5)).convert("L").point(
        lambda value: 255 if value >= 110 else 0
    ).convert("RGB")


def draw_reference_human_target(filename: str, page_size: tuple[float, float], marker_size_mm: float) -> None:
    width, height = page_size
    target = extract_reference_human_target()
    target_path = OUTPUT_DIR / "nbys-human-silhouette-reference-crop.png"
    target.save(target_path, optimize=True)

    pdf = canvas.Canvas(str(OUTPUT_DIR / filename), pagesize=page_size, pageCompression=1)
    pdf.setTitle("NBYS Human Silhouette Reference Target")
    pdf.setAuthor("NBYS")

    # Fit the complete supplied left panel inside a clean printable area.
    max_width = width - 31 * mm
    max_height = height - 27 * mm
    ratio = min(max_width / target.width, max_height / target.height)
    draw_width = target.width * ratio
    draw_height = target.height * ratio
    draw_x = (width - draw_width) / 2
    draw_y = (height - draw_height) / 2
    pdf.drawImage(ImageReader(target), draw_x, draw_y, draw_width, draw_height, preserveAspectRatio=True, mask="auto")

    width_mm = width / mm
    height_mm = height / mm
    margin = marker_size_mm * 0.95
    for x, y, label in (
        (margin, height_mm - margin, "TL"),
        (width_mm - margin, height_mm - margin, "TR"),
        (width_mm - margin, margin, "BR"),
        (margin, margin, "BL"),
    ):
        marker(pdf, x, y, marker_size_mm, label)
    pdf.showPage()
    pdf.save()


if __name__ == "__main__":
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    draw_target("nbys-laser-precision-a4.pdf", A4, 16)
    draw_target("nbys-laser-precision-a3.pdf", A3, 21)
    draw_reference_human_target("nbys-human-silhouette-a4.pdf", A4, 16)
    draw_reference_human_target("nbys-human-silhouette-a3.pdf", A3, 21)
