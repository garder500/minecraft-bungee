#!/usr/bin/env python3
"""Extend the isolated `e2e_client.py --scenario skyblock --keep` run with two real clients.
Creates a second disposable offline client from the first private test instance. Never accesses a real stack.
Restarts ONLY .e2e/server, preserves its worlds/profiles, and leaves that test server running.
"""
import json, math, os, pathlib, shutil, socket, subprocess, time
import e2e_client as e

server = e.WORK / 'server'
properties = (server / 'server.properties').read_text()
assert 'server-ip=127.0.0.1' in properties and 'motd=Isolated Skyblock test' in properties
assert 'online-mode=false' in properties and 'server-port=25599' in properties
x = e.tool('xdotool', 'xdotool')
mag = e.tool('magick', 'imagemagick')
tools = {'xdotool': x, 'magick': mag, 'import': str(pathlib.Path(mag).parent / 'import')}
env = dict(os.environ, DISPLAY=e.DISPLAY, SDL_VIDEODRIVER='x11', QT_QPA_PLATFORM='xcb',
           VK_ICD_FILENAMES='/run/opengl-driver/share/vulkan/icd.d/lvp_icd.x86_64.json')
env.pop('WAYLAND_DISPLAY', None)
wins = json.loads((e.WORK / 'test-windows.json').read_text()) if (e.WORK / 'test-windows.json').exists() else {}
shots = e.WORK / 'shots-skyblock-simple'
results = json.loads((e.WORK / 'skyblock-roundtrip.json').read_text()) if (e.WORK / 'skyblock-roundtrip.json').exists() else []

def say(message): print('[skyblock roundtrip] ' + message, flush=True)
def check(value, message):
    if not value: raise AssertionError(message)
    results.append(message); say('PASS ' + message)
    (e.WORK / 'skyblock-roundtrip.json').write_text(json.dumps(results, indent=2))
def rcon(command): return e.Rcon().command(command)
def dimension(name): return rcon('data get entity ' + name + ' Dimension')
def pos(name):
    raw = rcon('data get entity ' + name + ' Pos')
    return [float(n.rstrip('d')) for n in raw.split('[')[1].rstrip(']').split(',')]
def as_player(name, command): return rcon('execute as ' + name + ' at ' + name + ' run ' + command)
def command(role, text, delay=3.3):
    subprocess.run([x, 'windowraise', wins[role], 'windowfocus', wins[role]], env=env, check=True)
    subprocess.run([x, 'key', 'slash'], env=env, check=True); time.sleep(.3)
    subprocess.run([x, 'type', '--delay', '30', text], env=env, check=True)
    subprocess.run([x, 'key', 'Return'], env=env, check=True); time.sleep(delay)
def wait_dimension(name, target, seconds=90):
    for _ in range(seconds):
        if target in dimension(name): return
        time.sleep(1)
    raise AssertionError(name + ' did not reach ' + target + ': ' + dimension(name))
def near(a, b, radius=3): return abs(a[0]-b[0]) <= radius and abs(a[2]-b[2]) <= radius
def screenshot(name, role='owner'):
    subprocess.run([x, 'windowraise', wins[role], 'windowfocus', wins[role]], env=env)
    time.sleep(.5); e.shot(tools, shots / name)
def profile(name):
    path = server / 'plugins/VampyuyuSurvival/players' / (offline_id(name) + '.json')
    return json.loads(path.read_text())['sections']
def offline_id(name): return e.offline_uuid(name)
def game_windows():
    output = subprocess.run([x, 'search', '--class', '.*'], env=env, capture_output=True, text=True).stdout
    return [w for w in output.split() if '854x480' in subprocess.check_output([x, 'getwindowgeometry', w], env=env, text=True)]
