.DEFAULT_GOAL := help
COMPOSE ?= compose.local.yaml
SERVER ?= creative
WORLD ?= world
ZIP ?=
OUT ?=
export COMPOSE SERVER WORLD ZIP OUT

.PHONY: dev-server hotswap dev-stop hud-sheet export-all zip-all help status up stop configure export zip import restore inspect export-panel import-panel backup test-archives pack art test-pack e2e-pack plugin plugin-prod plugin-build plugin-test plugin-clear plugin-clear-prod plugin-status plugin-status-prod test-plugins reload-data reload-data-prod reload-data-clear reload-data-clear-prod
help: ## Show operator commands
	@python3 scripts/world_archive.py help
status: ## Show container status
	@python3 scripts/world_archive.py status
up: ## Build/start locally; pull/start pinned images in production
	@python3 scripts/world_archive.py up
stop: ## Stop the selected Minecraft server
	@python3 scripts/world_archive.py stop
configure: ## Initialize missing secrets and private forwarding; preserve existing secrets
	@python3 scripts/world_archive.py configure
export: ## Create a portable server ZIP; briefly stop and resume only this server
	@python3 scripts/world_archive.py export
zip: export ## Alias for export
export-all: ## Create three server ZIPs and one panel ZIP
	@python3 scripts/world_archive.py export-all
zip-all: export-all ## Alias for export-all
import: ## Inspect, stage, back up and restore ZIP; resume only if previously running
	@python3 scripts/world_archive.py import
restore: import ## Alias for import
inspect: ## Inspect a portable ZIP or a plain Minecraft world ZIP without changing data
	@python3 scripts/world_archive.py inspect
export-panel: ## Export the panel database, roles and administrator separately
	@python3 scripts/world_archive.py export-panel
import-panel: ## Restore a panel ZIP with an automatic previous-state backup
	@python3 scripts/world_archive.py import-panel
backup: ## Full network backup, including secrets; leaves the network stopped
	@python3 scripts/world_archive.py backup
test-archives: ## Run isolated ZIP safety and restoration tests
	@python3 -m unittest discover -s scripts -p 'test_world_archive.py' -v
pack: ## Build the reproducible menu resource pack and print its SHA-1
	@python3 scripts/build_pack.py
art: ## Repaint pack textures from resource-pack/art/render.py (needs headless Chrome)
	@python3 resource-pack/art/render.py
e2e-pack: ## Boot a creative Paper 26.3 + headless 26.3 client and check the pack loads and renders (see scripts/e2e_client.py)
	@python3 scripts/e2e_client.py
test-pack: ## Validate the menu resource pack structure and reproducibility
	@python3 scripts/test_pack.py
	@python3 scripts/test_hud_sheet.py
hud-sheet: ## Render every HUD state to resource-pack/dist/hud-sheet/ (index.html is self-contained) [SCALE=2|3|4] [MODE=auto|large|medium|small]
	@python3 resource-pack/art/hud_sheet.py --scale $(or $(SCALE),3) --mode $(or $(MODE),auto)
PLUGIN ?=
SERVERS ?=
plugin: ## Build PLUGIN=network|arena|lobby|claims, override it locally, restart only its servers [SERVERS=...] [FAST=1] [FORCE=1]
	@scripts/plugins.sh push $(PLUGIN) $(SERVERS)
plugin-prod: ## Same on production over SSH (.env.remote: PROD_HOST, PROD_PATH)
	@scripts/plugins.sh remote push $(PLUGIN) $(SERVERS)
plugin-build: ## Only build PLUGIN's jar
	@scripts/plugins.sh build $(PLUGIN)
plugin-test: ## Only run PLUGIN's unit tests [TEST=SomeTestClass]
	@scripts/plugins.sh test $(PLUGIN) $(TEST)
plugin-clear: ## Drop local plugin overrides and restart the affected servers [SERVERS=...]
	@scripts/plugins.sh clear $(SERVERS)
plugin-clear-prod: ## Drop production plugin overrides
	@scripts/plugins.sh remote clear $(SERVERS)
plugin-status: ## List local plugin overrides
	@scripts/plugins.sh status
plugin-status-prod: ## List production plugin overrides
	@scripts/plugins.sh remote status
test-plugins: ## Test plugin overrides (start.sh) and the hotfix script without Docker
	@python3 scripts/test_plugins.py
dev-server: ## Boot the local Paper 26.3 dev server with JDWP open (no client); keeps running
	@python3 scripts/e2e_client.py --scenario dev
hotswap: ## Build PLUGIN (arena|network) without tests and redefine the changed classes in the running dev server, no restart
	@python3 scripts/hotswap.py $(PLUGIN)
dev-stop: ## Stop the dev server
	@python3 scripts/hotswap.py stop
FILES ?=
reload-data: ## Push changed werewolf data (roles, ambience, decor) and /ww reload, no restart [FILES="roles/SEER.json ..."] [SERVERS=...]
	@scripts/plugins.sh data $(FILES) $(if $(SERVERS),-s $(SERVERS))
reload-data-prod: ## Same on production over SSH
	@scripts/plugins.sh remote data $(FILES) $(if $(SERVERS),-s $(SERVERS))
reload-data-clear: ## Drop the data overrides and /ww reload [SERVERS=...]
	@scripts/plugins.sh data-clear $(SERVERS)
reload-data-clear-prod: ## Same on production
	@scripts/plugins.sh remote data-clear $(SERVERS)
