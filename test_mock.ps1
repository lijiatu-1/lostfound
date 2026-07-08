$resp = Invoke-WebRequest -Uri "http://localhost:8081/api/items" -UseBasicParsing
Write-Host "Status: $($resp.StatusCode)"
Write-Host "Content:`n$($resp.Content)"