def launch(role, name, instance):
    before = game_windows()
    logfile = open(e.WORK / ('prism-' + role + '-rejoin.log'), 'a')
    subprocess.Popen(['prismlauncher', '-d', str(e.WORK / 'prism'), '-l', instance, '-o', name,
                      '-s', '127.0.0.1:25599'], env=env, stdout=logfile, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(90):
        added = [w for w in game_windows() if w not in before]
        if added: wins[role] = added[0]; break
        time.sleep(1)
    else: raise RuntimeError('no new window for ' + name)
    subprocess.run([x, 'windowraise', wins[role], 'windowfocus', wins[role]], env=env)
    for _ in range(90):
        if name in rcon('list'):
            log = e.WORK / 'prism/instances' / instance / 'minecraft/logs/latest.log'
            if log.exists() and log.read_text(errors='replace').count('Reloading ResourceManager') >= 2: break
        subprocess.run([x, 'mousemove', '485', '445', 'click', '1'], env=env)
        time.sleep(2)
    else: raise RuntimeError('client did not rejoin with its pack: ' + name)
    time.sleep(3)
    rcon('gamemode creative ' + name)
    (e.WORK / 'test-windows.json').write_text(json.dumps(wins))

def ensure_guest():
    windows = game_windows()
    if not wins:
        if len(windows) != 1: raise RuntimeError('expected exactly one private E2E client before creating the guest')
        wins['owner'] = windows[0]
    if 'E2EGuest' in rcon('list') and wins.get('guest') in windows: return
    source = e.WORK / 'prism/instances/26.3'
    target = e.WORK / 'prism/instances/26.3-guest'
    if not target.exists():
        shutil.copytree(source,target)
        shutil.rmtree(target/'minecraft/logs',ignore_errors=True)
        config=target/'instance.cfg'
        config.write_text(config.read_text().replace('name=26.3','name=Skyblock Test Guest'))
    launch('guest','E2EGuest','26.3-guest')

def restart(label, legacy_import=False):
    say('graceful stop and restart of the isolated server: ' + label)
    for window in wins.values(): subprocess.run([x, 'windowclose', window], env=env)
    time.sleep(3)
    try: rcon('stop')
    except (OSError, EOFError): pass
    for _ in range(60):
        try:
            with socket.create_connection(('127.0.0.1', e.MC_PORT), timeout=.5): pass
        except OSError: break
        time.sleep(1)
    else: raise RuntimeError('isolated server did not stop')
    time.sleep(2)
    if legacy_import:
        # Only this disposable offline profile is altered to simulate a pre-upgrade native bed.
        player_file=server/'plugins/VampyuyuSurvival/players'/(offline_id('E2EBot')+'.json')
        document=json.loads(player_file.read_text())
        points=document['sections']['mode_respawns']
        points.pop('skyblock',None); points['imported']=False
        player_file.write_text(json.dumps(document,indent=2))
    for jar in (e.SURVIVAL_JAR, e.NETWORK_JAR): shutil.copy(jar, server / 'plugins' / jar.name)
    old = server / 'console.log'
    if old.exists(): shutil.move(old, server / ('console-' + label + '.log'))
    server_env = dict(os.environ, SERVER_ID='survival', ROLE_BRIDGE_URL='http://127.0.0.1:9',
        ROLE_BRIDGE_TOKEN_FILE=str(server / 'token'), MENU_PACK_BASE_URL='http://127.0.0.1:25597',
        MENU_PACK_REQUIRED='true', MENU_PACK_SHA1_FILE=str(e.WORK / 'pack/vampyuyu-menus.sha1'),
        ARENA_REDIS_HOST='127.0.0.1', WEREWOLF_LOG_CONSOLE='false')
    coreutils = pathlib.Path(os.path.realpath(shutil.which('mkdir'))).parent
    setup = f'mount -t tmpfs tmpfs /run && "{coreutils}/mkdir" -p /run/secrets && "{coreutils}/cp" "{server}/token" /run/secrets/role_bridge_token && exec "$@"'
    args = ['unshare', '-rm', 'bash', '-c', setup, 'bash', e.java25(), '-Dterminal.jline=false',
            '-Dterminal.ansi=false', '-Xmx1G', '-jar', str(e.paper_jar()), '--nogui']
    proc = subprocess.Popen(args, cwd=server, env=server_env, stdin=subprocess.DEVNULL,
        stdout=open(old, 'w'), stderr=subprocess.STDOUT, start_new_session=True)
    (server / 'test-paper.pid').write_text(str(proc.pid))
    if not e.wait_for(old, 'Done (', 180, [proc]): raise RuntimeError('test server restart failed')
    launch('owner', 'E2EBot', '26.3'); launch('guest', 'E2EGuest', '26.3-guest')
    for world in ('overworld','the_nether','the_end','sb_world','sb_nether'):
        rcon('execute in minecraft:' + world + ' run gamerule immediate_respawn true')
        rcon('execute in minecraft:' + world + ' run gamerule spawn_mobs false')
    rcon('execute in minecraft:overworld run kill @e[type=slime]')

def die(expected_world, bed):
    time.sleep(2)
    rcon('kill E2EBot'); time.sleep(4); wait_dimension('E2EBot',expected_world)
    check(near(pos('E2EBot'),bed), 'death in current dimension uses the correct independent bed: ' + expected_world)

def verify_blocked_points():
    points = profile('E2EBot')['mode_respawns']
    beds = {mode: [points[mode]['position'][axis] for axis in ('x','y','z')]
            for mode in ('skyblock','survival')}
    def place(world, bed, blocked):
        bx,by,bz = [math.floor(v) for v in bed]
        material = 'obsidian' if blocked else 'air'
        rcon(f'execute in minecraft:{world} run forceload add {bx-2} {bz-2} {bx+2} {bz+2}')
        time.sleep(1)
        rcon(f'execute in minecraft:{world} run fill {bx-2} {by} {bz-2} {bx+2} {by+3} {bz+2} {material}')
        rcon(f'execute in minecraft:{world} run fill {bx-2} {by-1} {bz-2} {bx+2} {by-1} {bz+2} stone')
        rcon(f'execute in minecraft:{world} run setblock {bx} {by} {bz} red_bed[facing=north,part=head]')
        rcon(f'execute in minecraft:{world} run setblock {bx} {by} {bz+1} red_bed[facing=north,part=foot]')
        if blocked:
            probe=rcon(f'execute in minecraft:{world} if block {bx+1} {by} {bz} obsidian')
            if 'Test passed' not in probe: raise AssertionError('test bed enclosure was not actually placed: '+probe)
    for mode,world in [('skyblock','sb_world'),('survival','overworld')]: place(world,beds[mode],False)
    command('owner','menu skyblock'); wait_dimension('E2EBot','sb_world'); home=pos('E2EBot')
    command('owner','island nether'); wait_dimension('E2EBot','sb_nether'); time.sleep(2)
    die('sb_world',beds['skyblock'])
    command('owner','menu survival'); wait_dimension('E2EBot','minecraft:overworld')
    command('owner','spawn',delay=5); spawn=pos('E2EBot')
    as_player('E2EBot','execute in minecraft:the_nether run tp @s 30 90 30')
    wait_dimension('E2EBot','the_nether'); time.sleep(2); die('minecraft:overworld',beds['survival'])
    check(True,'latest native respawn restoration preserves both real beds after switching modes')
    for mode,world in [('skyblock','sb_world'),('survival','overworld')]: place(world,beds[mode],True)
    command('owner','menu skyblock'); wait_dimension('E2EBot','sb_world')
    command('owner','island nether'); wait_dimension('E2EBot','sb_nether'); time.sleep(2)
    rcon('kill E2EBot'); time.sleep(4); wait_dimension('E2EBot','sb_world')
    check(near(pos('E2EBot'),home),'obstructed Skyblock bed falls back safely to personal island')
    command('owner','menu survival'); wait_dimension('E2EBot','minecraft:overworld')
    as_player('E2EBot','execute in minecraft:the_nether run tp @s 30 90 30')
    wait_dimension('E2EBot','the_nether'); time.sleep(2); rcon('kill E2EBot'); time.sleep(4)
    wait_dimension('E2EBot','minecraft:overworld')
    check(near(pos('E2EBot'),spawn),'obstructed Survival bed falls back safely to Survival spawn')
    screenshot('sb-10-obstructed-bed-fallback.png')
    # Restore usable test beds so the server is ready for human inspection.
    for mode,world in [('skyblock','sb_world'),('survival','overworld')]:
        place(world,beds[mode],False)
        bx,by,bz=[math.floor(v) for v in beds[mode]]
        rcon(f'execute in minecraft:{world} run forceload remove {bx-2} {bz-2} {bx+2} {bz+2}')
    rcon('gamemode creative E2EBot'); rcon('gamemode creative E2EGuest')

def verify_command_isolation():
    command('owner','menu skyblock'); wait_dimension('E2EBot','sb_world'); home=pos('E2EBot')
    command('owner','sethome sky_test')
    command('owner','menu survival'); wait_dimension('E2EBot','minecraft:overworld')
    command('owner','home sky_test',delay=5)
    check('minecraft:overworld' in dimension('E2EBot'),'saved /home cannot cross from Survival into Skyblock')
    command('owner','menu skyblock'); wait_dimension('E2EBot','sb_world')
    command('owner','back',delay=6)
    check('sb_world' in dimension('E2EBot'),'death /back cannot cross from Skyblock into Survival')
    command('owner','island visit E2EGuest')
    command('owner','spawn',delay=5)
    check(near(pos('E2EBot'),home),'Skyblock /spawn returns to the personal island without any hub')
    command('guest','island home'); wait_dimension('E2EGuest','sb_world')
    screenshot('sb-11-final-personal-island.png')

def verify_skyblock_return_portal():
    command('owner','menu skyblock'); wait_dimension('E2EBot','sb_world'); home=pos('E2EBot')
    command('owner','island nether'); wait_dimension('E2EBot','sb_nether')
    px,py,pz=math.floor(home[0])+10,math.floor(home[1]),math.floor(home[2])+10
    prefix='execute in minecraft:sb_nether run '
    rcon(prefix+f'forceload add {px-2} {pz-2} {px+5} {pz+2}'); time.sleep(1)
    rcon(prefix+f'fill {px-2} {py-1} {pz-2} {px+5} {py-1} {pz+2} stone')
    rcon(prefix+f'fill {px} {py} {pz} {px+3} {py+4} {pz} obsidian')
    rcon(prefix+f'fill {px+1} {py+1} {pz} {px+2} {py+3} {pz} nether_portal[axis=x]')
    probe=rcon(prefix+f'execute if block {px+1} {py+1} {pz} nether_portal')
    if 'Test passed' not in probe: raise AssertionError('return portal was not actually built: '+probe)
    rcon(prefix+f'tp E2EBot {px+1.5} {py+1} {pz+.5}')
    wait_dimension('E2EBot','sb_world')
    check(near(pos('E2EBot'),home,30),'physical return portal from Skyblock Nether stays on the personal Skyblock island')
    rcon(prefix+f'forceload remove {px-2} {pz-2} {px+5} {pz+2}')
    command('owner','island home')

def main():
    ensure_guest()
    restart('initial-upgrade')
    points = profile('E2EBot')['mode_respawns']
    check(points['skyblock']['kind'] == points['survival']['kind'] == 'BED', 'two real BED spawn events persisted through shutdown')
    check(points['skyblock']['position']['world'] == 'sb_world' and points['survival']['position']['world'] == 'world', 'separate bed worlds loaded after restart')
    sb_bed = [points['skyblock']['position'][a] for a in ('x','y','z')]
    normal_bed = [points['survival']['position'][a] for a in ('x','y','z')]
    command('owner', 'menu skyblock'); wait_dimension('E2EBot', 'sb_world'); home = pos('E2EBot')
    command('guest', 'menu skyblock'); wait_dimension('E2EGuest', 'sb_world'); guest_home = pos('E2EGuest')
    check(not near(home, guest_home, 60), 'two real players receive distinct personal islands')
    command('owner','island warp set OwnerTest')
    command('guest','island visit E2EBot')
    check(near(pos('E2EGuest'),home), 'visit reaches the other player island')
    command('guest','island home'); check(near(pos('E2EGuest'),guest_home), 'home still returns the visitor to their own island')
    command('owner','island invite E2EGuest')
    command('guest','island accept E2EBot')
    check(near(pos('E2EGuest'),home), 'invitation accepted between two real clients without losing either island')
    command('guest','island home'); time.sleep(7)
    command('guest','island warp E2EBot')
    check(near(pos('E2EGuest'),home), 'public warp reaches the owner island')
    screenshot('sb-7-two-player-visit.png','guest')
    command('guest','island home'); check(near(pos('E2EGuest'),guest_home), 'public warp and membership preserve the guest personal home')

    # Build physical portals in test terrain. Actual portal events decide the destination.
    def physical_portal(world, at, end=False):
        px,py,pz = [math.floor(v) for v in at]
        prefix = 'execute in minecraft:' + world + ' run '
        rcon(prefix + f'fill {px-2} {py-1} {pz-2} {px+5} {py-1} {pz+2} stone')
        if end:
            rcon(prefix + f'setblock {px} {py} {pz} end_portal')
            rcon(prefix + f'tp E2EBot {px+.5} {py+.1} {pz+.5}')
        else:
            rcon(prefix + f'fill {px} {py} {pz} {px+3} {py+4} {pz} obsidian')
            rcon(prefix + f'fill {px+1} {py+1} {pz} {px+2} {py+3} {pz} nether_portal[axis=x]')
            rcon(prefix + f'tp E2EBot {px+1.5} {py+1} {pz+.5}')
        time.sleep(7)

    physical_portal('sb_world',[home[0]+12,home[1],home[2]])
    wait_dimension('E2EBot','sb_nether'); check(True,'physical Skyblock Nether portal reaches sb_nether')
    die('sb_world',sb_bed)
    physical_portal('sb_world',[home[0]+20,home[1],home[2]],end=True)
    check('sb_world' in dimension('E2EBot'),'physical End portal is blocked in Skyblock')
    command('owner','menu survival'); wait_dimension('E2EBot','minecraft:overworld')
    physical_portal('overworld',[80,-60,0]); wait_dimension('E2EBot','the_nether')
    check('sb_nether' not in dimension('E2EBot'),'Survival Nether portal reaches the distinct vanilla Nether')
    die('minecraft:overworld',normal_bed)
    physical_portal('overworld',[110,-60,0],end=True); wait_dimension('E2EBot','the_end')
    check(True,'physical Survival End portal remains available')
    die('minecraft:overworld',normal_bed)
    rcon('kill E2EBot'); time.sleep(4); check(near(pos('E2EBot'),normal_bed),'Survival overworld death uses its own bed')
    screenshot('sb-8-survival-after-end-death.png')
    command('owner','menu skyblock'); command('owner','island nether'); wait_dimension('E2EBot','sb_nether')
    command('guest','menu survival'); wait_dimension('E2EGuest','minecraft:overworld')
    restart('persistence')
    check('sb_nether' in dimension('E2EBot'),'reconnect after restart restores Skyblock Nether mode and position')
    check('minecraft:overworld' in dimension('E2EGuest'),'other player reconnects in Survival independently')
    die('sb_world',sb_bed)
    command('guest','menu skyblock'); wait_dimension('E2EGuest','sb_world')
    check(near(pos('E2EGuest'),guest_home),'guest personal island persists after restart')
    command('guest','island visit E2EBot'); check(near(pos('E2EGuest'),home),'visit/membership still works after restart')

    # Destroy just the test beds, then check mode-local fallbacks.
    for world, bed in [('sb_world',sb_bed),('overworld',normal_bed)]:
        bx,by,bz=[math.floor(v) for v in bed]
        rcon(f'execute in minecraft:{world} run fill {bx-1} {by} {bz-1} {bx+1} {by} {bz+1} air')
    command('owner','island nether'); wait_dimension('E2EBot','sb_nether')
    rcon('kill E2EBot'); time.sleep(4); wait_dimension('E2EBot','sb_world')
    check(near(pos('E2EBot'),home),'destroyed Skyblock bed falls back to personal island')
    command('owner','menu survival'); wait_dimension('E2EBot','minecraft:overworld')
    command('owner','spawn',delay=5); spawn=pos('E2EBot')
    as_player('E2EBot','execute in minecraft:the_nether run tp @s 30 90 30')
    wait_dimension('E2EBot','the_nether'); time.sleep(2); rcon('kill E2EBot'); time.sleep(4)
    wait_dimension('E2EBot','minecraft:overworld')
    check(near(pos('E2EBot'),spawn),'destroyed Survival bed falls back to Survival spawn')
    screenshot('sb-9-destroyed-bed-fallback.png')
    check(not any((base/name).exists() for base in (server,server/'world/dimensions/minecraft')
                  for name in ('sb_hub','sb_end')),'no Skyblock hub or End generated across two restarts')
    rcon('gamemode creative E2EBot'); rcon('gamemode creative E2EGuest')
    verify_blocked_points()
    verify_command_isolation()
    verify_skyblock_return_portal()
    say('all extended checks passed; isolated server left on 127.0.0.1:25599')


if __name__ == '__main__':
    main()
