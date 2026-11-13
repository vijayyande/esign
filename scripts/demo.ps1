$ErrorActionPreference = "Stop"
$asp = "http://localhost:8081"
$esp = "http://localhost:8082"

foreach ($url in @("$asp/api/v1/meta", "http://localhost:8082/api/v1/meta", "http://localhost:8083/api/v1/meta", "http://localhost:8084/api/v1/meta")) {
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        try {
            Invoke-RestMethod $url | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    if (-not $ready) { throw "Not ready: $url" }
}

$pem = (curl.exe -sS "$asp/api/v1/meta/certificate") -join "`n"
$onboard = @{ aspId = "ASP001"; name = "Reference application"; certificatePem = $pem } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$esp/api/v1/asps" -ContentType "application/json" -Body $onboard | Out-Null

$sample = Join-Path $PSScriptRoot "sample.txt"
Set-Content -Path $sample -Value "eSign API 3.3 demo document" -Encoding utf8
$upload = curl.exe -s -F "file=@$sample" -F "docInfo=Demo document" "$asp/api/v1/documents" | ConvertFrom-Json
if (-not $upload.documentId) { throw "Upload failed" }

$body = @{
    documentIds = @($upload.documentId)
    signerId = "demo.user@username.ESP001"
    signingAlgorithm = "RSA"
    responseSigType = "PKCS7pdf"
    maxWaitPeriod = 30
} | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$asp/api/v1/esign/requests" -ContentType "application/json" -Body $body
$uri = [Uri]$created.authenticationUrl
$txnref = [System.Web.HttpUtility]::ParseQueryString($uri.Query)["txnref"]
if (-not $txnref) {
    $query = $uri.Query.TrimStart("?")
    $txnref = ($query -split "&" | Where-Object { $_ -like "txnref=*" }) -replace "^txnref=", ""
    $txnref = [Uri]::UnescapeDataString($txnref)
}

$otp = Invoke-RestMethod -Method Post -Uri "$esp/api/v1/esign/auth/otp" -ContentType "application/json" -Body (@{
    txnref = $txnref
    signerId = "demo.user@username.ESP001"
} | ConvertTo-Json)

$authFile = Join-Path $env:TEMP "esign-auth-response.html"
$http = curl.exe -sS -o $authFile -w "%{http_code}" -X POST "$esp/api/v1/esign/auth" `
    --data-urlencode "txnref=$txnref" `
    --data-urlencode "signerId=demo.user@username.ESP001" `
    --data-urlencode "pin=123456" `
    --data-urlencode "otp=$($otp.demoOtp)" `
    --data-urlencode "doc=1"
if ($http -ne "200") {
    throw "Auth failed with HTTP $http`n$(Get-Content -Raw $authFile)"
}
$txn = Invoke-RestMethod "$asp/api/v1/esign/transactions/$($created.txn)"
$txn | ConvertTo-Json -Depth 4
if ($txn.status -ne "1") { throw "Expected status 1 but got $($txn.status) error $($txn.error)" }
$doc = $txn.documentIds[0]
$sig = Invoke-RestMethod "$asp/api/v1/documents/$doc/signature"
if (-not $sig.verified) { throw "Signature did not verify: $($sig.error)" }
if (-not $sig.signedPdf) { throw "Signed PDF was not written: $($sig.pdfError)" }
$pdf = Join-Path $env:TEMP "esign-signed.pdf"
curl.exe -sS -o $pdf "$asp$($sig.signedPdf)"
$head = [System.Text.Encoding]::ASCII.GetString((Get-Content -Encoding Byte -TotalCount 5 $pdf))
if ($head -ne "%PDF-") { throw "Signed download is not a PDF" }
Write-Host "Signed transaction $($created.txn) pdf $($sig.signedPdf)"
