<# 在独立文件库启动公司工作区；首次建表，之后严格 validate，不覆盖已有库。 #>
param([ValidateSet('structured','ddd')][string]$Strategy='structured', [int]$Port=18083,
 [string]$RuntimeDirectory, [string]$JavaPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $RuntimeDirectory){$RuntimeDirectory=Join-Path $projectRoot 'work/company-runtime'}
$runtime=[IO.Path]::GetFullPath($RuntimeDirectory)
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
if(-not $JavaPath){throw '请设置 JAVA_HOME 或传入 -JavaPath，要求 JDK21'}
$java=Join-Path $JavaPath 'bin/java.exe'
if(-not(Test-Path -LiteralPath $java)){throw '没有找到 JDK java.exe'}
$pidFile=Join-Path $runtime 'company.pid'
if(Test-Path -LiteralPath $pidFile){
 $runningId=[int](Get-Content -LiteralPath $pidFile)
 $running=Get-CimInstance Win32_Process -Filter "ProcessId=$runningId"
 if($running -and $running.CommandLine.Contains($runtime) -and $running.CommandLine.Contains('company-console.jar')){Stop-Process -Id $runningId;Wait-Process -Id $runningId -ErrorAction SilentlyContinue}
}
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw "端口 $Port 已被其他进程占用，未停止该进程"}
$jar=Join-Path $projectRoot 'target/nified-permission-system-0.0.1-SNAPSHOT.jar'
if(-not(Test-Path -LiteralPath $jar)){throw '先运行 Maven verify 生成应用包'}
Copy-Item -LiteralPath $jar -Destination (Join-Path $runtime 'company-console.jar') -Force
$credentialFile=Join-Path $runtime 'access.local.json'
$databaseBase=Join-Path $runtime 'company-data'
$exists=Test-Path -LiteralPath "$databaseBase.mv.db"
if(-not(Test-Path -LiteralPath $credentialFile)){
 if($exists){throw '数据库已存在但运行凭据丢失；请恢复凭据，不自动创建或重置账号'}
 function New-RandomBytes([int]$count){$bytes=New-Object byte[] $count;$generator=[Security.Cryptography.RandomNumberGenerator]::Create();try{$generator.GetBytes($bytes)}finally{$generator.Dispose()};return $bytes}
 $access=@{username='company-admin';password=('Cdwy!21-'+[BitConverter]::ToString((New-RandomBytes 18)).Replace('-',''));jwtSecret=[Convert]::ToBase64String((New-RandomBytes 48))}
 $access | ConvertTo-Json | Set-Content -LiteralPath $credentialFile -Encoding utf8
}
$access=Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
$env:PERMISSION_JWT_SECRET=$access.jwtSecret
$env:PERMISSION_BOOTSTRAP_USERNAME=$access.username
$env:PERMISSION_BOOTSTRAP_PASSWORD=$access.password
$schemaMode=if($exists){'validate'}else{'create'}
try{
 $arguments=@('-jar',('"'+(Join-Path $runtime 'company-console.jar')+'"'),"--server.port=$Port",'--server.address=127.0.0.1',"--app.backend-strategy=$Strategy",'--spring.profiles.active=default',('--spring.datasource.url=jdbc:h2:file:'+($databaseBase.Replace('\','/'))+';DB_CLOSE_ON_EXIT=FALSE'),'--spring.datasource.driver-class-name=org.h2.Driver',"--spring.jpa.hibernate.ddl-auto=$schemaMode")
 $process=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'company.log') -RedirectStandardError (Join-Path $runtime 'company-error.log')
 $process.Id | Set-Content -LiteralPath $pidFile
}finally{Remove-Item Env:\PERMISSION_JWT_SECRET,Env:\PERMISSION_BOOTSTRAP_USERNAME,Env:\PERMISSION_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue}
$ready=$false
for($attempt=0;$attempt -lt 60;$attempt++){
 try{$status=Invoke-WebRequest -Uri "http://127.0.0.1:$Port/ui/index.html" -UseBasicParsing -TimeoutSec 1;if($status.StatusCode -eq 200){$ready=$true;break}}catch{}
 if($process.HasExited){throw '应用退出，请查看 company.log 和 company-error.log'}
 Start-Sleep -Milliseconds 500
}
if(-not $ready){throw '启动未就绪，请查看运行日志'}
Write-Output "已启动：http://127.0.0.1:$Port/ui/index.html；模式：$Strategy；DDL：$schemaMode"
Write-Output "管理员凭据保存在：$credentialFile（不要上传该文件）"
