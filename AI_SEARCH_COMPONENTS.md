# On-device search components

## ONNX Runtime

The Android app module uses Microsoft's ONNX Runtime Android package to run the text embedding model locally in the app process. The first launch that has network access may download a quantized model (about 23 MB) and its vocabulary into app-private storage; no separate app or service is installed.

- Model: `sentence-transformers/all-MiniLM-L6-v2`, quantized ONNX export `model_qint8_arm64.onnx`
- Immutable source revision: `1110a243fdf4706b3f48f1d95db1a4f5529b4d41`
- Model SHA-256: `4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474`
- Model card/license: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
- Runtime/license: https://github.com/microsoft/onnxruntime (MIT)

The downloaded model is checksum-verified before it can be loaded. If download, checksum validation, tokenizer loading, or ONNX session creation fails, the app retains its existing rule-based matching engine. The model is English-centric, so exact-term matching and source-site metadata remain important for multilingual and niche searches. Before commercial distribution, review the model card and training-data terms for the intended use.

## SQLite FTS5

The local FTS5 virtual table indexes saved-video titles, tags, and source-site names inside the existing encrypted SQLCipher database. BM25 ranking and highlighted snippets are used for advanced searches of the local saved library. It does not replace the external-site extractors. If the active SQLCipher SQLite build does not include FTS5, the repository uses a bounded LIKE fallback rather than failing database startup.
