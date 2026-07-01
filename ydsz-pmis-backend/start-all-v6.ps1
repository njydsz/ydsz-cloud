# Pmis backend launcher using P/Invoke CreateProcess with DETACHED_PROCESS + STD file handles
# This ensures the Java processes survive the parent PowerShell exit and stdout/stderr are captured.

$ErrorActionPreference = 'Continue'
$backendDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$logDir = Join-Path $backendDir "logs"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Load env vars
$envMap = @{}
Get-Content (Join-Path $backendDir "dev.env") | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $key = $matches[1].Trim()
        $val = $matches[2].Trim()
        $envMap[$key] = $val
        [System.Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }
}
Write-Host ("Loaded {0} env vars" -f $envMap.Count) -ForegroundColor DarkGray

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.IO;
using System.Collections.Generic;

public static class DetachedJava {
    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
    private struct STARTUPINFO {
        public int cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public int dwX, dwY, dwXXize, dwYSize;
        public int dwXCountChars, dwYCountChars;
        public int dwFillAttribute;
        public int dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION {
        public IntPtr hProcess;
        public IntPtr hThread;
        public int dwProcessId;
        public int dwThreadId;
    }

    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
    private static extern bool CreateProcessW(
        string lpApplicationName,
        string lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("kernel32.dll", SetLastError=true)]
    private static extern bool CloseHandle(IntPtr hObject);

    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
    private static extern bool CreateFileW(
        string lpFileName,
        uint dwDesiredAccess,
        uint dwShareMode,
        IntPtr lpSecurityAttributes,
        uint dwCreationDisposition,
        uint dwFlagsAndAttributes,
        IntPtr hTemplateFile);

    private const uint GENERIC_WRITE = 0x40000000;
    private const uint FILE_SHARE_READ = 0x00000001;
    private const uint FILE_SHARE_WRITE = 0x00000002;
    private const uint OPEN_ALWAYS = 4;
    private const uint FILE_ATTRIBUTE_NORMAL = 0x80;
    private const int  STARTF_USESTDHANDLES = 0x00000100;
    private const uint DETACHED_PROCESS = 0x00000008;
    private const uint CREATE_NEW_PROCESS_GROUP = 0x00000200;
    private const uint CREATE_NO_WINDOW = 0x08000000;

