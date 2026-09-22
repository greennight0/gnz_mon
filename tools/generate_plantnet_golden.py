"""Export CPU goldens independently of Android JPEG decode/Canvas resize.

Reference: Python 3.13, Pillow 12.3.0, numpy 2.5.3, ai-edge-litert 2.2.0.
Run: python tools/generate_plantnet_golden.py
"""
from pathlib import Path
import hashlib
import json
import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/androidTest/assets"
source = assets / "plantnet300k_upstream_1.jpg"
model = root / "app/src/main/assets/models/plantnet.tflite"
assert hashlib.sha256(source.read_bytes()).hexdigest() == "51708858ba06f339d3a5761964c7dec2cb67e93d5c9784b08ee7d3bce704e730"
assert hashlib.sha256(model.read_bytes()).hexdigest() == "6f59f046c6a86593713aca76a3ab7bb55b520265eb66f5a77a114e450b1ccbf5"
rgb = np.asarray(Image.open(source).convert("RGB").resize((224, 224), Image.Resampling.BILINEAR), dtype=np.float32) / 255
tensor = np.transpose((rgb - np.array([.485, .456, .406], np.float32)) / np.array([.229, .224, .225], np.float32), (2, 0, 1))[None]
binary = tensor.astype("<f4").tobytes()
(assets / "plantnet300k_reference_input.f32").write_bytes(binary)
runner = Interpreter(model_path=str(model), num_threads=2)
runner.allocate_tensors()
runner.set_tensor(runner.get_input_details()[0]["index"], tensor)
runner.invoke()
logits = runner.get_tensor(runner.get_output_details()[0]["index"])[0]
assert int(logits.argmax()) == 4
reference = {
    "inputSha256": hashlib.sha256(binary).hexdigest(),
    "preprocessing": "Pillow BILINEAR RGB 224x224; ImageNet normalization; Float32 NCHW little endian",
    "topOne": 4,
    "scientificName": "Cirsium vulgare (Savi) Ten.",
    "logits": logits.tolist(),
}
(assets / "plantnet300k_reference.json").write_text(json.dumps(reference, indent=2) + "\n", encoding="utf-8")
print("Exported fixed tensor:", reference["inputSha256"])
