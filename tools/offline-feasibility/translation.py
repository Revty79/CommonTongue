"""Disposable Marian/ONNX adapter; not a production capability interface."""

import json
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
import sentencepiece as spm


class Marian:
    def __init__(self, directory, threads=4, prefix=""):
        started = time.perf_counter()
        self.directory = Path(directory)
        self.prefix = prefix
        self.vocab = json.loads((self.directory / "vocab.json").read_text(encoding="utf-8"))
        self.inverse = {value: key for key, value in self.vocab.items()}
        self.config = json.loads((self.directory / "config.json").read_text())
        self.tokenizer = spm.SentencePieceProcessor(model_file=str(self.directory / "source.spm"))
        self.target = spm.SentencePieceProcessor(model_file=str(self.directory / "target.spm"))
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads
        options.inter_op_num_threads = 1
        self.encoder = ort.InferenceSession(str(self.directory / "onnx/encoder_model_quantized.onnx"), options, providers=["CPUExecutionProvider"])
        self.decoder = ort.InferenceSession(str(self.directory / "onnx/decoder_model_quantized.onnx"), options, providers=["CPUExecutionProvider"])
        self.load_ms = (time.perf_counter() - started) * 1000

    def translate(self, text, max_tokens=96):
        started = time.perf_counter()
        pieces = self.tokenizer.encode(text, out_type=str)
        if self.prefix:
            pieces.insert(0, self.prefix)
        if len(pieces) > 256:
            raise ValueError("Research input exceeds 256 pieces; do not silently truncate")
        ids = [self.vocab.get(piece, self.vocab["<unk>"]) for piece in pieces] + [self.config["eos_token_id"]]
        inputs = np.array([ids], dtype=np.int64)
        mask = np.ones_like(inputs)
        hidden = self.encoder.run(None, {"input_ids": inputs, "attention_mask": mask})[0]
        generated = [self.config["decoder_start_token_id"]]
        eos = self.config["eos_token_id"]
        finished = False
        for _ in range(max_tokens):
            result = self.decoder.run(["logits"], {
                "input_ids": np.array([generated], dtype=np.int64),
                "encoder_hidden_states": hidden,
                "encoder_attention_mask": mask,
            })[0][0, -1].copy()
            result[self.config["pad_token_id"]] = -np.inf
            token = int(np.argmax(result))
            generated.append(token)
            if token == eos:
                finished = True
                break
        output = self.target.decode_pieces([self.inverse[i] for i in generated[1:] if i != eos])
        return {"text": output, "ms": (time.perf_counter() - started) * 1000,
                "tokens": len(generated) - 1, "completed": finished,
                "decoding": "greedy; no KV cache; max 96 tokens", "model": self.directory.name}
