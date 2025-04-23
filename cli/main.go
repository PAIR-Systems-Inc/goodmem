package main

import (
	"github.com/pairsys/goodmem/cli/cmd"
)

var gitCommit = "dev"

func main() {
	cmd.Execute(gitCommit)
}