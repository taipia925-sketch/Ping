package main

import (
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"time"
)

const appVersion = "1.1.0"

func main() {
	noWindow := flag.Bool("no-window", false, "non aprire la finestra (solo server, per prove)")
	port := flag.Int("port", 0, "porta locale (0 = automatica)")
	flag.Parse()

	store := OpenStore(dataDir())
	app := NewApp(store)
	srv := &Server{app: app, token: newToken()}

	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", *port))
	if err != nil {
		ln, err = net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			fatal("Impossibile avviare PingPro: " + err.Error())
		}
	}
	srv.port = ln.Addr().(*net.TCPAddr).Port
	go func() { _ = http.Serve(ln, srv.routes()) }()

	if *noWindow {
		fmt.Println(srv.URL())
		select {}
	}

	// Si apre la finestra dell'app; quando viene chiusa, PingPro termina.
	if waited := openAppWindow(srv.URL()); waited {
		os.Exit(0)
	}
	// Finestra aperta nel browser predefinito: PingPro termina quando la pagina resta chiusa per 30 secondi.
	srv.lastSeen.Store(time.Now().UnixMilli())
	for {
		time.Sleep(5 * time.Second)
		if srv.clients.Load() == 0 && time.Now().UnixMilli()-srv.lastSeen.Load() > 30_000 && !app.Busy() {
			os.Exit(0)
		}
	}
}
