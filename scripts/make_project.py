"""Genera un custom project basico de SQX: Build -> Retest -> Optimize (WFO).

Parte de las tareas sueltas del Builder, Retester y Optimizer de SQX (los ajustes
que el usuario tiene ahora) y cambia mercado, costes, fechas y databanks.
Uso: python -I make_project.py <dir_con_Builder_Retester_Optimizer> <salida.cfx> <nombre>
"""
import copy
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

src, out_cfx, name = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]

SYMBOL, TF, SPREAD = "SP_M1_dukas", "H1", "6"
DATE_FROM, DATE_TO = "2013.01.01", "2026.10.22"
OOS_FROM = "2022.01.01"
COMMISSIONS = ('<Method type="SizeBased" use="true"><Params>'
               '<Param key="Commission" className="SizeBased">0.55</Param></Params></Method>')
SWAP = ('<Swap use="true" type="money" long="-11.56" short="5.18" '
        'tripleSwapOn="FRIDAY" rolloutHour="23:00" />')
DB_BUILD, DB_RETEST, DB_OPT = "1-Build", "2-Retest", "3-Optimized"


def load(rel):
    return ET.parse(src / rel).getroot()


def patch_data(root, precision=None):
    data = root.find("Data")
    setup = data.find("Setups/Setup")
    setup.set("dateFrom", DATE_FROM)
    setup.set("dateTo", DATE_TO)
    setup.set("engine", "MetaTrader5 (hedged)")
    if precision is not None:
        setup.set("testPrecision", precision)
    chart = setup.find("Chart")
    chart.set("symbol", SYMBOL)
    chart.set("timeframe", TF)
    chart.set("spread", SPREAD)
    comm = setup.find("Commissions")
    comm.clear()
    comm.append(ET.fromstring(COMMISSIONS))
    old_swap = setup.find("Swap")
    if old_swap is not None:
        setup.remove(old_swap)
    setup.append(ET.fromstring(SWAP))
    old_oos = data.find("OutOfSample")
    idx = list(data).index(old_oos)
    data.remove(old_oos)
    oos = ET.Element("OutOfSample", {"showGraph": "false"})
    ET.SubElement(oos, "Range", {"dateFrom": OOS_FROM, "dateTo": DATE_TO})
    data.insert(idx, oos)


def set_databank(root, kind, value, only_first=True):
    for db in root.findall("Databanks/Databank"):
        if db.get("name") == kind:
            db.set("value", value)
            if only_first:
                return
    raise SystemExit(f"No encuentro el databank {kind}")


def drop_resources(root):
    res = root.find("Resources")
    if res is not None:
        root.remove(res)


def to_bytes(root):
    return ET.tostring(root, encoding="utf-8")


# Build
build = load("Builder/Build-Task1.xml")
patch_data(build)
for p in build.iter("Param"):
    if p.get("key") == "MarketOpenSession":
        p.text = "SP500_darwinex"
set_databank(build, "Output", DB_BUILD)  # el primero es Results; el segundo, Last generation
drop_resources(build)

# Retest
retest = load("Retester/Retest-Task1.xml")
patch_data(retest)
set_databank(retest, "Input", DB_BUILD)
set_databank(retest, "Output", DB_RETEST)
drop_resources(retest)

# Optimize (walk-forward): mismos datos y money management que el Builder
opt = load("Optimizer/Optimize-Task1.xml")
patch_data(opt, precision="1")
opt.find("Optimization").set("type", "2")  # 1 simple, 2 walk-forward, 3 WF matrix
old_mm = opt.find("RiskMoneyManagement")
idx = list(opt).index(old_mm)
opt.remove(old_mm)
opt.insert(idx, copy.deepcopy(build.find("RiskMoneyManagement")))
set_databank(opt, "Input", DB_RETEST)
set_databank(opt, "Output", DB_OPT)
drop_resources(opt)

config = f"""<Project name="{name}" version="143.2708">
  <Tasks>
    <Task type="Build" name="Build strategies" showSettingsOverview="false" sampleName="Custom" active="true" taskXMLFile="Build-Task1.xml" title="1 - Generar" />
    <Task type="Retest" name="Retest strategies" showSettingsOverview="false" sampleName="Custom" active="true" taskXMLFile="Retest-Task1.xml" title="2 - Retest OOS" />
    <Task type="Optimize" name="Optimize" showSettingsOverview="false" sampleName="Custom" active="true" taskXMLFile="Optimize-Task1.xml" title="3 - Walk-forward" />
  </Tasks>
  <Databanks>
    <Databank name="{DB_BUILD}" view="Default - Main data" syncType="Auto-sync never" position="10" />
    <Databank name="{DB_RETEST}" view="Default - Full sample - ISV" syncType="Auto-sync never" position="20" />
    <Databank name="{DB_OPT}" view="Default - WF" syncType="Auto-sync never" position="30" />
    <Databank name="Last generation" view="Default" syncType="Auto-sync never" position="40" />
    <Databank name="Initial population" view="Default" syncType="Auto-sync never" position="50" />
    <Databank name="Strategies to improve" view="Default" syncType="Auto-sync never" position="60" />
    <Databank name="Existing portfolio" view="Default" syncType="Auto-sync never" position="70" />
  </Databanks>
</Project>
"""

out_cfx.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(out_cfx, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("config.xml", config.encode("utf-8"))
    z.writestr("Build-Task1.xml", to_bytes(build))
    z.writestr("Retest-Task1.xml", to_bytes(retest))
    z.writestr("Optimize-Task1.xml", to_bytes(opt))

# Comprobacion: todo vuelve a parsear
with zipfile.ZipFile(out_cfx) as z:
    for n in z.namelist():
        ET.fromstring(z.read(n))
print("OK", out_cfx, out_cfx.stat().st_size, "bytes")
