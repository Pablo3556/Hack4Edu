' Lanza AdAstra-windows.ps1 sin ventana de consola (lo usa el acceso directo).
Set fso = CreateObject("Scripting.FileSystemObject")
raiz = fso.GetParentFolderName(fso.GetParentFolderName(WScript.ScriptFullName))
Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = raiz
shell.Run "powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & raiz & "\AdAstra-windows.ps1"" -SinTerminal", 0, False
