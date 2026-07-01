// launch-as-detached.csx - uses Roslyn-style scripting
// P/Invoke to CreateProcess with DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP

using System;
using System.Runtime.InteropServices;
using System.Diagnostics;
using System.IO;
using System.Text;

class Startup
{
    [StructLayout(LayoutKind.Sequential)]
    struct STARTUPINFO
    {
        public int cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public int dwX;
        public int dwY;
        public int dwXSize;
        public int dwYSize;
        public int dwXCountChars;
        public int dwYCountChars;
        public int dwFillAttribute;
        public int dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
        public IntPtr hProcess;
        public IntPtr hThread;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct PROCESS_INFORMATION
    {
        public IntPtr hProcess;
        public IntPtr hThread;
        public int dwProcessId;
        public int dwThreadId;
    }

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern bool CreateProcess(
        string lpApplicationName,
        string lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        [In] ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern uint ResumeThread(IntPtr hThread);

    const uint CREATE_NO_WINDOW = 0x08000000;
    const uint DETACHED_PROCESS = 0x00000008;
    const uint CREATE_NEW_PROCESS_GROUP = 0x00000200;

    static int Main(string[] args)
    {
        if (args.Length < 2) {
            Console.Error.WriteLine("Usage: launch-detached <workdir> <executable> [args...]");
            return 1;
        }
        string workdir = args[0];
        string executable = args[1];
        var sb = new StringBuilder();
        sb.Append('"').Append(executable).Append('"');
        for (int i = 2; i < args.Length; i++) {
            sb.Append(' ').Append('"').Append(args[i]).Append('"');
        }

        var si = new STARTUPINFO();
        si.cb = Marshal.SizeOf(si);
        var pi = new PROCESS_INFORMATION();
        uint flags = CREATE_NO_WINDOW | DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP;
        if (!CreateProcess(null, sb.ToString(), IntPtr.Zero, IntPtr.Zero, false, flags, IntPtr.Zero, workdir, ref si, out pi)) {
            Console.Error.WriteLine("CreateProcess failed: " + Marshal.GetLastWin32Error());
            return 2;
        }
        Console.WriteLine(pi.dwProcessId);
        return 0;
    }
}
