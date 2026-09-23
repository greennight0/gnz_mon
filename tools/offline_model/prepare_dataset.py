"""Download a reproducible, licensed TRAINING bootstrap. Never manufacture field splits.

Fruits-360 does not establish a plain pomelo class: its 'Pomelo Sweetie' is not
silently relabelled as Vietnamese buoi. Nor do its isolated fruits cover bunches.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import hashlib
import json
import urllib.parse
import urllib.request

REVISION = "45ed8feceec7e2512d47042662773cbd9a4c9c0e"
REPO = "fruits-360/fruits-360-100x100"
FOLDERS = {
    "banana": ["Banana 1", "Banana 3", "Banana Lady Finger 1"],
    "lime": ["Limes 1"], "lemon": ["Lemon 1", "Lemon Meyer 1"], "kumquat": ["Kumquats 1"],
    "orange": ["Orange 1", "Orange 2"], "mandarin": ["Mandarine 1"],
    "apple": ["Apple Granny Smith 1", "Apple Red 1"], "mango": ["Mango 1", "Mango Red 1"],
    "guava": ["Guava 1"], "papaya": ["Papaya 1"], "pineapple": ["Pineapple 1"],
    "watermelon": ["Watermelon 1"], "tomato": ["Tomato 1", "Tomato Yellow 1"],
    "cucumber": ["Cucumber 1"], "carrot": ["Carrot 1"], "potato": ["Potato White 1", "Potato Red 1"],
    "sweet_potato": ["Potato Sweet 1"], "cabbage": ["Cabbage white 1", "Cabbage red 1"],
    "bell_pepper": ["Pepper Red 1", "Pepper Green 1", "Pepper Yellow 1"],
    "outside_scope": ["Kiwi 1", "Pear 1", "Grape Blue 1"],
}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "GNZ-MON-dataset-preparation"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "data/bootstrap")
    parser.add_argument("--per-folder", type=int, default=40)
    args = parser.parse_args()
    if args.per_folder < 1:
        parser.error("--per-folder must be positive")
    args.output.mkdir(parents=True, exist_ok=True)
    raw = f"https://raw.githubusercontent.com/{REPO}/{REVISION}"
    license_bytes = fetch(raw + "/LICENSE")
    (args.output / "Fruits-360-LICENSE").write_bytes(license_bytes)
    (args.output / "Fruits-360-README.md").write_bytes(fetch(raw + "/README.md"))
    tasks = []
    for label, folders in FOLDERS.items():
        for folder in folders:
            directory = urllib.parse.quote("Training/" + folder)
            listing = json.loads(fetch(f"https://api.github.com/repos/{REPO}/contents/{directory}?ref={REVISION}"))
            files = sorted((r for r in listing if r["name"].endswith(".jpg")), key=lambda r: r["name"])
            for i in range(min(args.per_folder, len(files))):
                item = files[i * len(files) // min(args.per_folder, len(files))]
                tasks.append((label, folder, item))
    def download(task):
        label, folder, item = task
        name = f"images/{folder.replace(' ', '_')}/{item['name']}"
        destination = args.output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        # Reuse only bytes matching the pinned Git blob hash.
        content = destination.read_bytes() if destination.exists() else fetch(item["download_url"])
        blob_hash = hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()
        if blob_hash != item["sha"]:
            raise ValueError(f"Upstream blob mismatch: {name}")
        destination.write_bytes(content)
        return dict(image=name, label=label, split="training", specimenId=f"fruits360:{folder}",
                    sessionId=f"fruits360:{folder}", environment="studio", source=item["html_url"],
                    license="CC-BY-SA-4.0", licenseFile="Fruits-360-LICENSE", sourceRevision=REVISION,
                    labelSource=f"Fruits-360 author label: {folder}", sha256=hashlib.sha256(content).hexdigest(),
                    attributes=[])
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(download, tasks))
    # Identical source bytes are not extra independent samples.
    unique = {r["sha256"]: r for r in rows}
    manifest = {"schemaVersion": 1, "limitations": ["Training bootstrap only; no independent field evaluation",
        "Missing pomelo and other_plant; missing real banana bunches and field negatives"],
        "images": list(unique.values())}
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Prepared {len(unique)} studio training images; field readiness is NOT satisfied.")


if __name__ == "__main__":
    main()
