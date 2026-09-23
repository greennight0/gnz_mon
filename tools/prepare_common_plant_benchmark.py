"""Download a small labelled Fruits-360 regression set (not a field accuracy benchmark)."""
from pathlib import Path
import hashlib, json, urllib.request, urllib.parse
from concurrent.futures import ThreadPoolExecutor
ROOT = Path(__file__).resolve().parents[1] / "app/src/androidTest/assets/common-plant-benchmark"
REPO = "fruits-360/fruits-360-100x100"
def get(url):
    return urllib.request.urlopen(url, timeout=60).read()
def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    revision = "45ed8feceec7e2512d47042662773cbd9a4c9c0e"
    (ROOT / "LICENSE").write_bytes(get(f"https://raw.githubusercontent.com/{REPO}/{revision}/LICENSE"))
    tasks = []
    for folder, truth, category in [("Banana 1","banana","fruit"),("Banana 3","banana","fruit"),
        ("Orange 1","orange","fruit"),("Lemon 1","lemon","fruit"),
        ("Apple Granny Smith 1","Granny Smith","fruit"),("Mango 1",None,"unsupported")]:
        for split, directory in [("calibration","Training"),("acceptance","Test")]:
            path = urllib.parse.quote(f"{directory}/{folder}")
            listing = json.loads(get(f"https://api.github.com/repos/{REPO}/contents/{path}?ref={revision}"))
            files = sorted([r for r in listing if r["name"].endswith(".jpg")], key=lambda x:x["name"])
            count = 10 if truth == "banana" else 4
            for i in range(count):
                item = files[i * len(files) // count]
                name = f"{split}-{folder.replace(' ','_')}-{i}.jpg"
                tasks.append((name,item["download_url"],dict(image=name,split=split,category=category,
                    groupCode=truth,sourceClass=folder,sourceRevision=revision,labelSource="Fruits-360 author-provided class",source=item["html_url"])))
    def download(task):
        name,url,row=task
        data=get(url); (ROOT/name).write_bytes(data)
        row["sha256"]=hashlib.sha256(data).hexdigest()
        return row
    with ThreadPoolExecutor(max_workers=6) as pool: rows=list(pool.map(download,tasks))
    # Supplement fruit fixtures with source-labelled leaves and visible non-plant scenes.
    leaf_repo = "spMohanty/PlantVillage-Dataset"
    leaf_rev = "7f7ecc7e1eaca78107e3affe7cb5abd9427e139a"
    (ROOT/"PlantVillage-NOTICE.md").write_bytes(get(f"https://raw.githubusercontent.com/{leaf_repo}/{leaf_rev}/README_HF.md"))
    leaf_items = json.loads(get(f"https://api.github.com/repos/{leaf_repo}/contents/raw/color/Tomato___healthy?ref={leaf_rev}"))
    for i, item in enumerate(leaf_items[:8]):
        name = f"tomato-leaf-{i}.jpg"
        data = get(item["download_url"]); (ROOT/name).write_bytes(data)
        rows.append(dict(image=name,split="calibration" if i < 4 else "acceptance",category="leaf",
            groupCode=None,scientificName="Solanum lycopersicum L.",source=item["html_url"],
            sourceRevision=leaf_rev,license="CC-BY-SA-3.0",labelSource="PlantVillage Tomato healthy",sha256=hashlib.sha256(data).hexdigest()))
    scene_repo = "scikit-image/skimage-tutorials"
    scene_rev = "26e2d4d27113a86b9dd99dc7e559ab25aa782436"
    (ROOT/"scikit-image-LICENSE.txt").write_bytes(get(f"https://raw.githubusercontent.com/{scene_repo}/{scene_rev}/LICENSE.txt"))
    for i, filename in enumerate(["balloon.jpg", "chapel_floor.png"]):
        url=f"https://raw.githubusercontent.com/{scene_repo}/{scene_rev}/images/{filename}"
        data=get(url); (ROOT/filename).write_bytes(data)
        rows.append(dict(image=filename,split="calibration" if i == 0 else "acceptance",category="object",
            groupCode=None,source=url,sourceRevision=scene_rev,labelSource="scikit-image named scene; non-plant",sha256=hashlib.sha256(data).hexdigest()))
    golden = ROOT.parent/"plantnet300k_upstream_1.jpg"
    data=golden.read_bytes(); (ROOT/"golden-flower.jpg").write_bytes(data)
    rows.append(dict(image="golden-flower.jpg",split="acceptance",category="flower",groupCode=None,
        scientificName="Cirsium vulgare (Savi) Ten.",source="../plantnet300k_reference.json",
        labelSource="Existing upstream-labelled PlantNet golden fixture",sha256=hashlib.sha256(data).hexdigest()))
    (ROOT/"manifest.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
    print(f"Downloaded {len(rows)} labelled fixtures; revision {revision}")
if __name__ == "__main__": main()