    public static int Start(string javaArgs, string workingDir, string outLogPath, string errLogPath, IDictionary<string, string> env) {
        // Create file handles for stdout/stderr
        IntPtr hOut = CreateFileW(outLogPath, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, IntPtr.Zero);
        if (hOut == IntPtr.Zero || hOut.ToInt64() == -1) throw new Win32Exception(Marshal.GetLastWin32Error());
        IntPtr hErr = CreateFileW(errLogPath, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, IntPtr.Zero);
        if (hErr == IntPtr.Zero || hErr.ToInt64() == -1) {
            CloseHandle(hOut);
            throw new Win32Exception(Marshal.GetLastWin32Error());
        }
        try {
            STARTUPINFO si = new STARTUPINFO();
            si.cb = Marshal.SizeOf(si);
            si.dwFlags = STARTF_USESTDHANDLES;
            si.hStdOutput = hOut;
            si.hStdError = hErr;
            si.hStdInput = IntPtr.Zero;

            // Build env block
            StringBuilder envBlock = new StringBuilder();
            foreach (var kv in env) {
                envBlock.Append(kv.Key).Append('=').Append(kv.Value ?? "").Append('\0');
            }
            envBlock.Append('\0');
            IntPtr envPtr = Marshal.StringToHGlobalUni(envBlock.ToString());

            // Build command line
            string cmd = "java " + javaArgs;

            PROCESS_INFORMATION pi;
            uint flags = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW;
            bool ok = CreateProcessW(null, cmd, IntPtr.Zero, IntPtr.Zero,
                true, flags, envPtr, workingDir, ref si, out pi);
            Marshal.FreeHGlobal(envPtr);
            if (!ok) {
                int err = Marshal.GetLastWin32Error();
                throw new Win32Exception(err, "CreateProcess failed: " + cmd);
            }
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
            return pi.dwProcessId;
        } finally {
            CloseHandle(hOut);
            CloseHandle(hErr);
        }
    }
}
"@

# Build env dictionary
$envDict = @{}
foreach ($k in $envMap.Keys) { $envDict[$k] = $envMap[$k] }

# Define services
$services = @(
    @{ name = 'config';       jar = 'ydsz-pmis-config.jar';       port = 9010; startOrder = 1 },
    @{ name = 'file';         jar = 'ydsz-pmis-file.jar';         port = 9019; startOrder = 2 },
    @{ name = 'audit';        jar = 'ydsz-pmis-audit.jar';        port = 9020; startOrder = 3 },
    @{ name = 'user';         jar = 'ydsz-pmis-user.jar';         port = 9002; startOrder = 4 },
    @{ name = 'auth';         jar = 'ydsz-pmis-auth.jar';         port = 9001; startOrder = 5 },
    @{ name = 'workflow';     jar = 'ydsz-pmis-workflow.jar';     port = 9014; startOrder = 6 },
    @{ name = 'notification'; jar = 'ydsz-pmis-notification.jar'; port = 9013; startOrder = 7 },
    @{ name = 'message';      jar = 'ydsz-pmis-message-exec.jar'; port = 9021; startOrder = 8 },
    @{ name = 'project';      jar = 'ydsz-pmis-project-exec.jar'; port = 9015; startOrder = 9 },
    @{ name = 'execution';    jar = 'ydsz-pmis-execution.jar';    port = 9016; startOrder = 10 },
    @{ name = 'agent';        jar = 'ydsz-pmis-agent.jar';        port = 9017; startOrder = 11 },
    @{ name = 'scheduler';    jar = 'ydsz-pmis-scheduler.jar';    port = 9022; startOrder = 12 },
    @{ name = 'gateway';      jar = 'ydsz-pmis-gateway.jar';      port = 9000; startOrder = 13 }
)

# Clean up any existing java processes (except Nacos + IDE)
Write-Host "Cleaning up old Java processes ..." -ForegroundColor Yellow
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmd -notmatch 'nacos-server\.jar' -and $cmd -notmatch 'spring-boot-language-server' -and $cmd -notmatch 'redhat.java' -and $cmd -notmatch 'vscode-spring-boot' -and $cmd -notmatch 'lemminx') {
        Write-Host "  Killing pid=$($_.Id)" -ForegroundColor DarkYellow
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 2

# Launch each service
$pidFile = Join-Path $logDir "pmis-pids.txt"
"" | Set-Content $pidFile

foreach ($svc in $services) {
    $moduleDir = Join-Path $backendDir ("ydsz-pmis-{0}" -f $svc.name)
    $jarPath = Join-Path $moduleDir ("target\{0}" -f $svc.jar)

    if (!(Test-Path $jarPath)) {
        Write-Host ("  MISSING JAR: {0}" -f $jarPath) -ForegroundColor Red
        continue
    }

    $outLog = Join-Path $logDir ("{0}.out.log" -f $svc.name)
    $errLog = Join-Path $logDir ("{0}.err.log" -f $svc.name)
    if (!(Test-Path $outLog)) { New-Item -ItemType File -Path $outLog -Force | Out-Null }
    if (!(Test-Path $errLog)) { New-Item -ItemType File -Path $errLog -Force | Out-Null }

    $javaArgs = "-jar `"$jarPath`""
    Write-Host ("[{0:D2}] Launching {1,-12} port={2:D4} ..." -f $svc.startOrder, $svc.name, $svc.port) -ForegroundColor Cyan
    try {
        $pid = [DetachedJava]::Start($javaArgs, $moduleDir, $outLog, $errLog, $envDict)
        Write-Host ("      -> pid {0}" -f $pid) -ForegroundColor DarkGray
        "$($svc.name)=$pid" | Add-Content $pidFile
    } catch {
        Write-Host ("      -> FAILED: {0}" -f $_.Exception.Message) -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 500
}

# Wait for services to come up
Write-Host ""
Write-Host "Waiting up to 90s for services to listen on their ports ..." -ForegroundColor Green
$maxWait = 90
$waited = 0
while ($waited -lt $maxWait) {
    Start-Sleep -Seconds 5
    $waited += 5
    $upCount = 0
    $totalCount = 0
    foreach ($svc in $services) {
        $totalCount++
        $port = $svc.port
        $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
        if ($listening) { $upCount++ }
    }
    Write-Host ("  [{0}s] {1}/{2} ports listening" -f $waited, $upCount, $totalCount) -ForegroundColor Cyan
    if ($upCount -eq $totalCount) { break }
}

# Final status
Write-Host ""
Write-Host "=== Final port status ===" -ForegroundColor Yellow
foreach ($svc in $services) {
    $port = $svc.port
    $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listening) {
        Write-Host ("  {0,-12} port {1:D4}  LISTENING (pid {2})" -f $svc.name, $port, $listening[0].OwningProcess) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-12} port {1:D4}  NOT LISTENING" -f $svc.name, $port) -ForegroundColor Red
    }
}
