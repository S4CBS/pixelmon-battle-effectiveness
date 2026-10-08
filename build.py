import os
import subprocess
import shutil

def build():
    appdata = os.path.expandvars(r'%APPDATA%\.minecraft')
    libs_dir = os.path.join(appdata, 'libraries')

    jars = []
    for root, dirs, files in os.walk(libs_dir):
        for f in files:
            if f.endswith('.jar'):
                jars.append(os.path.join(root, f))

    mc_jar = os.path.join(appdata, 'versions', '1.21.1', '1.21.1.jar')
    if os.path.exists(mc_jar):
        jars.append(mc_jar)

    pixelmon_jar = os.path.join(appdata, 'mods', 'Pixelmon-1.21.1-9.4.1-universal.jar')
    if os.path.exists(pixelmon_jar):
        jars.append(pixelmon_jar)

    cp = os.pathsep.join(jars)

    shutil.rmtree('build/classes', ignore_errors=True)
    os.makedirs('build/classes', exist_ok=True)

    src_files = []
    for root, dirs, files in os.walk('src/main/java'):
        for f in files:
            if f.endswith('.java'):
                src_files.append(os.path.join(root, f))

    javac = r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javac.exe'
    cmd = [javac, '--release', '21', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', 'build/classes'] + src_files

    print(f'Compiling {len(src_files)} source files...')
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print('Compilation failed:\n', res.stderr)
        return False
    print('Compilation succeeded!')

    build_jar = 'build/jar'
    shutil.rmtree(build_jar, ignore_errors=True)
    os.makedirs(build_jar, exist_ok=True)

    shutil.copytree('src/main/resources', build_jar, dirs_exist_ok=True)
    shutil.copytree('build/classes', build_jar, dirs_exist_ok=True)

    os.makedirs('build/libs', exist_ok=True)
    jar_file = 'build/libs/PixelmonEffectiveness-1.21.1-1.0.3.jar'
    if os.path.exists(jar_file):
        os.remove(jar_file)

    jar_exe = r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\jar.exe'
    cmd = [jar_exe, 'cf', os.path.abspath(jar_file), '-C', os.path.abspath(build_jar), '.']
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print('JAR error:\n', res.stderr)
        return False

    print(f'Created JAR: {jar_file} ({os.path.getsize(jar_file)} bytes)')

    mods_dir = os.path.join(appdata, 'mods')
    dest_jar = os.path.join(mods_dir, 'PixelmonEffectiveness-1.21.1-1.0.3.jar')
    shutil.copy2(jar_file, dest_jar)
    print(f'Deployed to mods: {dest_jar}')

    for old in ['PixelmonEffectiveness-1.21.1-1.0.0.jar', 'PixelmonEffectiveness-1.21.1-1.0.1.jar', 'PixelmonEffectiveness-1.21.1-1.0.2.jar']:
        p = os.path.join(mods_dir, old)
        if os.path.exists(p):
            os.remove(p)
            print(f'Removed old version: {old}')

    return True

if __name__ == '__main__':
    build()
