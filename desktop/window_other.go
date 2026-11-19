//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"runtime"
)

const platformName = runtime.GOOS

func openAppWindow(url string) bool {
	opener := "xdg-open"
	if runtime.GOOS == "darwin" {
		opener = "open"
	}
	if err := exec.Command(opener, url).Start(); err != nil {
		fmt.Println("Apri nel browser:", url)
	}
	return false
}

func fatal(msg string) {
	fmt.Fprintln(os.Stderr, msg)
	os.Exit(1)
}
