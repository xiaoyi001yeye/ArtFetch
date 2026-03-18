# PowerShell 脚本下载 5 个艺术品详情页 HTML 样本
$ErrorActionPreference = "Stop"

$downloadDir = "c:\code\ArtFetch\download"
$urls = @(
    @{url = "https://auction.artron.net/paimai-art31600061/"; filename = "art31600061.html"},
    @{url = "https://auction.artron.net/paimai-art5218483031/"; filename = "art5218483031.html"},
    @{url = "https://auction.artron.net/paimai-art5242552056/"; filename = "art5242552056.html"},
    @{url = "https://auction.artron.net/paimai-art5141700424/"; filename = "art5141700424.html"},
    @{url = "https://auction.artron.net/paimai-art0011950838/"; filename = "art0011950838.html"}
)

$headers = @{
    "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    "Accept-Language" = "zh-CN,zh;q=0.9"
    "Referer" = "https://artso.artron.net/"
}

foreach ($item in $urls) {
    $outFile = Join-Path $downloadDir $item.filename
    Write-Host "Downloading $($item.url) -> $outFile ..."
    try {
        $response = Invoke-WebRequest -Uri $item.url -Headers $headers -TimeoutSec 30 -UseBasicParsing
        $html = $response.Content
        [System.IO.File]::WriteAllText($outFile, $html, [System.Text.Encoding]::UTF8)
        Write-Host "Saved $($html.Length) bytes"
        Start-Sleep -Seconds 1
    } catch {
        Write-Error "Failed to download $($item.url): $_"
    }
}

Write-Host "`nAll downloads completed!"
