$uri = "http://localhost:8080/api/auth/login"
$body = '{"openid": "test_openid"}'

try {
    $response = Invoke-WebRequest -Uri $uri -Method POST -Body $body -ContentType "application/json" -UseBasicParsing
    Write-Host "Status Code: $($response.StatusCode)"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "Status Code: $statusCode"
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $errorResponse = $reader.ReadToEnd()
        Write-Host "Error Response: $errorResponse"
    }
}