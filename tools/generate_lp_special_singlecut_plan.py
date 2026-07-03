from pathlib import Path
from math import pow

from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A0
from reportlab.lib.units import mm


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf"
OUT.mkdir(parents=True, exist_ok=True)

SVG_PATH = OUT / "les-paul-special-single-cut-reference-plan.svg"
PDF_PATH = OUT / "les-paul-special-single-cut-reference-plan.pdf"

# Sheet and verified principal dimensions, in millimetres.
W, H = 841.0, 1189.0
CX = 420.5
NUT_Y = 155.0
SCALE = 628.65
BRIDGE_Y = NUT_Y + SCALE
BODY_TOP = 608.0
BODY_BOTTOM = 1046.0


def fret_y(n: int) -> float:
    return NUT_Y + SCALE * (1.0 - pow(2.0, -n / 12.0))


BODY_PATH = (
    f"M {CX-30} {BODY_TOP} "
    f"C {CX-55} 608, {CX-80} 620, {CX-95} 642 "
    f"C {CX-120} 650, {CX-150} 675, {CX-157} 715 "
    f"C {CX-165} 760, {CX-140} 790, {CX-150} 830 "
    f"C {CX-165} 890, {CX-145} 965, {CX-90} 1015 "
    f"C {CX-45} 1055, {CX+45} 1060, {CX+100} 1020 "
    f"C {CX+155} 980, {CX+172} 900, {CX+155} 835 "
    f"C {CX+145} 795, {CX+165} 760, {CX+155} 710 "
    f"C {CX+145} 670, {CX+115} 650, {CX+85} 655 "
    f"C {CX+60} 657, {CX+45} 675, {CX+37.5} 695 "
    f"C {CX+30} 716, {CX+15} 725, {CX+3.5} 718 "
    f"C {CX-10} 708, {CX} 688, {CX+25} 670 "
    f"C {CX+35} 660, {CX+30} 625, {CX+30} {BODY_TOP} Z"
)


