Write-Host "=== Test Item Actions (with Item ID) ===" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080/api"
$token = $null
$itemId = 1  # 已发布的物品ID

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

Write-Host "`n1. Login to get Token" -ForegroundColor Yellow
$loginBody = '{"openid": "test_user_cert"}'
$loginResult = Test-Request -method "POST" -url "/auth/login" -body $loginBody
if ($loginResult) {
    $resultObj = $loginResult | ConvertFrom-Json
    $token = $resultObj.token
    Write-Host "   Token obtained: $($token.Substring(0, 20))..."
}

Write-Host "`n2. Get Item Detail (GET /api/items/{id})" -ForegroundColor Yellow
$detailResult = Test-Request -method "GET" -url "/items/$itemId"
if ($detailResult) {
    $item = $detailResult | ConvertFrom-Json
    Write-Host "   Item Title: $($item.title)"
    Write-Host "   Item Type: $($item.type)"
    Write-Host "   Location: $($item.locationName)"
    Write-Host "   Status: $($item.status)"
}

Write-Host "`n3. Update Item (PUT /api/items/{id})" -ForegroundColor Yellow
$updateBody = '{"title":"Updated Phone","description":"Updated description","locationName":"Library"}'
$updateResult = Test-Request -method "PUT" -url "/items/$itemId" -body $updateBody -authToken $token
if ($updateResult) {
    $updatedItem = $updateResult | ConvertFrom-Json
    Write-Host "   Updated Title: $($updatedItem.title)"
    Write-Host "   Updated Location: $($updatedItem.locationName)"
}

Write-Host "`n4. Create Claim Application (POST /api/applications)" -ForegroundColor Yellow
$claimBody = '{"itemId":' + $itemId + ',"type":"claim","message":"This is my phone, I lost it yesterday!"}'
$claimResult = Test-Request -method "POST" -url "/applications" -body $claimBody -authToken $token
if ($claimResult) {
    $claim = $claimResult | ConvertFrom-Json
    Write-Host "   Application ID: $($claim.id)"
    Write-Host "   Application Status: $($claim.status)"
}

Write-Host "`n5. Get My Applications (GET /api/applications/my)" -ForegroundColor Yellow
$myAppsResult = Test-Request -method "GET" -url "/applications/my" -authToken $token
if ($myAppsResult) {
    $apps = $myAppsResult | ConvertFrom-Json
    Write-Host "   My Applications Count: $($apps.Count)"
}

Write-Host "`n6. Delete Item (DELETE /api/items/{id})" -ForegroundColor Yellow
$deleteResult = Test-Request -method "DELETE" -url "/items/$itemId" -authToken $token
if ($deleteResult) {
    Write-Host "   Item deleted successfully!"
}

Write-Host "`n=== Item Actions Test Complete ===" -ForegroundColor Cyan