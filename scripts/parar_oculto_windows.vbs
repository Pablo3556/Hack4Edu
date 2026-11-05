' Para Hack4Edu sin ventana de consola (lo usa el acceso directo «Parar Hack4Edu»).
' La salida de docker queda en hack4edu.log.
Set fso = CreateObject("Scripting.FileSystemObject")
raiz = fso.GetParentFolderName(fso.GetParentFolderName(WScript.ScriptFullName))
Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = raiz
registro = raiz & "\hack4edu.log"
codigo = shell.Run("cmd /c echo ===== %date% %time% - parar ===== >> """ & registro & """ & docker compose down >> """ & registro & """ 2>&1", 0, True)
If codigo = 0 Then
    MsgBox "Hack4Edu se ha parado. Los datos se conservan.", vbInformation, "Hack4Edu"
Else
    MsgBox "No se pudo parar Hack4Edu (esta abierto Docker Desktop?)." & vbCrLf & vbCrLf & "Detalles en: " & registro, vbExclamation, "Hack4Edu"
End If
