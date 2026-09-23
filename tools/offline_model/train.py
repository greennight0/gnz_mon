"""Train/export an EfficientNet-Lite0 candidate; never install it into the app.

Requires independent field training/calibration coverage before starting. Acceptance
labels are not used in fitting or early stopping. See README.md for data provenance.
"""
import argparse
import json
import os
from pathlib import Path
from dataset import LABELS, digest, load_dataset, readiness

BACKBONE = "https://tfhub.dev/tensorflow/efficientnet/lite0/feature-vector/2"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--backbone", default=BACKBONE)
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--fine-tune-epochs", type=int, default=5)
    args = parser.parse_args()
    rows = load_dataset(args.manifest)
    report = readiness(rows)
    training_gaps = [g for g in report["gaps"] if not g.startswith("acceptance/")]
    if training_gaps:
        parser.exit(2, "Training blocked by data coverage:\n" + "\n".join(training_gaps) + "\n")
    if args.epochs < 1 or args.fine_tune_epochs < 0:
        parser.error("Invalid epoch counts")
    os.environ.setdefault("TF_USE_LEGACY_KERAS", "1")
    os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")
    import numpy as np
    import tensorflow as tf
    import tf_keras as keras
    import tensorflow_hub as hub
    from PIL import Image, ImageOps
    tf.keras.utils.set_random_seed(42)
    tf.config.threading.set_intra_op_parallelism_threads(4)
    tf.config.threading.set_inter_op_parallelism_threads(2)

    def dataset(split, shuffle):
        subset = [r for r in rows if r["split"] == split]
        def examples():
            for row in subset:
                with Image.open(row["path"]) as original:
                    source = ImageOps.exif_transpose(original).convert("RGB")
                    side = max(source.size)
                    canvas = Image.new("RGB", (side, side), (124, 116, 104))
                    canvas.paste(source, ((side-source.width)//2, (side-source.height)//2))
                    pixels = np.asarray(canvas.resize((224, 224), Image.Resampling.BILINEAR), dtype=np.float32) / 255.
                yield pixels, LABELS.index(row["label"])
        data = tf.data.Dataset.from_generator(examples, output_signature=(
            tf.TensorSpec((224, 224, 3), tf.float32), tf.TensorSpec((), tf.int32)))
        if shuffle:
            data = data.shuffle(len(subset), seed=42, reshuffle_each_iteration=True)
        return data.batch(16).prefetch(1)

    backbone = hub.KerasLayer(args.backbone, trainable=False, name="efficientnet_lite0")
    augmentation = keras.Sequential([
        keras.layers.RandomFlip("horizontal", seed=42),
        keras.layers.RandomRotation(.08, fill_mode="constant", fill_value=.45, seed=43),
        keras.layers.RandomContrast(.1, seed=44),
    ])
    inputs = keras.Input((224, 224, 3), name="rgb_0_1")
    features = backbone(augmentation(inputs))
    outputs = keras.layers.Dense(len(LABELS), activation="softmax", name="produce_probabilities")(
        keras.layers.Dropout(.2, seed=45)(features))
    model = keras.Model(inputs, outputs)
    model.compile(optimizer=keras.optimizers.Adam(1e-3), loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    callbacks = [keras.callbacks.EarlyStopping(monitor="val_loss", patience=3, restore_best_weights=True)]
    train = dataset("training", True)
    calibration = dataset("calibration", False)
    history = model.fit(train, validation_data=calibration, epochs=args.epochs, callbacks=callbacks).history
    if args.fine_tune_epochs:
        backbone.trainable = True
        model.compile(optimizer=keras.optimizers.Adam(1e-5), loss="sparse_categorical_crossentropy", metrics=["accuracy"])
        fine_history = model.fit(train, validation_data=calibration, epochs=args.fine_tune_epochs,
                                 callbacks=callbacks).history
        history["fine_tuning"] = fine_history
    args.output.mkdir(parents=True, exist_ok=True)
    saved = args.output / "saved_model"
    model.save(str(saved), include_optimizer=False)
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    model_path = args.output / "common_plant.tflite"
    model_path.write_bytes(converter.convert())
    interpreter = tf.lite.Interpreter(model_path=str(model_path), num_threads=2)
    interpreter.allocate_tensors()
    assert list(interpreter.get_input_details()[0]["shape"]) == [1, 224, 224, 3]
    assert list(interpreter.get_output_details()[0]["shape"]) == [1, len(LABELS)]
    sample = next(iter(calibration))[0][:1].numpy()
    interpreter.set_tensor(interpreter.get_input_details()[0]["index"], sample)
    interpreter.invoke()
    reference = model(sample, training=False).numpy()
    actual = interpreter.get_tensor(interpreter.get_output_details()[0]["index"])
    if not np.allclose(reference, actual, atol=.01, rtol=.01):
        raise ValueError("TFLite conversion changed model predictions beyond tolerance")
    (args.output / "common_plant_labels.txt").write_text("\n".join(LABELS) + "\n", encoding="utf-8")
    manifest = dict(model="EfficientNet-Lite0 produce20", source=args.backbone,
                    sha256=digest(model_path), bytes=model_path.stat().st_size,
                    input=dict(shape=[1,224,224,3], dtype="float32", normalization="RGB / 255", padding=[124,116,104]),
                    output=dict(shape=[1,len(LABELS)], dtype="float32", kind="softmax"),
                    datasetSha256=digest(args.manifest), seed=42, fieldAcceptance=False,
                    status="candidate_not_for_deployment", history=history)
    (args.output / "common_plant.manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print("Exported candidate. Calibration, license review and ARM64 field acceptance are still required.")


if __name__ == "__main__":
    main()
