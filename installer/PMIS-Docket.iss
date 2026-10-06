; PMIS Docket — Windows installer (Inno Setup 6)
; Built by build-installer.ps1. You can also compile it by hand:
;   ISCC.exe /DServerUrl=https://192.168.1.10:8443 PMIS-Docket.iss

#define AppName "PMIS Docket"
#define AppPublisher "PMIS"
#define AppExe "PMIS Docket.exe"
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef ServerUrl
  #define ServerUrl "https://SERVER-IP:8443"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\app\PMIS Docket"
#endif

[Setup]
AppId={{6228CB97-47DF-42CD-9CE3-3B4A84E0E925}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
VersionInfoCompany={#AppPublisher}
VersionInfoVersion={#AppVersion}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; "Only for me" needs no admin rights; the user can choose "Everyone on this PC" in the first dialog.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\build\installer
OutputBaseFilename=PMIS-Docket-Setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
WizardSizePercent=110
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
CloseApplications=yes
#if FileExists(AddBackslash(SourcePath) + "assets\docket.ico")
SetupIconFile=assets\docket.ico
#endif
#if FileExists(AddBackslash(SourcePath) + "assets\wizard-side.bmp")
WizardImageFile=assets\wizard-side.bmp
#endif
#if FileExists(AddBackslash(SourcePath) + "assets\wizard-small.bmp")
WizardSmallImageFile=assets\wizard-small.bmp
#endif
; After buying a code signing certificate, define a sign tool in Inno Setup (Tools > Configure Sign Tools)
; named "pmissign" and remove the semicolon below. This removes the "Unknown publisher" warning.
; SignTool=pmissign
; SignedUninstaller=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to PMIS Docket
WelcomeLabel2=Docket is PMIS's file manager for the company server. You'll see your own folder and the company folders you have access to.%n%nIt takes about a minute. Close other programs before you continue.
FinishedHeadingLabel=PMIS Docket is ready
FinishedLabel=Sign in with your company account. Docket will keep itself up to date.

[Tasks]
Name: "desktopicon"; Description: "Put a Docket icon on the desktop"; GroupDescription: "Shortcuts:"
Name: "startup"; Description: "Open Docket when I sign in to Windows"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon
Name: "{autostartup}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: startup

[Run]
Filename: "{app}\{#AppExe}"; Description: "Launch PMIS Docket now"; Flags: nowait postinstall skipifsilent

[Code]
var
  ServerPage: TInputQueryWizardPage;

procedure InitializeWizard;
begin
  ServerPage := CreateInputQueryPage(wpSelectTasks,
    'Company server', 'Where is the PMIS file server?',
    'This address was set by PMIS IT. Change it only if IT asked you to.');
  ServerPage.Add('Server address:', False);
  ServerPage.Values[0] := ExpandConstant('{param:server|{#ServerUrl}}');
end;

function SettingsFile: String;
begin
  if IsAdminInstallMode then
    Result := ExpandConstant('{commonappdata}\PMIS Docket\docket.properties')
  else
    Result := ExpandConstant('{localappdata}\PMIS Docket\docket.properties');
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Url: String;
begin
  if CurStep = ssPostInstall then
  begin
    Url := Trim(ServerPage.Values[0]);
    ForceDirectories(ExtractFileDir(SettingsFile));
    SaveStringToFile(SettingsFile, '# Written by PMIS Docket Setup' + #13#10 + 'server.url=' + Url + #13#10, False);
  end;
end;