def make_svg() -> str:
    fret_lines = []
    for n in range(1, 23):
        y = fret_y(n)
        half = 24.5 + (y - NUT_Y) / (fret_y(22) - NUT_Y) * 4.5
        fret_lines.append(
            f'<line x1="{CX-half:.2f}" y1="{y:.2f}" x2="{CX+half:.2f}" y2="{y:.2f}" class="fine"/>'
        )

    return f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="841mm" height="1189mm" viewBox="0 0 841 1189">
  <style>
    .outline {{ fill:none; stroke:#101820; stroke-width:0.7; }}
    .cut {{ fill:none; stroke:#c0392b; stroke-width:0.45; stroke-dasharray:3 2; }}
    .fine {{ fill:none; stroke:#34495e; stroke-width:0.28; }}
    .center {{ fill:none; stroke:#2874a6; stroke-width:0.3; stroke-dasharray:7 3 1 3; }}
    .dim {{ fill:none; stroke:#555; stroke-width:0.25; marker-start:url(#arr); marker-end:url(#arr); }}
    .txt {{ font-family:Arial,Helvetica,sans-serif; font-size:5px; fill:#17202a; }}
    .small {{ font-family:Arial,Helvetica,sans-serif; font-size:3.5px; fill:#263238; }}
    .title {{ font-family:Arial,Helvetica,sans-serif; font-size:10px; font-weight:bold; fill:#101820; }}
    .warn {{ font-family:Arial,Helvetica,sans-serif; font-size:4.2px; fill:#922b21; }}
  </style>
  <defs>
    <marker id="arr" markerWidth="5" markerHeight="5" refX="2.5" refY="2.5" orient="auto">
      <path d="M5,0 L0,2.5 L5,5" fill="none" stroke="#555" stroke-width="0.7"/>
    </marker>
  </defs>
  <rect x="12" y="12" width="817" height="1165" class="fine"/>
  <text x="28" y="38" class="title">LES PAUL SPECIAL SINGLE CUT - 1950s STYLE REFERENCE PLAN</text>
  <text x="28" y="49" class="txt">A0 / 1:1 / millimetres / construction-reference drawing</text>
  <text x="28" y="59" class="warn">NOT A GIBSON FACTORY DRAWING. Verify all hardware and make test templates before cutting.</text>

  <line x1="{CX}" y1="78" x2="{CX}" y2="1080" class="center"/>
  <path d="{BODY_PATH}" class="outline"/>

  <!-- Neck, fingerboard and headstock -->
  <path d="M {CX-29} {fret_y(22):.2f} L {CX-24.5} {NUT_Y} L {CX+24.5} {NUT_Y} L {CX+29} {fret_y(22):.2f} Z" class="outline"/>
  <path d="M {CX-24.5} {NUT_Y} L {CX-36} 91 L {CX-25} 73 L {CX+25} 73 L {CX+36} 91 L {CX+24.5} {NUT_Y} Z" class="outline"/>
  <line x1="{CX-24.5}" y1="{NUT_Y}" x2="{CX+24.5}" y2="{NUT_Y}" class="outline"/>
  {''.join(fret_lines)}
  <text x="{CX+33}" y="{fret_y(12)+1:.2f}" class="small">12</text>
  <text x="{CX+35}" y="{fret_y(22)+1:.2f}" class="small">22</text>

  <!-- Suggested routs: intentionally dashed -->
  <rect x="{CX-43}" y="646" width="86" height="38" rx="2" class="cut"/>
  <rect x="{CX-43}" y="727" width="86" height="38" rx="2" class="cut"/>
  <text x="{CX+47}" y="669" class="small">SOAPBAR P-90 - VERIFY ACTUAL COVER</text>
  <text x="{CX+47}" y="750" class="small">SOAPBAR P-90 - VERIFY ACTUAL COVER</text>
  <line x1="{CX-39}" y1="{BRIDGE_Y}" x2="{CX+39}" y2="{BRIDGE_Y}" class="outline"/>
  <circle cx="{CX-41}" cy="{BRIDGE_Y}" r="3.5" class="outline"/>
  <circle cx="{CX+41}" cy="{BRIDGE_Y}" r="3.5" class="outline"/>
  <text x="{CX+50}" y="{BRIDGE_Y+2}" class="small">WRAPAROUND - drill to selected bridge spec</text>

  <!-- Indicative controls -->
  <g class="cut">
    <circle cx="{CX+72}" cy="844" r="4"/><circle cx="{CX+105}" cy="857" r="4"/>
    <circle cx="{CX+67}" cy="889" r="4"/><circle cx="{CX+101}" cy="902" r="4"/>
    <circle cx="{CX-91}" cy="705" r="5"/>
    <path d="M {CX+40} 820 C {CX+122} 807, {CX+146} 860, {CX+119} 930 C {CX+85} 959, {CX+38} 934, {CX+40} 820 Z"/>
  </g>
  <text x="{CX+116}" y="850" class="small">2 volume / 2 tone</text>
  <text x="{CX-145}" y="698" class="small">3-way switch</text>

  <!-- Principal dimensions -->
  <line x1="{CX-210}" y1="{NUT_Y}" x2="{CX-210}" y2="{BRIDGE_Y}" class="dim"/>
  <line x1="{CX-204}" y1="{NUT_Y}" x2="{CX-24.5}" y2="{NUT_Y}" class="fine"/>
  <line x1="{CX-204}" y1="{BRIDGE_Y}" x2="{CX-45}" y2="{BRIDGE_Y}" class="fine"/>
  <text x="{CX-218}" y="{(NUT_Y+BRIDGE_Y)/2}" class="txt" transform="rotate(-90 {CX-218} {(NUT_Y+BRIDGE_Y)/2})">SCALE LENGTH 628.65 mm / 24.75 in (verified)</text>
  <line x1="{CX-166}" y1="1070" x2="{CX+166}" y2="1070" class="dim"/>
  <text x="{CX-39}" y="1065" class="txt">approx. max width 332 mm</text>
  <line x1="{CX-190}" y1="{BODY_TOP}" x2="{CX-190}" y2="{BODY_BOTTOM}" class="dim"/>
  <text x="{CX-198}" y="840" class="txt" transform="rotate(-90 {CX-198} 840)">reference body length 438 mm</text>

  <!-- Calibration and notes -->
  <line x1="35" y1="1120" x2="135" y2="1120" stroke="#000" stroke-width="1"/>
  <line x1="35" y1="1115" x2="35" y2="1125" stroke="#000" stroke-width="1"/>
  <line x1="135" y1="1115" x2="135" y2="1125" stroke="#000" stroke-width="1"/>
  <text x="60" y="1132" class="txt">100 mm calibration bar</text>
  <text x="250" y="1107" class="txt">Verified Gibson values: 628.65 mm scale, 22 frets, 12 in radius, 42.85-43.05 mm nut.</text>
  <text x="250" y="1117" class="txt">Reference values: slab body approx. 438 x 332 x 44.5 mm; silhouette and rout locations reconstructed.</text>
  <text x="250" y="1127" class="txt">Red dashed geometry is indicative only: measure your pickups, pots, switch, bridge and covers first.</text>
  <text x="250" y="1137" class="txt">Print at 100% / Actual size. Confirm the 100 mm bar and nut-to-bridge distance before use.</text>
  <text x="28" y="1162" class="small">Reference drawing prepared 2026-07-03. Gibson and Les Paul are trademarks of their respective owners.</text>
</svg>'''


def make_pdf():
    c = canvas.Canvas(str(PDF_PATH), pagesize=A0)
    c.setTitle("Les Paul Special Single Cut Reference Plan")
    sy = lambda y: H - y
    path = c.beginPath()
    # Same silhouette as the SVG, expressed as explicit cubic segments.
    path.moveTo((CX-30)*mm, sy(BODY_TOP)*mm)
    segments = [
        (CX-55,608,CX-80,620,CX-95,642),(CX-120,650,CX-150,675,CX-157,715),
        (CX-165,760,CX-140,790,CX-150,830),(CX-165,890,CX-145,965,CX-90,1015),
        (CX-45,1055,CX+45,1060,CX+100,1020),(CX+155,980,CX+172,900,CX+155,835),
        (CX+145,795,CX+165,760,CX+155,710),(CX+145,670,CX+115,650,CX+85,655),
        (CX+60,657,CX+45,675,CX+37.5,695),(CX+30,716,CX+15,725,CX+3.5,718),
        (CX-10,708,CX,688,CX+25,670),(CX+35,660,CX+30,625,CX+30,BODY_TOP),
    ]
    for x1,y1,x2,y2,x3,y3 in segments:
        path.curveTo(x1*mm,sy(y1)*mm,x2*mm,sy(y2)*mm,x3*mm,sy(y3)*mm)
    path.close()
    c.setLineWidth(0.7*mm); c.drawPath(path)
    c.setLineWidth(0.3*mm); c.setDash(7*mm,3*mm); c.setStrokeColorRGB(.16,.45,.65)
    c.line(CX*mm,sy(78)*mm,CX*mm,sy(1080)*mm)
    c.setDash(); c.setStrokeColorRGB(0,0,0)
    c.setFont("Helvetica-Bold", 10*mm); c.drawString(28*mm, sy(38)*mm, "LES PAUL SPECIAL SINGLE CUT - 1950s STYLE REFERENCE PLAN")
    c.setFont("Helvetica", 5*mm); c.drawString(28*mm, sy(49)*mm, "A0 / 1:1 / millimetres / construction-reference drawing")
    c.setFillColorRGB(.57,.17,.13); c.setFont("Helvetica",4.2*mm)
    c.drawString(28*mm,sy(59)*mm,"NOT A GIBSON FACTORY DRAWING. Verify all hardware and make test templates before cutting.")
    c.setFillColorRGB(0,0,0)
    # Fingerboard and frets.
    c.setLineWidth(.55*mm)
    c.line((CX-24.5)*mm,sy(NUT_Y)*mm,(CX-29)*mm,sy(fret_y(22))*mm)
    c.line((CX+24.5)*mm,sy(NUT_Y)*mm,(CX+29)*mm,sy(fret_y(22))*mm)
    c.line((CX-24.5)*mm,sy(NUT_Y)*mm,(CX+24.5)*mm,sy(NUT_Y)*mm)
    for n in range(1,23):
        y=fret_y(n); half=24.5+(y-NUT_Y)/(fret_y(22)-NUT_Y)*4.5
        c.setLineWidth(.25*mm); c.line((CX-half)*mm,sy(y)*mm,(CX+half)*mm,sy(y)*mm)
    # Headstock.
    hp=c.beginPath(); hp.moveTo((CX-24.5)*mm,sy(NUT_Y)*mm); hp.lineTo((CX-36)*mm,sy(91)*mm)
    hp.lineTo((CX-25)*mm,sy(73)*mm); hp.lineTo((CX+25)*mm,sy(73)*mm); hp.lineTo((CX+36)*mm,sy(91)*mm)
    hp.lineTo((CX+24.5)*mm,sy(NUT_Y)*mm); c.drawPath(hp)
    # Routs and hardware.
    c.setStrokeColorRGB(.75,.22,.17); c.setDash(3*mm,2*mm); c.setLineWidth(.4*mm)
    for y in (646,727): c.roundRect((CX-43)*mm,sy(y+38)*mm,86*mm,38*mm,2*mm)
    for x,y in ((CX+72,844),(CX+105,857),(CX+67,889),(CX+101,902),(CX-91,705)):
        c.circle(x*mm,sy(y)*mm,(5 if x<CX else 4)*mm)
    c.setDash(); c.setStrokeColorRGB(0,0,0); c.setLineWidth(.55*mm)
    c.line((CX-39)*mm,sy(BRIDGE_Y)*mm,(CX+39)*mm,sy(BRIDGE_Y)*mm)
    c.circle((CX-41)*mm,sy(BRIDGE_Y)*mm,3.5*mm); c.circle((CX+41)*mm,sy(BRIDGE_Y)*mm,3.5*mm)
    # Notes and calibration.
    c.setFont("Helvetica",3.5*mm)
    c.drawString((CX+47)*mm,sy(669)*mm,"SOAPBAR P-90 - VERIFY ACTUAL COVER")
    c.drawString((CX+47)*mm,sy(750)*mm,"SOAPBAR P-90 - VERIFY ACTUAL COVER")
    c.drawString((CX+50)*mm,sy(BRIDGE_Y+2)*mm,"WRAPAROUND - drill to selected bridge spec")
    c.setLineWidth(1*mm); c.line(35*mm,sy(1120)*mm,135*mm,sy(1120)*mm)
    c.line(35*mm,sy(1115)*mm,35*mm,sy(1125)*mm); c.line(135*mm,sy(1115)*mm,135*mm,sy(1125)*mm)
    c.setFont("Helvetica",5*mm); c.drawString(60*mm,sy(1132)*mm,"100 mm calibration bar")
    notes=[
        "Verified Gibson values: 628.65 mm scale, 22 frets, 12 in radius, 42.85-43.05 mm nut.",
        "Reference values: slab body approx. 438 x 332 x 44.5 mm; silhouette and rout locations reconstructed.",
        "Red dashed geometry is indicative only: measure your pickups, pots, switch, bridge and covers first.",
        "Print at 100% / Actual size. Confirm the 100 mm bar and nut-to-bridge distance before use.",
    ]
    c.setFont("Helvetica",4.2*mm)
    for i,t in enumerate(notes): c.drawString(250*mm,sy(1107+i*10)*mm,t)
    c.setFont("Helvetica",3.5*mm)
    c.drawString(28*mm,sy(1162)*mm,"Reference drawing prepared 2026-07-03. Gibson and Les Paul are trademarks of their respective owners.")
    c.showPage(); c.save()


SVG_PATH.write_text(make_svg(), encoding="utf-8")
make_pdf()
print(SVG_PATH)
print(PDF_PATH)
