package main

import (
	"context"
	"github.com/garder500/minecraft-bungee/internal/control"
	"log"
	"os"
	"os/signal"
	"syscall"
	_ "time/tzdata" // the runtime image has no zoneinfo; the backup schedule needs named time zones
)

func main() {
	token, e := control.Secret("/run/secrets/controller_token")
	if e != nil {
		log.Fatal(e)
	}
	pw := map[string]string{}
	for _, id := range []string{"lobby", "creative", "survival"} {
		p, e := control.Secret("/run/secrets/" + id + "_rcon")
		if e != nil {
			log.Fatal(e)
		}
		pw[id] = p
	}
	gameToken, e := control.Secret("/run/secrets/role_bridge_token")
	if e != nil {
		log.Fatal(e)
	}
	project := os.Getenv("STACK_ID")
	if project == "" {
		log.Fatal("STACK_ID required")
	}
	s := control.Server{GameToken: gameToken, DataRoot: "/servers", Docker: control.NewDocker("/var/run/docker.sock", project), Token: token, Passwords: pw, BackupDir: "/backups", Version: os.Getenv("MC_VERSION"), StateDir: "/state"}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer stop()
	go s.RunScheduler(ctx)
	if err := control.Listen(":8081", s.Handler()); err != nil {
		log.Fatal(err)
	}
}
