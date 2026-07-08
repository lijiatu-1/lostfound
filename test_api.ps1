Write-Host "=== Testing Lost & Found API ===" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080/api"

function Test-API {
    param(
        [string]$method,
        [string]$endpoint
    )
    
    try {
        $response = Invoke-WebRequest -Uri "$baseUrl$endpoint" -Method $method -UseBasicParsing
        Write-Host "✅ $method $endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
        return $response.Content
    } catch {
        Write-Host "❌ $method $endpoint - Failed" -ForegroundColor Red
        return $null
    }
}

Write-Host "`n1. Testing Items API" -ForegroundColor Yellow
$itemsResult = Test-API -method "GET" -endpoint "/items"
if ($itemsResult) {
    Write-Host "   Items count: $(($itemsResult | ConvertFrom-Json).Count)"
}

Write-Host "`n2. Testing Categories API" -ForegroundColor Yellow
Test-API -method "GET" -endpoint "/items/categories"

Write-Host "`n3. Testing Login API" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/auth/login" -Method POST -Body '{"openid":"test_user"}' -ContentType "application/json" -UseBasicParsing
    Write-Host "✅ POST /auth/login - Status: $($response.StatusCode)" -ForegroundColor Green
    $result = $response.Content | ConvertFrom-Json
    Write-Host "   Token obtained"
} catch {
    Write-Host "❌ POST /auth/login - Failed" -ForegroundColor Red
}

Write-Host "`n=== API Test Complete ===" -ForegroundColor Cyan