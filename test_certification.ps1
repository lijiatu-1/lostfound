Write-Host "=== User Certification Process Test ===" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080/api"
$token = $null
$userId = $null

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

Write-Host "`n1. Login and Get Token" -ForegroundColor Yellow
$loginBody = '{"openid": "test_user_cert"}'
$loginResult = Test-Request -method "POST" -url "/auth/login" -body $loginBody
if ($loginResult) {
    $resultObj = $loginResult | ConvertFrom-Json
    $token = $resultObj.token
    $userId = $resultObj.user.id
    Write-Host "   User ID: $userId"
    Write-Host "   Token: $($token.Substring(0, 20))..."
    Write-Host "   Status: $($resultObj.user.status)"
}

Write-Host "`n2. Submit Certification" -ForegroundColor Yellow
$certBody = '{"realName":"ZhangSan","studentId":"2021001001","cardPhoto":"https://example.com/card.jpg"}'
$certResult = Test-Request -method "POST" -url "/auth/certification" -body $certBody -authToken $token
if ($certResult) {
    $certObj = $certResult | ConvertFrom-Json
    Write-Host "   Certification Status: $($certObj.status)"
}

Write-Host "`n3. Admin Review (Accept)" -ForegroundColor Yellow
$reviewBody = '{"action":"accept"}'
$reviewResult = Test-Request -method "POST" -url "/auth/certification/$userId/review" -body $reviewBody
if ($reviewResult) {
    $reviewObj = $reviewResult | ConvertFrom-Json
    Write-Host "   After Review Status: $($reviewObj.status)"
}

Write-Host "`n4. Get User Info" -ForegroundColor Yellow
$userResult = Test-Request -method "GET" -url "/auth/user" -authToken $token
if ($userResult) {
    $userObj = $userResult | ConvertFrom-Json
    Write-Host "   User Status: $($userObj.status)"
    Write-Host "   Real Name: $($userObj.realName)"
    Write-Host "   Student ID: $($userObj.studentId)"
}

Write-Host "`n5. Publish Item (After Certification)" -ForegroundColor Yellow
$publishBody = '{"title":"Test Phone","description":"Found in teaching building","type":"lost","category":"Electronics","locationName":"Building A","contact":"13800138000","images":["https://example.com/phone.jpg"]}'
$publishResult = Test-Request -method "POST" -url "/items" -body $publishBody -authToken $token
if ($publishResult) {
    $publishObj = $publishResult | ConvertFrom-Json
    Write-Host "   Published Successfully! Item ID: $($publishObj.item.id)"
}

Write-Host "`n=== Certification Process Test Complete ===" -ForegroundColor Cyan