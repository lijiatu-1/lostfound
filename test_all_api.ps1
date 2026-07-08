Write-Host "=== Testing Lost & Found Platform API ===" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080/api"
$token = $null
$itemId = $null

function Test-Request {
    param(
        [string]$method,
        [string]$url,
        [string]$body = $null,
        [string]$authToken = $null
    )
    
    $headers = @{}
    if ($authToken) {
        $headers["Authorization"] = "Bearer $authToken"
    }
    
    try {
        $params = @{
            Uri = "$baseUrl$url"
            Method = $method
            Headers = $headers
            UseBasicParsing = $true
        }
        
        if ($body) {
            $params["Body"] = $body
            $params["ContentType"] = "application/json"
        }
        
        $response = Invoke-WebRequest @params
        Write-Host "OK $method $url - Status: $($response.StatusCode)" -ForegroundColor Green
        return $response.Content
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "FAIL $method $url - Status: $statusCode" -ForegroundColor Red
        if ($_.Exception.Response) {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errorResponse = $reader.ReadToEnd()
            Write-Host "   Error: $errorResponse"
        }
        return $null
    }
}

Write-Host "`n1. Test Login (POST /api/auth/login)" -ForegroundColor Yellow
$loginBody = '{"openid": "test_user_001"}'
$loginResult = Test-Request -method "POST" -url "/auth/login" -body $loginBody
if ($loginResult) {
    $token = ($loginResult | ConvertFrom-Json).token
    Write-Host "   Token obtained: $($token.Substring(0, 20))..."
}

Write-Host "`n2. Test Get User Info (GET /api/auth/user)" -ForegroundColor Yellow
Test-Request -method "GET" -url "/auth/user" -authToken $token

Write-Host "`n3. Test Get Items List (GET /api/items)" -ForegroundColor Yellow
$itemsResult = Test-Request -method "GET" -url "/items"
if ($itemsResult) {
    $items = $itemsResult | ConvertFrom-Json
    Write-Host "   Items count: $($items.Count)"
}

Write-Host "`n4. Test Publish Item (POST /api/items)" -ForegroundColor Yellow
$publishBody = '{"title":"Test Item-Campus Card","description":"Found in library, has cartoon pattern","type":"lost","category":"ID Cards","location":"Library 2nd Floor","contact":"13800138000","images":["https://example.com/card.jpg"]}'
$publishResult = Test-Request -method "POST" -url "/items" -body $publishBody -authToken $token
if ($publishResult) {
    $item = $publishResult | ConvertFrom-Json
    $itemId = $item.id
    Write-Host "   Published successfully! Item ID: $itemId"
}

Write-Host "`n5. Test Get Item Detail (GET /api/items/{id})" -ForegroundColor Yellow
if ($itemId) {
    Test-Request -method "GET" -url "/items/$itemId"
}

Write-Host "`n6. Test Update Item (PUT /api/items/{id})" -ForegroundColor Yellow
if ($itemId) {
    $updateBody = '{"title":"Test Item-Campus Card (Updated)","description":"Updated description"}'
    Test-Request -method "PUT" -url "/items/$itemId" -body $updateBody -authToken $token
}

Write-Host "`n7. Test Create Application (POST /api/applications)" -ForegroundColor Yellow
if ($itemId) {
    $applyBody = '{"itemId":' + $itemId + ',"type":"claim","message":"This is my campus card, ID: 123456"}'
    Test-Request -method "POST" -url "/applications" -body $applyBody -authToken $token
}

Write-Host "`n8. Test Get Messages (GET /api/messages)" -ForegroundColor Yellow
Test-Request -method "GET" -url "/messages" -authToken $token

Write-Host "`n9. Test Get Categories (GET /api/items/categories)" -ForegroundColor Yellow
Test-Request -method "GET" -url "/items/categories"

Write-Host "`n10. Test Search Items (GET /api/items/search)" -ForegroundColor Yellow
Test-Request -method "GET" -url "/items/search?keyword=campus"

Write-Host "`n=== API Testing Complete ===" -ForegroundColor Cyan