//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"unsafe"
)

const platformName = "windows"

// openAppWindow apre PingPro in una finestra dedicata di Microsoft Edge (modalità app, senza barre del browser).
// Restituisce true se ha atteso la chiusura della finestra.
func openAppWindow(url string) bool {
	edge := findEdge()
	if edge != "" {
		profile := filepath.Join(dataDir(), "finestra")
		cmd := exec.Command(edge, "--app="+url, "--user-data-dir="+profile, "--window-size=1280,860",
			"--no-first-run", "--no-default-browser-check", "--disable-features=Translate")
		cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
		if err := cmd.Start(); err == nil {
			_ = cmd.Wait()
			return true
		}
	}
	// Ripiego: browser predefinito.
	_ = exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	return false
}

func findEdge() string {
	var dirs []string
	for _, env := range []string{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"} {
		if v := os.Getenv(env); v != "" {
			dirs = append(dirs, filepath.Join(v, "Microsoft", "Edge", "Application", "msedge.exe"))
		}
	}
	for _, p := range dirs {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	return ""
}

func fatal(msg string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	box := user32.NewProc("MessageBoxW")
	t, _ := syscall.UTF16PtrFromString("PingPro")
	m, _ := syscall.UTF16PtrFromString(msg)
	box.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x10)
	os.Exit(1)
}
