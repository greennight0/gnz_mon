"""Calibrate a policy from instrumented predictions, never using acceptance rows.

For the existing model --suggestions-only preserves confirmation thresholds exactly.
Specialized models need all 22 class policies; unsupported classes stay disabled.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from dataset import LABELS


def ranked(row):
    a, b = row["firstView"], row["secondView"]
    means = {k: (a.get(k, 0.) + b.get(k, 0.)) / 2. for k in a.keys() | b.keys()}
    rank = lambda values: sorted(values, key=lambda k: (-values[k], k))
    return a, b, means, rank(a), rank(b), rank(means)


def calibrate(report, base, labels, suggestions_only=False):
    rows = [r for r in report["rows"] if r["split"] == "calibration"]
    if not rows:
        raise ValueError("No calibration rows")
    result = copy.deepcopy(base)
    result["classes"] = copy.deepcopy(base.get("classes", {}))
    decisions = []
    for label in labels:
        samples = [(r, ranked(r)) for r in rows]
        suggestion = None
        # At least five correct observations before enabling a provisional suggestion.
        for cutoff in [i / 100 for i in range(10, 96, 5)]:
            selected = [r for r, (_, _, means, *_rest) in samples if means.get(label, 0.) >= cutoff]
            correct = sum(r["truth"] == label for r in selected)
            if correct >= 5 and correct / len(selected) >= .80:
                suggestion = cutoff
                break
        policy = dict(minimumMean=.75, minimumView=.75, minimumMargin=.20,
                      minimumViewMargin=.20, suggestionMinimum=suggestion,
                      confirmationEnabled=bool(suggestions_only))
        if suggestions_only:
            policy.update(base.get("classes", {}).get(label, {}))
            policy["suggestionMinimum"] = suggestion
        else:
            # Select maximal calibration recall subject to precision, then the strictest tie.
            choices = []
            for mean in (.60, .70, .75, .80, .85, .90, .95):
                for view in (.40, .50, .60, .70, .75, .80):
                    if view > mean:
                        continue
                    for margin in (.10, .15, .20, .25, .30):
                        selected = []
                        for row, (a, b, m, ar, br, mr) in samples:
                            if ar[0] == br[0] == mr[0] == label and m[label] >= mean and min(a[label], b[label]) >= view and m[mr[0]]-m[mr[1]] >= margin and min(a[ar[0]]-a[ar[1]], b[br[0]]-b[br[1]]) >= .05:
                                selected.append(row)
                        correct = sum(r["truth"] == label for r in selected)
                        if correct >= 10 and correct / len(selected) >= .95:
                            choices.append((correct, mean, view, margin))
            if choices:
                _, mean, view, margin = max(choices)
                policy.update(minimumMean=mean, minimumView=view, minimumMargin=margin,
                              minimumViewMargin=.05, confirmationEnabled=True)
        result["classes"][label] = policy
        decisions.append(dict(label=label, examples=sum(r["truth"] == label for r in rows), **policy))
    calibration_bytes = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode()
    result["calibration"] = dict(split="calibration", count=len(rows),
        sha256=hashlib.sha256(calibration_bytes).hexdigest(), fieldAcceptance=False,
        note="Threshold selection is not independent acceptance. Unrepresented suggestions remain disabled.")
    result["version"] = "calibration-" + result["calibration"]["sha256"][:12]
    return result, decisions


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path)
    parser.add_argument("--base-policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--suggestions-only", action="store_true")
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    base = json.loads(args.base_policy.read_text(encoding="utf-8"))
    # Legacy labels are tied to the actual model, not the target training taxonomy.
    labels = ("banana", "orange", "lemon", "Granny Smith", "pineapple", "cucumber", "head cabbage", "bell pepper") if args.suggestions_only else LABELS
    policy, decisions = calibrate(report, base, labels, args.suggestions_only)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(policy, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(decisions, indent=2))


if __name__ == "__main__":
    main()
