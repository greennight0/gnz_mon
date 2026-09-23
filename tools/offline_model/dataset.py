"""Shared, dependency-free dataset validation. Studio frames are training-only.

Manifest: {schemaVersion: 1, images: [{image, label, split, specimenId, sessionId,
source, license, labelSource, sha256, environment, attributes: []}]}.
Paths are relative to the manifest. IDs must be stable across all source splits.
"""
from collections import Counter, defaultdict
from pathlib import Path
import hashlib
import json

PRODUCE = (
    "banana", "lime", "lemon", "kumquat", "orange", "mandarin", "pomelo", "apple",
    "mango", "guava", "papaya", "pineapple", "watermelon", "tomato", "cucumber",
    "carrot", "potato", "sweet_potato", "cabbage", "bell_pepper",
)
LABELS = PRODUCE + ("other_plant", "outside_scope")
SPLITS = ("training", "calibration", "acceptance")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_dataset(path):
    path = Path(path).resolve()
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1 or not isinstance(data.get("images"), list):
        raise ValueError("Expected schemaVersion=1 and images array")
    seen_paths, identities, hashes = set(), {}, {}
    rows = data["images"]
    for row in rows:
        for key in ("image", "label", "split", "specimenId", "sessionId", "source", "license", "labelSource", "sha256", "environment"):
            if not isinstance(row.get(key), str) or not row[key].strip():
                raise ValueError(f"Missing {key}: {row.get('image')}")
        if row["label"] not in LABELS or row["split"] not in SPLITS:
            raise ValueError(f"Unknown label/split: {row['image']}")
        if row["environment"] not in ("field", "studio"):
            raise ValueError("environment must be field or studio")
        if row["environment"] == "studio" and row["split"] != "training":
            raise ValueError("Studio images cannot be field calibration/acceptance")
        image = (path.parent / row["image"]).resolve()
        if not image.is_relative_to(path.parent) or image in seen_paths:
            raise ValueError(f"Duplicate or escaping image path: {row['image']}")
        seen_paths.add(image)
        if not image.is_file() or digest(image) != row["sha256"]:
            raise ValueError(f"Missing image or checksum mismatch: {row['image']}")
        if row["sha256"] in hashes:
            raise ValueError(f"Duplicate image content: {row['image']}")
        hashes[row["sha256"]] = row["split"]
        for key in ("specimenId", "sessionId"):
            identity = (key, row[key])
            if identity in identities and identities[identity] != row["split"]:
                raise ValueError(f"Cross-split {key} leakage: {row[key]}")
            identities[identity] = row["split"]
        row["path"] = image
    return rows


def readiness(rows):
    counts = {split: Counter(r["label"] for r in rows if r["split"] == split) for split in SPLITS}
    # These are minimum data checks, not a guarantee of model accuracy.
    minimums = {"training": 30, "calibration": 20, "acceptance": 30}
    gaps = [f"{split}/{label}: {counts[split][label]}/{minimums[split]}"
            for split in SPLITS for label in LABELS if counts[split][label] < minimums[split]]
    field_training = Counter(r["label"] for r in rows if r["split"] == "training" and r["environment"] == "field")
    gaps += [f"training/{label}: needs field examples" for label in LABELS if field_training[label] < 10]
    for split in ("training", "calibration"):
        attributes = {a for r in rows if r["split"] == split and r["label"] == "banana"
                      for a in r.get("attributes", [])}
        gaps += [f"{split}/banana: missing {a}" for a in ("bunch", "single", "green", "ripe", "cluttered_background", "low_light")
                 if a not in attributes]
    return {"counts": {s: dict(counts[s]) for s in SPLITS}, "gaps": gaps, "ready": not gaps}


def evaluate(rows):
    """Evaluate output rows, counting wrong scientific species as false confirmations too."""
    acceptance = [r for r in rows if r["split"] == "acceptance"]
    accepted = [r for r in acceptance if r["accepted"]]
    per_class = {}
    for label in PRODUCE:
        group = [r for r in acceptance if r["truth"] == label]
        correct = sum(r["accepted"] and r["prediction"] == label for r in group)
        recall = correct / len(group) if group else None
        per_class[label] = {"count": len(group), "recall": recall,
                            "passed": len(group) >= 30 and recall >= (.90 if label == "banana" else .85)}
    precision = sum(r["correct"] for r in accepted) / len(accepted) if accepted else None
    outside = [r for r in acceptance if r["truth"] == "outside_scope"]
    false_rate = sum(r["accepted"] for r in outside) / len(outside) if outside else None
    passed = (all(r["passed"] for r in per_class.values()) and precision is not None and precision >= .95
              and len(outside) >= 30 and false_rate <= .05)
    return {"perClass": per_class, "acceptedPrecision": precision, "outsideFalseAcceptanceRate": false_rate,
            "numericalGatePassed": passed}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = readiness(load_dataset(args.manifest))
    output = json.dumps(report, ensure_ascii=False, indent=2)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output, encoding="utf-8")
    print(output)
    raise SystemExit(0 if report["ready"] else 2)
