# Downloads paraphrase-multilingual-MiniLM-L12-v2 ONNX assets for Phase 0 benchmark.
# Output: models/embedding/{model.onnx, tokenizer.json} under project root (gitignored).
#
# This model uses HuggingFace tokenizer.json (SentencePiece), NOT vocab.txt.

param(
    [string]$OutputDir = "$PSScriptRoot\..\models\embedding",
    [ValidateSet("full", "quantized_avx2", "quantized_arm64")]
    [string]$Variant = "quantized_avx2"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$baseUrl = "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main"

$modelSource = switch ($Variant) {
    "full" { "onnx/model.onnx" }
    "quantized_avx2" { "onnx/model_quint8_avx2.onnx" }
    "quantized_arm64" { "onnx/model_qint8_arm64.onnx" }
}

$downloads = @(
    @{ Url = "$baseUrl/tokenizer.json"; Dest = "tokenizer.json" },
    @{ Url = "$baseUrl/tokenizer_config.json"; Dest = "tokenizer_config.json" },
    @{ Url = "$baseUrl/$modelSource"; Dest = "model.onnx" }
)

function Download-IfMissing($url, $dest) {
    if (Test-Path $dest) {
        Write-Host "Already present: $dest"
        return
    }
    Write-Host "Downloading $url ..."
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
}

foreach ($item in $downloads) {
    Download-IfMissing $item.Url (Join-Path $OutputDir $item.Dest)
}

Write-Host ""
Write-Host "Done. Add to local.properties:"
Write-Host "embedding.model.dir=$((Resolve-Path $OutputDir).Path.Replace('\', '\\'))"
Write-Host ""
Write-Host "Run benchmark:"
Write-Host ('  $env:EMBEDDING_MODEL_DIR="' + (Resolve-Path $OutputDir).Path + '"')
Write-Host "  .\gradlew :app:testDebugUnitTest --tests com.example.mydeskrobot.memory.unified.embedding.MemoryEmbeddingBenchmarkTest"
