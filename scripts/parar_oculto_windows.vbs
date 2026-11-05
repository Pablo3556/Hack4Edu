' Para Ad Astra sin ventana de consola (lo usa el acceso directo «Parar Ad Astra»).
' La salida de docker queda en adastra.log.
Set fso = CreateObject("Scripting.FileSystemObject")
raiz = fso.GetParentFolderName(fso.GetParentFolderName(WScript.ScriptFullName))
Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = raiz
registro = raiz & "\adastra.log"
codigo = shell.Run("cmd /c echo ===== %date% %time% - parar ===== >> """ & registro & """ & docker compose down >> """ & registro & """ 2>&1", 0, True)
If codigo = 0 Then
    MsgBox "Ad Astra se ha parado. Los datos se conservan.", vbInformation, "Ad Astra"
Else
    MsgBox "No se pudo parar Ad Astra (esta abierto Docker Desktop?)." & vbCrLf & vbCrLf & "Detalles en: " & registro, vbExclamation, "Ad Astra"
End If
